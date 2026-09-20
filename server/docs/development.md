# Developing the Snatter server

This document is for people changing the server. For running it, see the
server README.

## Package layout

Base package is `app.snatter.server`. Each feature gets its own sub-package
containing its resources, services and repositories together, rather than
splitting by technical layer:

| Package       | Contents                                             |
|---------------|------------------------------------------------------|
| `settings`    | Community settings, server info, settings API        |
| `account`     | Accounts and the identity model                      |
| `auth`        | Passwords, sessions, challenges, HTTP authentication |
| `blob`        | Binary content: storage, metadata, image detection   |
| `invite`      | Invite links and their redemption                    |
| `role`        | Permissions, roles, assignment, hierarchy rules      |
| `ratelimit`   | Per-client rate limiting driven by the settings      |
| `api`         | Shared API error types and exception mappers         |
| `common`      | Domain-wide abstractions such as `Value` and `Id`    |
| `persistence` | JDBI producer and small JDBC helpers                 |

Planned: `channel`, `gateway`, `media`.

## Database

- **Flyway owns the schema.** Migrations live in
  `src/main/resources/db/migration` as `V<n>__<description>.sql` and run at
  startup. Never edit a migration that has been committed; add a new one.
- **Plain SQL through [JDBI 3](https://jdbi.org).** No ORM. Each feature
  package has a repository class that injects `Jdbi`, writes SQL in text
  blocks, and maps rows onto records with an explicit `RowMapper`.
- **Transactions are JTA.** Put `@Transactional` on the service or repository
  method and use `jdbi.withHandle` / `jdbi.useHandle` inside it. Agroal enlists
  the connection in the transaction. Do not use `jdbi.inTransaction` or
  `handle.begin()`.
- **Timestamps** are `TIMESTAMPTZ` in the database and `Instant` in Java. Use
  the helpers in `persistence.Rows` to read them.
- **Ids** are UUID version 7 wrapped in typed records such as `AccountId`;
  see "Typed identifiers" below.

## Identity and authentication

An account is reached through one or more identities, keyed by
`(issuer, subject)`. Local accounts use issuer `local` with the account id as
subject. External OpenID Connect providers will add rows with their issuer URL
and subject claim, so one account can be reached through several logins.

Passwords for local accounts are hashed with Argon2id in PHC string format
(`$argon2id$v=19$m=65536,t=3,p=1$...`). Parameters are embedded in each hash
and can be raised without invalidating old ones.

Sessions are opaque bearer tokens, not JWTs, so they can be revoked
instantly. A token is `snt_` followed by 32 random bytes in URL-safe base64.
Only its SHA-256 hash is stored, in the `session` table. Clients send it as
`Authorization: Bearer snt_...`. `SessionAuthenticationMechanism` and
`SessionIdentityProvider` resolve it into an `AccountPrincipal` carrying the
account id, username, session id and effective permissions, so
`@Authenticated`, `@PermissionsAllowed` and `SecurityIdentity` work as usual
in resources.

Sessions record the client IP as the server sees it. Behind a reverse proxy
that is the proxy's address until trusted-proxy handling is configured; this
arrives together with IP bans.

## Blobs and avatars

All binary content goes through the `blob` package. A row in the `blob` table
holds the metadata (content type, size, SHA-256, owner, purpose) and the bytes
live in a `BlobStore`. The default store is the filesystem under
`snatter.storage.root`, laid out as `blobs/<first two hex digits>/<id>`. An
object store implementation can be added behind the same interface.

`BlobService` keeps the two in step across transactions: bytes are written
before the row is inserted and removed after the deleting transaction commits,
so readers never see a row without bytes.

Blobs are immutable and their ids unguessable, so `GET /api/v1/blobs/{id}` is
public and served with a one-year immutable cache header. This lets `<img>`
tags load avatars without an Authorization header. Anything that needs access
control later, such as attachments in private channels, will need a different
delivery scheme.

Avatars are stored as uploaded after validation: PNG, JPEG, GIF or WebP as
determined from the bytes (the declared Content-Type is ignored), at most
1 MiB and between 32 and 1024 pixels on each side. The server does not resize,
so clients should crop and scale before uploading. `Account.avatarId` points
at the current blob; replacing or clearing an avatar deletes the old blob.

## Server owner and settings

The first account registered on a fresh server becomes the **server owner**,
recorded in `server_settings.owner_account_id`. The owner holds every
permission, is exempt from the role hierarchy rules, and cannot be demoted.
Everything else is decided by permissions; see "Roles and permissions".

Everything an administrator can change at runtime lives in the
`server_settings` row and is read through `ServerSettingsService`, which
caches the row in memory and fires a `Changed` event on updates. Request paths
never query the table. Tuning that only an operator would touch, such as the
challenge difficulty, stays in `application.properties`.

## Roles and permissions

Authorization is permission-based. `role.Permission` is an enum with a fixed
bit per permission, stored as a bitmask in `role.permissions` and exposed by
name in the API; never renumber a bit. Roles (`role` table) bundle
permissions and are assigned to accounts through `account_role`. One role is
the **default role** that every member has implicitly; it is never listed in
`Account.roleIds` and cannot be assigned, removed or deleted, only its
permissions change. A member's effective permissions are the union of the
default role and their assigned roles; the owner has all of them.

`SessionIdentityProvider` resolves the effective permissions once per request
through `RoleService.resolve` and puts them on the `AccountPrincipal`
together with an owner flag and the position of the member's highest role. It
also installs a Quarkus permission checker, so resources guard operations
with `@PermissionsAllowed("MANAGE_ROLES")` and the like; a denial is rendered
as the `forbidden` error. Checks that need to look at the arguments, such as
"is this role below mine", live in the services and use the principal.

Two rules from Discord keep role management safe, and `RoleService`
enforces both for everyone except the owner:

- **Hierarchy.** Roles have a `position`; the default role is 0 and new roles
  are inserted at 1 with everything else moving up. You may only change,
  delete, assign or move roles whose position is strictly below your own
  highest role (`role_hierarchy`).
- **No escalation.** You may only grant permissions you hold yourself
  (`permission_escalation`).

Channel-level permission overrides will refine the channel-oriented
permissions later; the server-level set is the baseline.

## Registration policy

`AuthService.register` applies the policy in this order:

1. The first account on an empty server is always accepted and becomes owner.
2. Otherwise an `inviteCode`, if given, is redeemed (`invite_invalid` when it
   cannot be); without one `registrationMode` must be `open`, else
   `registration_closed` (403).
3. If `challengeRequired` is set, the request must carry a solved
   [ALTCHA](https://altcha.org) proof-of-work in `altcha`, verified by
   `AltchaService`: HMAC signature, `SHA-256(salt + number)`, expiry from the
   salt, and single use via the `used_challenge` table. The HMAC key is random
   per server start, so a restart invalidates outstanding challenges without
   any stored state.
4. Username uniqueness, hashing and session creation as before.

## Invites

An invite is a row in `invite` keyed by an 8-character random code
(`InviteCode`, a `CharSequence` value record so the contract's pattern can
validate it as a path parameter). It may carry an expiry, a maximum number of
uses, and a revocation time. `InviteRepository.redeem` counts a use in a
single conditional `UPDATE ... RETURNING`, so concurrent registrations cannot
overspend the last use. Redemption runs inside the registration transaction:
if the registration fails afterwards, for example on a taken username, the
use is rolled back with it.

Creating an invite needs the `CREATE_INVITE` permission, which the default
role grants unless changed. Members with `MANAGE_INVITES` list and revoke all
invites; others only their own. `GET /api/v1/invites/{code}` is public so a client can show what the
invite leads to before the person registers; it is rate limited under the
`invite` policy and answers 404 for unknown or revoked codes and 410 for
expired or used-up ones.

Invite links are `<publicUrl>/invite/<code>`. `publicUrl` is an owner
setting; when it is not set the link is built from the address the request
arrived on. The `/invite/<code>` path is reserved for a landing page and is
not served yet. Accounts remember the invite and inviter they came in with
(`account.invite_code`, `account.invited_by`) for later moderation features.

## Rate limiting

`@RateLimited("<policy>")` on a resource method applies the named policy from
`ServerSettings.rateLimits`, keyed by client IP, through `RateLimitFilter`.
Policies are token buckets; a refused request gets 429 with `Retry-After`
and the `rate_limited` error. Buckets live in memory, so this protects a
single server instance, and they are reset whenever the owner changes the
policies. Policies exist for `login`, `register`, `challenge` and `invite`.

## HTTP API

The API is specification-first. `protocol/openapi/openapi.yaml` is
hand-written and is the contract. On every build the openapi-generator Maven
plugin turns it into JAX-RS interfaces (`app.snatter.api.*Api`, one per tag)
and request/response classes (`app.snatter.api.model.*Dto`) under
`target/generated-sources/openapi`. Resource classes implement the
interfaces, so a change to the contract that the code does not honour fails
to compile. The same file is served verbatim at `/q/openapi`, with Swagger UI
at `/q/swagger-ui` in dev mode; annotation scanning is disabled.

Conventions that follow from this:

- **Resources implement a generated interface** and carry no JAX-RS
  annotations of their own; paths, media types and parameter constraints come
  from the contract. Security annotations such as `@Authenticated` go on the
  implementing class or method.
- **Generated types are DTOs**, suffixed `Dto` to keep them apart from domain
  records. Map at the boundary, for example `AccountDtos.toDto(Account)`.
  Typed ids (`AccountId`, `BlobId`) are the exception: the contract's
  `AccountId` and `BlobId` schemas are mapped straight onto the hand-written
  records, so DTOs and interface parameters use them directly.
- **Methods return `RestResponse<Dto>`**, Quarkus REST's typed response, so
  the body type is checked by the compiler while status and headers stay
  under the resource's control. Binary bodies are `InputStream` in both
  directions.
- **Partial updates** use `*Update` schemas where every field is optional.
  Generated DTOs leave absent arrays `null` (`containerDefaultToNull`), so a
  null field means "unchanged" and an empty array means "set to empty".
- **Bean Validation constraints live in the contract** (`minLength`,
  `pattern`, `required`) and are generated onto the DTOs and interface
  parameters. Do not repeat them on the implementing method.

Errors have one shape, the `ApiError` schema:

```json
{"error": "<stable_code>", "message": "human readable", "fields": {"username": "..."}}
```

`fields` appears only for validation failures. Throw `api.ApiException` for
domain errors; it carries the status and code, and `ApiExceptionMappers`
renders it. When adding a code, document it in the contract on the operation
that produces it.

| Method | Path                | Auth | Purpose                                   |
|--------|---------------------|------|-------------------------------------------|
| GET    | `/server-info`      | no   | Software and community description        |
| GET    | `/server-settings`   | MANAGE_SERVER | Read the settings                |
| PATCH  | `/server-settings`   | MANAGE_SERVER | Change settings, partial         |
| GET    | `/auth/challenge`   | no   | Proof-of-work challenge for registration  |
| POST   | `/auth/register`    | no   | Create a local account, returns a session |
| POST   | `/auth/login`       | no   | Username and password, returns a session  |
| POST   | `/auth/logout`      | yes  | Revoke the calling session                |
| GET    | `/accounts/me`      | yes  | The calling account                       |
| GET    | `/accounts/{id}`    | yes  | Any member's profile                      |
| PUT    | `/accounts/me/avatar` | yes | Replace the profile picture; body is the raw image |
| DELETE | `/accounts/me/avatar` | yes | Remove the profile picture              |
| GET    | `/invites`           | yes  | List invites: all for the owner, own for members |
| POST   | `/invites`           | yes  | Create an invite                          |
| GET    | `/invites/{code}`    | no   | Preview an invite: community and inviter  |
| DELETE | `/invites/{code}`    | yes  | Revoke, by creator or owner               |
| GET    | `/roles`             | yes  | List roles, highest first                 |
| POST   | `/roles`             | MANAGE_ROLES | Create a role at the bottom       |
| PATCH  | `/roles/{id}`        | MANAGE_ROLES | Change name, colour, position or permissions |
| DELETE | `/roles/{id}`        | MANAGE_ROLES | Delete a role                     |
| PUT    | `/accounts/{id}/roles/{roleId}` | MANAGE_ROLES | Assign a role         |
| DELETE | `/accounts/{id}/roles/{roleId}` | MANAGE_ROLES | Remove a role         |
| GET    | `/accounts/me/permissions` | yes | Effective permissions of the caller |
| GET    | `/blobs/{id}`       | no   | Blob bytes, immutable, cache forever      |

Error codes so far: `validation_failed`, `username_taken`, `registration_closed`,
`challenge_required`, `challenge_invalid`, `forbidden`, `rate_limited`,
`invite_invalid`, `invite_not_found`, `invite_unusable`, `role_not_found`,
`role_hierarchy`, `permission_escalation`, `default_role`,
`invalid_credentials`, `account_not_found`, `blob_not_found`,
`unsupported_image`, `image_dimensions`, `image_too_large`.

Usernames are 3 to 32 characters of letters, digits, underscore and dot, and
unique per server regardless of case. Passwords are 8 to 128 characters.

## Testing

- `*Test` classes run with `@QuarkusTest` against the application in the same
  JVM. Dev Services provides PostgreSQL.
- `*IT` classes extend the HTTP tests with `@QuarkusIntegrationTest` and run
  them against the packaged application. They are skipped by default and
  enabled by the `native` profile, so
  `mvn verify -Dnative -Dquarkus.native.container-build=true` also tests the
  native executable end to end. Run it before merging changes that add
  dependencies or touch serialisation.

- All tests that need an account register it through `testing.TestUsers`. Its
  first use bootstraps the server: the well-known `owner` account is created
  as the first account, opens registration and disables rate limiting for the
  rest of the run. A test that registers any other way first would make that
  account the owner and break the suite. Tests that change settings restore
  them in a `finally` block.
- Integration tests run the packaged application with the `test` profile
  (`quarkus.test.integration-test-profile`), so `%test` configuration applies
  to them too.

## Typed identifiers and value records

Identifiers are never bare `UUID`s or `String`s in method signatures. Each
kind of id is its own record implementing `common.Id`, for example
`account.AccountId` and `auth.SessionId`, so the compiler stops an account id
from being passed where a session id belongs. Row mappers construct these
types directly.

Each id record provides `newId()`, `fromString(String)` for JAX-RS path
parameters, and serialises to JSON as the plain UUID string. Repositories
bind them directly with `.bind("id", accountId)`;
`persistence.ValueArgumentFactory` handles the conversion, so call sites never
unwrap the value.

The same pattern covers other single-value records through `common.Value<T>`,
of which `Id` is the UUID case. `invite.InviteCode` wraps a `String` this
way. A `String`-valued record that arrives as a path parameter with a pattern
constraint in the contract must also implement `CharSequence`, or Bean
Validation cannot apply the constraint to it.
