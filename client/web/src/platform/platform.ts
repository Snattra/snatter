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
  /** Small values that are not secret, such as the servers added, kept across restarts. */
  readonly settings: SettingsStore;
  /** Opens a web address outside the app: a new tab, or the desktop browser. */
  openLink(url: string): Promise<void>;
}

export interface SecretStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  delete(key: string): Promise<void>;
}

/** Like {@link SecretStore}, for values that need no protection. */
export type SettingsStore = SecretStore;

const PREFIX = "snatter:";
const SETTINGS_PREFIX = "snatter:settings:";

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
  // Settings live in localStorage too. Storage can be off, as in some private
  // windows: then nothing is kept, and the app starts afresh next time.
  settings: {
    async get(key) {
      try {
        return localStorage.getItem(SETTINGS_PREFIX + key);
      } catch {
        return null;
      }
    },
    async set(key, value) {
      try {
        localStorage.setItem(SETTINGS_PREFIX + key, value);
      } catch {
        // Not kept.
      }
    },
    async delete(key) {
      try {
        localStorage.removeItem(SETTINGS_PREFIX + key);
      } catch {
        // Nothing was kept.
      }
    },
  },
  async openLink(url) {
    // Without an opener or a referrer, the page learns nothing of the app.
    window.open(url, "_blank", "noopener,noreferrer");
  },
};

export const platform: Platform = webPlatform;
