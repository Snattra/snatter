# Snatter protocol

The wire contract between a Snatter server and its clients. Everything in
this directory is licensed under the Apache License 2.0 (see `LICENSE` here)
so that third-party clients, bots and SDKs can be built under any license,
while the server and official client remain AGPL.

## Contents

| Path                   | What it is                                       |
|------------------------|--------------------------------------------------|
| `openapi/openapi.yaml` | The HTTP API, OpenAPI 3.0. Hand-written; this is the source of truth. |

Planned: the WebSocket gateway event schema and the media signalling protocol.

## How the contract is used

The specification is written first. The server build generates JAX-RS
interfaces and request/response types from it with
[openapi-generator](https://openapi-generator.tech), and the server's resource
classes implement those interfaces, so the server cannot drift from the
document without failing to compile. The same file is served unchanged by a
running server at `/q/openapi`.

Clients and SDKs are expected to generate their code from the same file.

## Changing the API

1. Edit `openapi/openapi.yaml`. Keep changes within a version additive; an
   incompatible change means a new path prefix and a bump of `apiVersion` in
   `GET /api/v1/server-info`.
2. Build the server (`mvn verify` in `server/`) and adjust the resource
   classes until it compiles and the tests pass.
3. Every error response uses the `ApiError` schema with a stable `error`
   code. Document new codes in the response description of the operation
   that produces them.
4. An operation that returns binary content must advertise exactly one media
   type across all its responses, so its error responses carry a description
   only and the success response is declared as `application/octet-stream`.
   See `getBlob` for the pattern.
