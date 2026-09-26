# Client development

How the client is put together and the conventions it follows. Setup is in
the client README.

## Package layout

| Directory (`web/src/`) | Contents                                                  |
|------------------------|-----------------------------------------------------------|
| `api/`                 | Typed REST client and type aliases, from the generated `schema.d.ts` |
| `gateway/`             | The gateway connection: identify, sequence checks, reconnects |
| `state/`               | The per-server view and the store holding it              |
| `servers/`             | `ServerConnection`: session, REST and gateway of one server |
| `auth/`                | Registration challenge solving                            |
| `platform/`            | What the app needs from where it runs                     |
| `ui/`                  | React components, hooks and icons                         |

## Contract first

The protocol is `protocol/openapi/openapi.yaml`, shared with the server.
`openapi-typescript` turns it into `web/src/api/schema.d.ts` before every
script, and `api/types.ts` gives the schemas short names. REST calls go
through `openapi-fetch`, so paths, parameters and bodies are type-checked;
`unwrap` turns an error response into an `ApiRequestError` carrying the
`ApiError` body. Gateway frames are the generated `GatewayServerFrame` and
`GatewayClientFrame` unions, discriminated by `type`, so a `switch` on
`frame.type` is exhaustive and a new frame in the contract fails the build
where it is not handled. Close codes work the same way (`gateway/closeCodes.ts`).

## Modes and servers

A server installation will serve the app for itself alone: single-server
mode, where the server is the page's origin. The hosted app at snatter.app/web
and the desktop app will manage many servers: multi-server mode, not built
yet (`config.ts`). So that multi-server needs no rework, **everything is kept
per server from the start**: the store holds entries by origin, and each
server has one `ServerConnection` (`connectionTo(origin)`) owning its token,
REST client and gateway.

## State

The server sends state, then differences, and the client mirrors that. A
server's `ServerView` (`state/serverView.ts`) is built from `ready` and
replaced by `applyFrame` for every later frame; both are pure functions,
unit-tested without a browser. After a reconnect the next `ready` replaces the
view wholesale. Messages will be fetched over REST per channel and live
alongside, not in, the view.

Typing indicators keep only when each ends; `typingIn` filters by the current
time, and the UI ticks a clock (`useNow`) only while someone is typing.

## Gateway

`gateway/Gateway.ts` identifies with the session token, requires frames to be
numbered without gaps (a gap closes and resyncs), and on losing the connection
retries with jittered, growing delays up to 30 seconds. `closeAction` decides
between reconnecting (network trouble, `too_slow`, `identify_timeout`) and
ending the session (`session_ended`, `authentication_failed`, `banned`, and
protocol errors, which a retry would repeat). An ended session signs the
client out with a notice.

## Authentication

Bearer tokens everywhere, never cookies: the hosted app talks to other
people's servers across origins, where cookies would be third-party. Tokens
are kept through `platform.secrets`: localStorage in the browser, the OS
keychain through Electron's `safeStorage` in the desktop app. localStorage is
only as safe as the page is free of injected script, so user content is never
rendered as HTML, and the page is served with a strict Content-Security-Policy
(in `web/nginx/default.conf.template`): only the app's own files run, nothing
inline, and Trusted Types are required, so the browser refuses any string
that would become HTML or script. The one exception is `trustedTypes.ts`,
whose `snatter` policy vouches for script URLs from the app's own origin;
creating a worker goes through `trustedScriptUrl`. `npm run preview` serves
the production build under the same policy, read from the nginx template, so
anything it blocks shows up before a container is built.

A fresh server reports `registration.setupRequired`; the sign-in screen then
only offers creating the first account, which becomes the owner and needs no
invite or challenge.

Registration solves the server's ALTCHA proof of work in a Web Worker
(`auth/altcha.worker.ts`) so the page stays responsive.

## Platform

`platform/platform.ts` is the seam for the desktop shell: everything that
differs between browser and desktop goes behind the `Platform` interface,
asynchronous because the desktop side will answer over IPC. The browser
implementation does what a browser can and nothing more.

## UI

Close to Discord's layout, dark by default: a server rail on the far left,
the server's channels (collapsible to icons), the channel in the middle, and
a member list on the right (can be closed) showing presence and who is typing
in the current channel. Layout choices are remembered per browser with
`usePreference`.

The look comes from the design system in `client/design-system/`: its
`README.md` is the brand book (colour, type, spacing, states), `motion.md`
says which animation means what, and each component has guidelines and a
live preview under `components/`. In the app, `tokens.css` holds the tokens
as CSS custom properties, `styles.css` the `sn-` component classes, and
`ui/controls.tsx`, `ui/surfaces.tsx`, `ui/people.tsx` and `ui/layout.tsx`
the components built on them. `design-system/adoption.md` maps one onto the
other. Change a token in `tokens.json` and `tokens.css` together, and design
a new component there before building it here.
