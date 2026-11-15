import { readFileSync } from "node:fs";
import react from "@vitejs/plugin-react";
import type { Plugin } from "vite";
import { defineConfig } from "vitest/config";

// In development the app is served by Vite and talks to a Snatter server
// through this proxy, so it runs same-origin just as it does when a server
// installation serves the built app. SNATTER_SERVER picks the server.
const server = process.env.SNATTER_SERVER ?? "http://localhost:8080";
const proxy = { "/api": { target: server, ws: true } };

// The Content-Security-Policy the app runs under: a server installation's,
// from its nginx config, or the hosted app's, from the _headers file the
// multi-server build ships with. `vite preview` serves the build with it, so
// that anything the policy blocks shows up locally. (The dev server cannot
// use it: its hot reload relies on inline script.)
function cspIn(file: string, pattern: RegExp): string {
  const text = readFileSync(new URL(file, import.meta.url), "utf8");
  const csp = pattern.exec(text)?.[1];
  if (csp === undefined) {
    throw new Error(`No Content-Security-Policy found in ${file}`);
  }
  return csp;
}
const installationCsp = cspIn("nginx/default.conf.template", /add_header Content-Security-Policy "([^"]+)"/);
const hostedHeaders = readFileSync(new URL("hosted/_headers", import.meta.url), "utf8");
const hostedCsp = cspIn("hosted/_headers", /^\s*Content-Security-Policy: (.+)$/m);

/**
 * What the hosted, multi-server build adds, for static hosts: the _headers
 * file, the policy in the page as well, for hosts that cannot set headers
 * (less frame-ancestors, which a page cannot set for itself), and the
 * version, which deploys check. SNATTER_VERSION gives it; "dev" otherwise.
 */
function hosted(): Plugin {
  const pagePolicy = hostedCsp
    .split(";")
    .map((directive) => directive.trim())
    .filter((directive) => directive !== "" && !directive.startsWith("frame-ancestors"))
    .join("; ");
  const version = process.env.SNATTER_VERSION ?? "dev";
  return {
    name: "snatter-hosted",
    apply: "build",
    // Right after the charset, which must stay near the top, and before any script.
    transformIndexHtml(html) {
      const charset = '<meta charset="UTF-8" />';
      if (!html.includes(charset)) {
        throw new Error(`index.html has no ${charset} to put the Content-Security-Policy after`);
      }
      return html.replace(
        charset,
        `${charset}\n    <meta http-equiv="Content-Security-Policy" content="${pagePolicy}" />` +
          `\n    <meta name="snatter-version" content="${version}" />`,
      );
    },
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "_headers", source: hostedHeaders });
    },
  };
}

export default defineConfig(({ mode }) => {
  const multi = mode === "multi";
  return {
    plugins: multi ? [react(), hosted()] : [react()],
    server: { proxy },
    preview: { proxy, headers: { "Content-Security-Policy": multi ? hostedCsp : installationCsp } },
    test: {
      environment: "node",
    },
  };
});
