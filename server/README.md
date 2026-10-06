# Snatter server

The Snatter server: HTTP API, real-time gateway and, later, the media
forwarding unit. Java 25+, [Quarkus](https://quarkus.io), Maven. Licensed under
AGPL-3.0 (see the repository root `LICENSE`).

Looking to run a server rather than build one? The repository root README
covers Docker Compose, which needs no toolchain at all.

## Requirements

- JDK 25 or newer
- Maven 3.9 or newer
- Docker (or Podman), only to build the native executable

The database is SQLite, built into the server, so dev mode and the tests need
nothing else running.

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
| `SNATTER_STORAGE_ROOT` | `./data` (`/var/lib/snatter` in the container) | Directory for everything the server keeps: the database and uploaded content such as avatars |
| `SNATTER_DB_PATH`      | `snatter.db` in `SNATTER_STORAGE_ROOT`          | The SQLite database file, if it should live elsewhere |
| `QUARKUS_HTTP_PORT`    | `8080`                                         | Port the server listens on     |
| `SNATTER_MEDIA_PORT`   | `8080`                                         | UDP port for voice. Open it, and publish it from a container, beside the HTTP port |
| `SNATTER_MEDIA_ADDRESS` | none                                          | The address people reach the voice port at, when the server is behind NAT or in a container: a public IP address or a host name |

Behind a reverse proxy, also set `QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING`
and `QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED` to `true` so rate limits see the
client's address, as `compose.yaml` does.

The database is a single SQLite file. The server creates it, and its
directory, on first start, and upgrades the schema automatically. Keep it on a
local disk: SQLite's locking does not work over network file systems such as
NFS or SMB shares, and a database there can be corrupted. Uploaded content
may live on such a share; point `SNATTER_DB_PATH` at a local disk then.

To back up, stop the server and copy the data directory. Copying the database
file while the server runs can give a broken copy; `sqlite3 snatter.db
".backup backup.db"` makes a consistent one without stopping it.

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
