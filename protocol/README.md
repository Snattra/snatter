# Snatter protocol

The wire contract between a Snatter server and its clients. Everything in
this directory is licensed under the Apache License 2.0 (see `LICENSE` here)
so that third-party clients, bots and SDKs can be built under any license,
while the server and official client remain AGPL.

## Contents

| Path                     | What it is                                                |
|--------------------------|-----------------------------------------------------------|
| `openapi/openapi.yaml`   | HTTP API, OpenAPI 3.1. Generated, do not edit by hand.    |
| `openapi/openapi.json`   | Same document in JSON.                                    |

Planned: the WebSocket gateway event schema and the media signalling protocol.

## How the OpenAPI document is maintained

The server generates it from the resource classes on every Maven build
(`mvn package` in `server/`). Changes to the API therefore always show up as
a diff here, which is where they should be reviewed. A build that leaves this
directory dirty means the committed contract is out of date.

The API is versioned in the path (`/api/v1/...`) and by the `apiVersion`
field of `GET /api/v1/server-info`. Within a version, changes are additive.
