# Snatter

Snatter is an open source, self-hosted voice and text communication server for
gamers and hobbyist communities. Think of it as the server you run yourself
instead of relying on a hosted chat platform.

**Status: early development. Nothing is usable yet.**

## Goals for v1

- Self-hosted server with local accounts and optional external identity
  providers (OpenID Connect).
- Open sign-up or invite-only, with bot protection that does not depend on a
  third party.
- Channels of three kinds: voice, text, or voice and text.
- Configurable audio quality per voice channel, including bitrate.
- Role-based access control with kick and ban by account or IP address.
- Video and screen sharing if time permits, otherwise in v1.5.

## Architecture in one paragraph

The server is written in Java on [Quarkus](https://quarkus.io). Text, presence
and signalling travel over HTTPS and WebSocket. Voice and video use standard
WebRTC on the wire, forwarded by a purpose-built selective forwarding unit
(SFU) inside the server, so the client can be an ordinary browser engine. The
desktop client is built with web technologies in a native shell and targets
Windows first, then macOS, then Linux.

## Repository layout

| Directory   | Contents                                          | License     |
|-------------|---------------------------------------------------|-------------|
| `server/`   | The Snatter server (Java, Quarkus)                | AGPL-3.0    |
| `client/`   | The desktop client                                | AGPL-3.0    |
| `protocol/` | Protocol definitions shared by server and clients | Apache-2.0  |


## Running with Docker Compose

The quickest way to run a Snatter server, no JDK or Maven required:

```
cp .env.example .env      # set SNATTER_DB_PASSWORD
docker compose up --build
```

This builds the server from source and starts it together with PostgreSQL in
the production profile. The server listens on http://localhost:8080; try
`/api/v1/server-info` and `/q/health`. Database files persist in the
`db-data` and `server-data` volumes, so `docker compose down` keeps your data and
`docker compose down -v` wipes it.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Commits must carry a DCO sign-off.

## License

Snatter is licensed under the GNU Affero General Public License v3.0, see
[LICENSE](LICENSE). The protocol definitions in `protocol/` are licensed under
the Apache License 2.0, see [protocol/LICENSE](protocol/LICENSE), so that
third-party clients, bots and SDKs can be built under any license.

Snatter is a project by [Snattra](https://github.com/Snattra).
Website: [snatter.app](https://snatter.app)
