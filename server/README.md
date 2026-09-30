# Snatter server

The Snatter server: HTTP API, real-time gateway and, later, the media
forwarding unit. Java 25+, [Quarkus](https://quarkus.io), Maven. Licensed under
AGPL-3.0 (see the repository root `LICENSE`).

Looking to run a server rather than build one? The repository root README
covers Docker Compose, which needs no toolchain at all.

## Requirements

- JDK 25 or newer
- Maven 3.9 or newer
- Docker (or Podman) for dev mode and tests, which start a throwaway
  PostgreSQL container automatically through Quarkus Dev Services

[SDKMAN](https://sdkman.io) installs the JDK and Maven: `sdk install java
26.0.2+1.1-tem` and `sdk install maven`. There is deliberately no Maven wrapper
in this repository.

## Running in development

```
mvn quarkus:dev
```

The server listens on http://localhost:8080 with live reload. Useful
endpoints:

| Path                   | Purpose                                          |
|------------------------|--------------------------------------------------|
| `/api/v1/server-info`  | Software name, version, API version and community name |
| `/q/health`            | Health checks (`/live` and `/ready` too)         |
| `/q/dev-ui`            | Quarkus Dev UI (dev mode only)                   |

## Building

Run the tests and build the runnable jar:

```
mvn verify
java -jar target/quarkus-app/quarkus-run.jar
```

Build a native executable (needs Docker or Podman, no local GraalVM):

```
mvn verify -Dnative -Dquarkus.native.container-build=true
./target/snatter-server-*-runner
```

Build the container image used by the root `compose.yaml`:

```
docker build -t snatter-server ./server
```

## Configuration

Environment variables say where the server runs; nothing else is needed, and
there is no configuration file to mount.

| Variable               | Default                                        | Purpose                        |
|------------------------|------------------------------------------------|--------------------------------|
| `SNATTER_DB_URL`       | `jdbc:postgresql://localhost:5432/snatter`     | PostgreSQL JDBC URL            |
| `SNATTER_DB_USER`      | `snatter`                                      | Database user                  |
| `SNATTER_DB_PASSWORD`  | none, required                                 | Database password              |
| `SNATTER_STORAGE_ROOT` | `./data` (`/var/lib/snatter` in the container) | Directory for uploaded content such as avatars |
| `QUARKUS_HTTP_PORT`    | `8080`                                         | Port the server listens on     |

Behind a reverse proxy, also set `QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING`
and `QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED` to `true` so rate limits see the
client's address, as `compose.yaml` does.

PostgreSQL is the only supported database. The schema is created and upgraded
automatically at startup.

How the community runs is not configuration: registration mode, the bot
check and its difficulty, rate limits, how long sessions last and voice
bitrates are server settings that members with the `MANAGE_SERVER`
permission change at runtime, in the app's Server settings or through
`PATCH /api/v1/server-settings`. The first account registered on a fresh
server becomes the owner.

## Contributing

Architecture, conventions and the API are described in
[docs/development.md](docs/development.md). See also the repository root
`CONTRIBUTING.md` for the sign-off requirement.
