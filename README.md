# Snatter

Snatter is an open source, self-hosted voice and text communication server for
gamers and hobbyist communities. Think of it as the server you run yourself
instead of relying on a hosted chat platform.

**Status: early development, not yet ready for production use.**

## Goals for v1

- Self-hosted server with local accounts and optional external identity
  providers (OpenID Connect).
- Open sign-up or invite-only, with bot protection that does not depend on a
  third party.
- Channels of three kinds: voice, text, or voice and text.
- Configurable audio quality per voice channel, including bitrate.
- Role-based access control with kick and ban by account or IP address.
- Video and screen sharing, in v1 or shortly after.

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
| `client/`   | The web client and, later, the desktop app        | AGPL-3.0    |
| `protocol/` | Protocol definitions shared by server and clients | Apache-2.0  |
| `website/`  | The public website at snatter.app, static HTML    | AGPL-3.0    |

## Running with Docker Compose

The quickest way to run Snatter, no JDK, Maven or Node.js required:

```
docker compose up --build
```

This builds the server and the web app from source and starts them in the
production profile. Open http://localhost:8080 and create the first account,
which becomes the owner. The web container serves the app and forwards `/api`
to the server, so the API is on the same port (try `/api/v1/server-info`).
Voice goes to the server directly, over UDP on port 8080: set
`SNATTER_MEDIA_ADDRESS` to the address browsers reach this machine at, such
as its LAN address; without it the server offers only its container's
addresses, which browsers usually cannot reach. Browsers give pages the
microphone only over HTTPS, or at `localhost`, so for voice from other
machines put a reverse proxy that terminates TLS in front of the web
container. The server keeps everything, its
SQLite database and uploaded content, in the `server-data` volume, so
`docker compose down` keeps your data and `docker compose down -v` wipes it.
To publish on another port, set `SNATTER_HTTP_PORT` in a `.env` file (see
`.env.example`).

## Developing locally

Run the server and the client side by side, each in its own terminal. You
need a JDK 25+, Maven and Node.js 24+; the READMEs in `server/` and
`client/` have the details.

```
cd server && mvn quarkus:dev                  # http://localhost:8080
cd client && npm install && npm run dev       # http://localhost:5173
```

Open http://localhost:5173. The client reaches the server through Vite's
proxy, and the first account you create on the fresh development database
becomes the owner. If port 8080 is taken, for example by the Docker Compose
server, start the server with `-Dquarkus.http.port=8081` and the client with
`SNATTER_SERVER=http://localhost:8081 npm run dev`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Commits must carry a DCO sign-off.

## License

Snatter is licensed under the GNU Affero General Public License v3.0, see
[LICENSE](LICENSE). The protocol definitions in `protocol/` are licensed under
the Apache License 2.0, see [protocol/LICENSE](protocol/LICENSE), so that
third-party clients, bots and SDKs can be built under any license.

Snatter is a project by [Snattra](https://github.com/Snattra).
Website: [snatter.app](https://snatter.app)
