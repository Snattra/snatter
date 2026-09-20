# Developing the Snatter server

This document is for people changing the server. For running it, see the
server README.

## Package layout

Base package is `app.snatter.server`. Each feature gets its own sub-package
containing its resources, services and repositories together, rather than
splitting by technical layer:

| Package       | Contents                                             |
|---------------|------------------------------------------------------|
| `info`        | Public server description endpoint                   |
| `settings`    | Community-wide settings                              |
| `account`     | Accounts and the identity model                      |
| `auth`        | Passwords, sessions, HTTP authentication             |
| `api`         | Shared API error types and exception mappers         |
| `common`      | Domain-wide abstractions such as `Id`                |
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
account id, username and session id, so `@Authenticated`, `@RolesAllowed` and
`SecurityIdentity` work as usual in resources.

Sessions record the client IP as the server sees it. Behind a reverse proxy
that is the proxy's address until trusted-proxy handling is configured; this
arrives together with IP bans.

## HTTP API

All endpoints live under `/api/v1`. Errors have one shape:

```json
{"error": "<stable_code>", "message": "human readable", "fields": {"username": "..."}}
```

`fields` appears only for validation failures. Throw `api.ApiException` for
domain errors; it carries the status and code.

| Method | Path                | Auth | Purpose                                   |
|--------|---------------------|------|-------------------------------------------|
| GET    | `/server-info`      | no   | Software and community description        |
| POST   | `/auth/register`    | no   | Create a local account, returns a session |
| POST   | `/auth/login`       | no   | Username and password, returns a session  |
| POST   | `/auth/logout`      | yes  | Revoke the calling session                |
| GET    | `/accounts/me`      | yes  | The calling account                       |

Error codes so far: `validation_failed`, `username_taken`,
`invalid_credentials`.

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

## Typed identifiers and value records

Identifiers are never bare `UUID`s or `String`s in method signatures. Each
kind of id is its own record implementing `common.Id`, for example
`account.AccountId` and `auth.SessionId`, so the compiler stops an account id
from being passed where a session id belongs. Row mappers construct these
types directly.

Each id record provides `newId()`, `fromString(String)` for JAX-RS path
parameters, and serialises to JSON as the plain UUID string. Repositories
bind them directly with `.bind("id", accountId)`; `persistence.IdArgumentFactory`
handles the conversion, so call sites never unwrap the value.

Apply the same idea to other frequently passed values where a plain `String`
invites mix-ups, once they earn it.
