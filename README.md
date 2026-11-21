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
which becomes the owner. The web container, Caddy, serves the app and passes
`/api` on to the server, so the API is on the same port (try
`/api/v1/server-info`).

Settings go in a `.env` file beside `compose.yaml` (see `.env.example`):

- `SNATTER_ADDRESS`: the address members reach this machine at, a host name
  pointing here or its public IP address. With it, the app is served over
  HTTPS, with a certificate from Let's Encrypt, for which ports 80 and 443 of
  that address must reach this machine's `SNATTER_HTTP_PORT` (8080) and
  `SNATTER_HTTPS_PORT` (8443), as by forwarding them on the router. Without
  it, plain HTTP: everything works but voice in a browser, since browsers
  give the microphone only to HTTPS pages, or at `localhost`.
- `SNATTER_MEDIA_ADDRESS`: voice goes to the server directly, over UDP on
  port 8080, at `SNATTER_ADDRESS` unless this names another address, such as
  the machine's LAN address. Without either, the server offers only its
  container's addresses, which browsers usually cannot reach.

The server keeps everything, its SQLite database and uploaded content, in the
`server-data` volume, and Caddy its certificates in `web-data`, so `docker
compose down` keeps them and `docker compose down -v` wipes them.

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
