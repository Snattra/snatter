#!/usr/bin/env bash
# Runs a released version of Snatter on the demo host, from the compose.yaml
# beside this script. The deploy workflow (.github/workflows/deploy-demo.yml)
# logs in with a key that may run only this script, and names the version as
# its command; by hand, on the host:
#
#   ./deploy.sh 0.1.0
#
# The version goes into the .env beside this script, where compose reads it,
# so that compose commands run here later keep to it.

set -euo pipefail

version=${SSH_ORIGINAL_COMMAND:-${1:-}}
if ! [[ $version =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$ ]]; then
  echo "Usage: deploy.sh VERSION, such as 0.1.0" >&2
  exit 2
fi

cd "$(dirname "$(readlink -f "$0")")"

touch .env
others=$(grep -v '^SNATTER_VERSION=' .env || true)
{
  [ -n "$others" ] && printf '%s\n' "$others"
  printf 'SNATTER_VERSION=%s\n' "$version"
} > .env.next
cat .env.next > .env
rm .env.next

docker compose pull --quiet
docker compose up -d --remove-orphans --wait
docker image prune -f > /dev/null
echo "Snatter $version is running"
