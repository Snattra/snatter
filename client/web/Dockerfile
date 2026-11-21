# Builds the Snatter web app and serves it with Caddy, which also passes the
# API and the gateway on to a Snatter server, so the app runs same-origin, and
# serves it over HTTPS when given an address (SNATTER_ADDRESS, see
# caddy/start.sh).
#
#   docker build -f client/web/Dockerfile -t snatter-web .
#   docker run -p 8080:80 -e SNATTER_SERVER_URL=http://my-server:8080 snatter-web
#
# The build context is the repository root, since the app is typed from the
# contract in protocol/openapi.

# --- Build stage --------------------------------------------------------------
# Runs on the builder's own architecture whatever the image is for: the app
# is the same everywhere, so only the runtime stage is built per architecture.
FROM --platform=$BUILDPLATFORM docker.io/library/node:24-alpine AS build
WORKDIR /build/client

# Install dependencies first so they are cached independently of source changes.
COPY client/package.json client/package-lock.json ./
COPY client/web/package.json web/
RUN npm ci

COPY protocol/openapi ../protocol/openapi
COPY client/web web
RUN npm run build

# --- Runtime stage ------------------------------------------------------------
FROM docker.io/library/caddy:2.11.9-alpine

# The Snatter server to pass /api to.
ENV SNATTER_SERVER_URL=http://server:8080
# The address members reach this machine at, for HTTPS; unset, plain HTTP.
ENV SNATTER_ADDRESS=""

COPY client/web/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --chmod=755 client/web/caddy/start.sh /usr/local/bin/snatter-web
COPY --from=build /build/client/web/dist /srv

# HTTP, and HTTPS over TCP and, for HTTP/3, UDP. Certificates are kept in /data.
EXPOSE 80 443 443/udp
CMD ["snatter-web"]
