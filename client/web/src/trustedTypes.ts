// The page is served with `require-trusted-types-for 'script'`: the browser
// refuses plain strings wherever they would become script, including worker
// URLs. This is the one policy the CSP allows (`trusted-types snatter`), and it
// only vouches for scripts from the app's own origin.

interface TrustedTypePolicy {
  createScriptURL(input: string): unknown;
}

interface TrustedTypePolicyFactory {
  createPolicy(name: string, rules: { createScriptURL(input: string): string }): TrustedTypePolicy;
}

// Not in TypeScript's DOM types yet; absent in browsers without Trusted Types.
declare const trustedTypes: TrustedTypePolicyFactory | undefined;

let policy: TrustedTypePolicy | null = null;

/** A script URL the browser accepts under Trusted Types; only the app's own scripts qualify. */
export function trustedScriptUrl(url: string): string {
  if (typeof trustedTypes === "undefined") {
    return url;
  }
  policy ??= trustedTypes.createPolicy("snatter", {
    createScriptURL(input) {
      if (new URL(input, location.href).origin !== location.origin) {
        throw new TypeError(`Refusing script from another origin: ${input}`);
      }
      return input;
    },
  });
  // A TrustedScriptURL; typed as string because that is what the DOM typings accept.
  return policy.createScriptURL(url) as string;
}
