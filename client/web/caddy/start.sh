#!/bin/sh
# Starts Caddy for SNATTER_ADDRESS, the address members reach this machine at:
#
#   unset           plain HTTP only, on port 80 in the container
#   a host name     HTTPS with a certificate from Let's Encrypt
#   an IP address   HTTPS with a six-day certificate for the address
#
# HTTPS needs ports 80 and 443 of the address to reach the container, for
# Let's Encrypt to check the address is this machine's.

set -eu

address=${SNATTER_ADDRESS:-}

# Only what a host name or an IP address is made of, as it goes into the
# Caddyfile as it is.
case $address in
  *[!][A-Za-z0-9.:-]*)
    echo "SNATTER_ADDRESS is not a host name or an IP address: $address" >&2
    exit 2
    ;;
esac

case $address in
  "")
    site=:80
    https=none
    ;;
  \[*\] | *:*:*)
    # IPv6, which a site address writes in brackets.
    site="[${address#[}"
    site="${site%]}]"
    https=ip
    ;;
  *[!0-9.]*)
    site=$address
    https=name
    ;;
  *)
    site=$address
    https=ip
    ;;
esac

export SNATTER_SITE="$site" SNATTER_HTTPS="$https"
echo "Serving Snatter at ${address:-any address, over plain HTTP} ($https)"
exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
