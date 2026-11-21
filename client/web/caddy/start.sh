#!/bin/sh
# Starts Caddy for SNATTER_ADDRESS, the address members reach this machine at:
#
#   unset           plain HTTP only, on port 80 in the container
#   a host name     HTTPS with a certificate from Let's Encrypt
#   an IP address   HTTPS with a six-day certificate for the address
#
# HTTPS needs ports 80 and 443 of the address to reach the container, for
# Let's Encrypt to check the address is this machine's. The address has no
# port: those are the compose file's.

set -eu

refuse() {
  echo "SNATTER_ADDRESS is not a host name or an IP address, without a port: $1" >&2
  exit 2
}

# Four numbers from 0 to 255.
is_ipv4() {
  case $1 in *[!0-9.]* | .* | *. | *..*) return 1 ;; esac
  old_ifs=$IFS
  IFS=.
  # shellcheck disable=SC2086 # split on the dots, which IFS now is
  set -- $1
  IFS=$old_ifs
  [ $# -eq 4 ] || return 1
  for part in "$@"; do
    [ ${#part} -le 3 ] && [ "$part" -le 255 ] || return 1
  done
}

# Hex digits and colons, two colons at least, and a :: at most once.
is_ipv6() {
  case $1 in "" | *[!0-9A-Fa-f:]* | *:::*) return 1 ;; esac
  case $1 in *:*:*) ;; *) return 1 ;; esac
  case $1 in *::*::*) return 1 ;; esac
  [ ${#1} -le 39 ]
}

# Labels of letters, digits and hyphens, not starting or ending with one,
# separated by dots, with a letter somewhere so it is no IP address.
is_host_name() {
  case $1 in "" | *[!A-Za-z0-9.-]* | .* | *. | *..* | -* | *-.* | *.-* | *-) return 1 ;; esac
  case $1 in *[A-Za-z]*) ;; *) return 1 ;; esac
  [ ${#1} -le 253 ] || return 1
  old_ifs=$IFS
  IFS=.
  # shellcheck disable=SC2086 # split on the dots, which IFS now is
  set -- $1
  IFS=$old_ifs
  for label in "$@"; do
    [ ${#label} -le 63 ] || return 1
  done
}

address=${SNATTER_ADDRESS:-}

case $address in
  "")
    site=:80
    https=none
    ;;
  \[*\] | *:*)
    # IPv6, which a site address writes in brackets.
    bare=${address#\[}
    bare=${bare%\]}
    is_ipv6 "$bare" || refuse "$address"
    site="[$bare]"
    https=ip
    ;;
  *[!0-9.]*)
    is_host_name "$address" || refuse "$address"
    site=$address
    https=name
    ;;
  *)
    is_ipv4 "$address" || refuse "$address"
    site=$address
    https=ip
    ;;
esac

export SNATTER_SITE="$site" SNATTER_HTTPS="$https"
echo "Serving Snatter at ${address:-any address, over plain HTTP} ($https)"
exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
