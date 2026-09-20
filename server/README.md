# Snatter server

The Snatter server: HTTP API, real-time gateway and, later, the media
forwarding unit. Java 25+, [Quarkus](https://quarkus.io), Maven. Licensed under
AGPL-3.0 (see the repository root `LICENSE`).

## Requirements

- JDK 25 or newer (the code targets Java 25 bytecode so that native images can be built; the JVM build runs on JDK 26 too)
- Maven 3.9 or newer
- Docker (or Podman) for dev mode and tests, which start a throwaway
  PostgreSQL container automatically through Quarkus Dev Services

[SDKMAN](https://sdkman.io) installs the JDK and Maven: `sdk install java
26.0.2+1.1-tem` and `sdk install maven`. There is deliberately no Maven wrapper
in this repository.

## Developing

Run in dev mode with live reload:

```
mvn quarkus:dev
```

The server listens on http://localhost:8080. Useful endpoints:

| Path                   | Purpose                                     |
|------------------------|---------------------------------------------|
| `/api/v1/server-info`  | Software name, version, API version and the community name |
| `/api/v1/auth/*`, `/api/v1/accounts/me` | Accounts and sessions, see Authentication below |
| `/q/health`            | Health checks (`/live` and `/ready` too)    |
| `/q/dev-ui`            | Quarkus Dev UI (dev mode only)              |

Run the tests and build the runnable jar:

```
mvn verify
java -jar target/quarkus-app/quarkus-run.jar
```

## Configuration

Defaults live in `src/main/resources/application.properties`. Every property
can be overridden by an environment variable using Quarkus' naming rules, for
example `QUARKUS_HTTP_PORT=9000`.

Production database settings:

| Variable              | Default                                      |
|-----------------------|----------------------------------------------|
| `SNATTER_DB_URL`      | `jdbc:postgresql://localhost:5432/snatter`   |
| `SNATTER_DB_USER`     | `snatter`                                    |
| `SNATTER_DB_PASSWORD` | none, required                               |


## Authentication

Sessions are opaque bearer tokens, not JWTs. A token looks like
`snt_<43 url-safe base64 characters>`, is sent as
`Authorization: Bearer snt_...`, and is stored only as a SHA-256 hash in the
`session` table. Deleting the row revokes the session immediately, which is
what kick and ban need. Sessions expire after `snatter.auth.session-lifetime`
(default 30 days).

Passwords are hashed with Argon2id (64 MiB, 3 iterations) in PHC string
format, so the parameters can be raised later without invalidating old hashes.

| Method | Path                    | Auth | Purpose                                   |
|--------|-------------------------|------|-------------------------------------------|
| POST   | `/api/v1/auth/register` | no   | Create a local account, returns a session |
| POST   | `/api/v1/auth/login`    | no   | Username and password, returns a session  |
| POST   | `/api/v1/auth/logout`   | yes  | Revoke the calling session                |
| GET    | `/api/v1/accounts/me`   | yes  | The calling account                       |

Usernames are 3 to 32 characters of letters, digits, underscore and dot, and
unique regardless of case. Passwords are 8 to 128 characters.

Every account has one or more rows in `identity`, keyed by `(issuer,
subject)`. Local accounts use issuer `local`. External OpenID Connect
providers will add rows with their issuer URL, so one account can later be
reached through several logins.

Errors are returned as `{"error": "<code>", "message": "...", "fields": {...}}`
where `fields` only appears for validation failures. Codes used so far:
`validation_failed`, `username_taken`, `invalid_credentials`.

Sessions record the client IP as seen by the server. Behind a reverse proxy
that is the proxy's address until `quarkus.http.proxy.proxy-address-forwarding`
and a trusted proxy list are configured; this is planned together with IP bans.

## Database access

PostgreSQL is the only supported database.

- **Flyway owns the schema.** Migrations live in
  `src/main/resources/db/migration` as `V<n>__<description>.sql` and run at
  startup. Never edit a migration that has been committed; add a new one.
- **Plain SQL through [JDBI 3](https://jdbi.org).** No ORM. Each feature
  package has a repository class that injects `Jdbi`, writes SQL in text
  blocks, and maps rows onto records with an explicit `RowMapper`.
- **Transactions are JTA.** Put `@Transactional` on the service or repository
  method and use `jdbi.withHandle` / `jdbi.useHandle` inside it. Agroal enlists
  the connection in the transaction. Do not use `jdbi.inTransaction` or
  `handle.begin()`; they fight the JTA-managed connection.
- **Timestamps** are `TIMESTAMPTZ` in the database and `Instant` in Java. Read
  them as `OffsetDateTime` and call `toInstant()`, because the PostgreSQL
  driver does not convert `timestamptz` to `Instant` directly.


## Container image

`Dockerfile` in this directory is a multi-stage build that compiles the server
and produces a JVM image on the Red Hat UBI OpenJDK runtime. It needs no local
toolchain and is what the repository root `compose.yaml` uses:

```
docker build -t snatter-server ./server
```

The Dockerfiles under `src/main/docker` are the standard Quarkus ones. They
expect a prebuilt `target/` directory and are the better choice in CI, where
the build already ran.

## Native executable

The `native` Maven profile builds a native executable with GraalVM or Mandrel:

```
mvn verify -Dnative -Dquarkus.native.container-build=true
```

The container build needs Docker or Podman and does not require a local
GraalVM. Native builds are not yet part of the regular workflow, but run one
before merging anything that touches static initialisation or reflection.

Rules learned so far:

- Never keep a `SecureRandom` or `Random` in a static field. GraalVM refuses
  to snapshot one into the image heap. Use an instance field on a CDI bean,
  or `UUID.randomUUID()` for random bits.
- Run `mvn clean` before a native build after changing the compiler release,
  otherwise stale class files from the previous level are reused.
- Quarkus registers types for reflection when they appear in resource method
  signatures. A type only ever passed to `Response.entity(...)`, such as
  `ApiError`, needs `@RegisterForReflection` or it serialises as `{}` in the
  native image.

The `*IT` test classes extend the HTTP tests and run them against the packaged
application. They are skipped by default and enabled by the `native` profile,
so a full `mvn verify -Dnative -Dquarkus.native.container-build=true` also
tests the native executable end to end.

## Package layout

Base package is `app.snatter.server`. Each feature gets its own sub-package
(for example `info`, `settings`, `account`, `auth`, later `channel`, `gateway`, `media`)
containing its resources, services and repositories together, rather than
splitting by technical layer. Cross-cutting infrastructure such as the JDBI
producer lives in `persistence`, and shared API error types in `api`.
