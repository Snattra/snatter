#!/usr/bin/env bash
# Fails when openapi.yaml breaks something since its version at a git ref
# (origin/main unless given) without moving to a new major version. CI runs
# it on pull requests. It needs oasdiff and yq (mikefarah/yq) on the path.
#
#   ./check-breaking.sh [ref]

set -euo pipefail
cd "$(dirname "$0")"
ref=${1:-origin/main}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# oasdiff compares operations, and no operation carries the gateway frames,
# so both sides get one that does: client frames as what it is sent, server
# frames and close reasons as what it answers. Each side is then held to the
# rules for requests and responses respectively.
gateway='.paths["/api/v1/gateway"].get = {
  "operationId": "gateway",
  "requestBody": {"content": {"application/json": {"schema":
    {"$ref": "#/components/schemas/GatewayClientFrame"}}}},
  "responses": {"200": {"description": "Frames", "content": {"application/json": {"schema": {
    "type": "object",
    "properties": {
      "frame": {"$ref": "#/components/schemas/GatewayServerFrame"},
      "closeReason": {"$ref": "#/components/schemas/GatewayCloseReason"}}}}}}}
}'
git show "$ref:./openapi/openapi.yaml" | yq "$gateway" > "$work/base.yaml"
yq "$gateway" openapi/openapi.yaml > "$work/revision.yaml"

# Clients must cope with enum values, frame types and close reasons they do
# not know, so a minor version may add them to what the server sends. But it
# may not take away a field a client reads, even one that is optional.
cat > "$work/levels.txt" <<'EOF'
response-property-enum-value-added info
response-body-one-of-added info
response-property-one-of-added info
response-optional-property-removed err
EOF

diff=("$work/base.yaml" "$work/revision.yaml" --flatten-allof --severity-levels "$work/levels.txt")
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  oasdiff changelog "${diff[@]}" --format markdown >> "$GITHUB_STEP_SUMMARY"
fi

# Exit status 1 means breaking changes; anything else is oasdiff failing.
status=0
oasdiff breaking "${diff[@]}" --fail-on WARN || status=$?
if [ "$status" -ne 1 ]; then
  exit "$status"
fi
from=$(yq .info.version "$work/base.yaml")
to=$(yq .info.version "$work/revision.yaml")
if [ "${to%%.*}" -gt "${from%%.*}" ]; then
  echo "Breaking, and the major version moves from $from to $to."
  exit 0
fi
echo "Breaking changes need a new major version: info.version is $to, as in $ref." >&2
exit 1
