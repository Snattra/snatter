# Snatter protocol

The wire contract between a Snatter server and its clients. Everything in
this directory is licensed under the Apache License 2.0 (see `LICENSE` here)
so that third-party clients, bots and SDKs can be built under any license,
while the server and official client remain AGPL.

## Contents

| Path                   | What it is                                       |
|------------------------|--------------------------------------------------|
| `openapi/openapi.yaml` | The HTTP API and the WebSocket gateway's frames, OpenAPI 3.0. Hand-written; this is the source of truth. |
| `CHANGELOG.md`         | What changed in each protocol version.           |

## How the contract is used

The specification is written first. The server build generates JAX-RS
interfaces and request/response types from it with
[openapi-generator](https://openapi-generator.tech), and the server's resource
classes implement those interfaces, so the server cannot drift from the
document without failing to compile. The same file is served unchanged by a
running server at `/q/openapi`.

Clients and SDKs are expected to generate their code from the same file.

## Versions

The protocol version is `info.version` in `openapi.yaml`, written
`major.minor`. A minor version only adds; a major version breaks existing
clients. Servers and clients tell each other their versions (see "Versions"
in the contract's description), so each side can tell when the other is too
old:

- The server accepts every client of its own major version. A server
  release can raise the minimum (`Protocol.MIN_CLIENT`), for example to turn
  away a client with a known problem.
- The official client works with servers back to the first version of the
  previous major, since members cannot update the servers they use.
- Between those, a client behind the server offers an update, and one ahead
  of it leaves out what the server cannot do yet.

What counts as breaking: removing or renaming anything, changing a field's
type or meaning, a new required request field, or a new check that rejects
requests that were valid. Everything else is a minor change, as long as an
older client that ignores it still works.

## Changing the API

1. Edit `openapi/openapi.yaml` and bump `info.version`. A breaking change
   moves the major version in the same pull request: CI compares the contract
   with main and fails otherwise (`check-breaking.sh`, which also runs
   locally with oasdiff and yq). An addition moves the minor version, unless
   it already moved since the last release. Note the change in
   `CHANGELOG.md`, and move `Protocol.CURRENT` in the server and
   `PROTOCOL_VERSION` in the web client along; a test on each side fails
   until they match.
2. Build the server (`mvn verify` in `server/`) and adjust the resource
   classes until it compiles and the tests pass.
3. Every error response uses the `ApiError` schema with a stable `error`
   code. Document new codes in the response description of the operation
   that produces them.
4. An operation that returns binary content must advertise exactly one media
   type across all its responses, so its error responses carry a description
   only and the success response is declared as `application/octet-stream`.
   See `getBlob` for the pattern.
5. Lint the contract with `npx @redocly/cli@2.57.0 lint` in this directory,
   the version CI uses; the rules are in `redocly.yaml`. Operations open to
   anyone say so with `security: []`.
