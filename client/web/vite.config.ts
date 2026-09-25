import { readFileSync } from "node:fs";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// In development the app is served by Vite and talks to a Snatter server
// through this proxy, so it runs same-origin just as it does when a server
// installation serves the built app. SNATTER_SERVER picks the server.
const server = process.env.SNATTER_SERVER ?? "http://localhost:8080";
const proxy = { "/api": { target: server, ws: true } };

// `vite preview` serves the production build with the production
// Content-Security-Policy, taken from the nginx config, so that anything the
// policy blocks shows up locally. (The dev server cannot use it: its hot
// reload relies on inline script.)
const nginxConfig = readFileSync(new URL("nginx/default.conf.template", import.meta.url), "utf8");
const csp = /add_header Content-Security-Policy "([^"]+)"/.exec(nginxConfig)?.[1];
if (csp === undefined) {
  throw new Error("No Content-Security-Policy found in nginx/default.conf.template");
}

export default defineConfig({
  plugins: [react()],
  server: { proxy },
  preview: { proxy, headers: { "Content-Security-Policy": csp } },
  test: {
    environment: "node",
  },
});
