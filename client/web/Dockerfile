# Builds the Snatter web app and serves it with nginx, which also forwards the
# API and the gateway to a Snatter server so the app runs same-origin.
#
#   docker build -f client/web/Dockerfile -t snatter-web .
#   docker run -p 8080:8080 -e SNATTER_SERVER_URL=http://my-server:8080 snatter-web
#
# The build context is the repository root, since the app is typed from the
# contract in protocol/openapi.

# --- Build stage --------------------------------------------------------------
FROM docker.io/library/node:24-alpine AS build
WORKDIR /build/client

# Install dependencies first so they are cached independently of source changes.
COPY client/package.json client/package-lock.json ./
COPY client/web/package.json web/
RUN npm ci

COPY protocol/openapi ../protocol/openapi
COPY client/web web
RUN npm run build

# --- Runtime stage ------------------------------------------------------------
FROM docker.io/nginxinc/nginx-unprivileged:stable-alpine

# The Snatter server to forward /api to.
ENV SNATTER_SERVER_URL=http://server:8080
# Makes the entrypoint export the container's DNS servers as
# NGINX_LOCAL_RESOLVERS, so the server's address is looked up again when it
# changes rather than only at startup.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

COPY client/web/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/client/web/dist /usr/share/nginx/html

EXPOSE 8080
