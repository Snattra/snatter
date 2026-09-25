/**
 * What the app needs from wherever it runs. The browser implementation is
 * below; the desktop shell will provide its own (tokens in the OS keychain
 * through Electron's safeStorage, global push-to-talk, tray, ...). Everything
 * is asynchronous because the desktop side answers over IPC.
 */
export interface Platform {
  readonly kind: "web" | "desktop";
  /** Small secrets such as session tokens, kept across restarts. */
  readonly secrets: SecretStore;
}

export interface SecretStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  delete(key: string): Promise<void>;
}

const PREFIX = "snatter:";

/**
 * In the browser, secrets live in localStorage. That is only as safe as the
 * page is free of injected scripts, which the Content-Security-Policy and
 * never rendering user content as HTML are there to ensure.
 */
export const webPlatform: Platform = {
  kind: "web",
  secrets: {
    async get(key) {
      return localStorage.getItem(PREFIX + key);
    },
    async set(key, value) {
      localStorage.setItem(PREFIX + key, value);
    },
    async delete(key) {
      localStorage.removeItem(PREFIX + key);
    },
  },
};

export const platform: Platform = webPlatform;
