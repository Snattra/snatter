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

The native build fails for now: the libraries that carry voice keep
objects in static fields that native images refuse. Making them work is
[#10](https://github.com/Snattra/snatter/issues/10); until then, run the
jar or the container image.

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
| `SNATTER_TRUSTED_PROXIES` | none                                       | The reverse proxies in front of the server, as IP addresses or CIDR ranges separated by commas, such as `127.0.0.1` or `172.16.0.0/12`. Only from them does the server take the client's address and the page's from `X-Forwarded-*` headers; unset, it takes them from nobody |
| `SNATTER_WEB_APP`       | `https://web.snatter.app`                      | The web app the server's own address, and its invite links, lead to when no app is served in front of it |

Behind a reverse proxy, set `SNATTER_TRUSTED_PROXIES` to its address, so that
rate limits see each client's own address rather than the proxy's. Name only
the proxy: any client named there can claim to be anyone. The proxy must
replace the `X-Forwarded-For`, `X-Forwarded-Proto` and `X-Forwarded-Host`
headers a client sends, not add to them, as the server takes the first
address in `X-Forwarded-For`. Caddy's `reverse_proxy` does so by itself; in
nginx, set them outright (`proxy_set_header X-Forwarded-For $remote_addr;`),
rather than with `$proxy_add_x_forwarded_for`.

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
