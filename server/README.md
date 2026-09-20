# Snatter server

The Snatter server: HTTP API, real-time gateway and, later, the media
forwarding unit. Java 26, [Quarkus](https://quarkus.io), Maven. Licensed under
AGPL-3.0 (see the repository root `LICENSE`).

## Requirements

- JDK 26
- Maven 3.9 or newer

[SDKMAN](https://sdkman.io) installs both: `sdk install java 26.0.2+1.1-tem`
and `sdk install maven`. There is deliberately no Maven wrapper in this
repository.

## Developing

Run in dev mode with live reload:

```
mvn quarkus:dev
```

The server listens on http://localhost:8080. Useful endpoints:

| Path                   | Purpose                                   |
|------------------------|-------------------------------------------|
| `/api/v1/server-info`  | Name, version and API version of the server |
| `/q/health`            | Health checks (`/live` and `/ready` too)  |
| `/q/dev-ui`            | Quarkus Dev UI (dev mode only)            |

Run the tests and build the runnable jar:

```
mvn verify
java -jar target/quarkus-app/quarkus-run.jar
```

## Configuration

Defaults live in `src/main/resources/application.properties`. Every property
can be overridden by an environment variable using Quarkus' naming rules, for
example `QUARKUS_HTTP_PORT=9000`.

## Native executable

The `native` Maven profile builds a native executable with GraalVM or Mandrel:

```
mvn verify -Dnative -Dquarkus.native.container-build=true
```

The container build needs Docker or Podman and does not require a local
GraalVM. Native builds are not yet part of the regular workflow.

## Package layout

Base package is `app.snatter.server`. Each feature gets its own sub-package
(for example `info`, later `auth`, `channel`, `gateway`, `media`) containing
its resources, services and persistence types together, rather than splitting
by technical layer.
