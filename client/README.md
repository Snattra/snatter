# Snatter client

The Snatter app: a web client, and later a desktop app for Windows, macOS and
Linux built around it. TypeScript, [React](https://react.dev),
[Vite](https://vite.dev). Licensed under AGPL-3.0 (see the repository root
`LICENSE`).

| Directory        | Contents                                                      |
|------------------|---------------------------------------------------------------|
| `web/`           | The app itself, built for every mode                          |
| `design-system/` | The design system: tokens, guidelines, component previews     |

## Requirements

- Node.js 24 or newer
- A Snatter server to talk to; see `server/README.md` for `mvn quarkus:dev`

## Running in development

```
npm install
npm run dev
```

The app is served on http://localhost:5173 and reaches the server through
Vite's proxy, by default at http://localhost:8080. Point it elsewhere with
`SNATTER_SERVER=http://localhost:8081 npm run dev`. The first account
registered on a fresh server becomes its owner.

To run the app as the hosted and desktop apps do, adding servers by their
address rather than talking to the one behind the proxy:

```
npm run dev:multi
```

Servers let apps on `http://localhost` connect by default, so a local server
at `localhost:8080` can be added as it is.

## Checking and testing

```
npm run check     # type check
npm test          # unit tests
npm run build     # production build into web/dist
npm run build:hosted  # the hosted, multi-server build into web/dist-hosted
npm run preview -w web   # serve that build under the production CSP
npm run preview:hosted -w web  # serve the hosted build under its own CSP
```

Every script first generates the API types from
`protocol/openapi/openapi.yaml` into `web/src/api/schema.d.ts`, which is not
committed.

## Container image

`web/Dockerfile` builds the app and serves it with Caddy, which passes `/api`
and the gateway on to the server named by `SNATTER_SERVER_URL`, and serves
the app over HTTPS when `SNATTER_ADDRESS` names the address it is reached at
(`web/caddy/start.sh`). Build it from the repository root, since it needs
`protocol/`:

```
docker build -f client/web/Dockerfile -t snatter-web .
```

The root `compose.yaml` runs it together with a server.
