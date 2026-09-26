import { useEffect, useState } from "react";

/**
 * A layout preference of this browser, such as a collapsed panel. Kept in
 * localStorage as a convenience only: when storage is unavailable the
 * preference simply resets.
 */
export function usePreference(key: string, initial: boolean): [boolean, (value: boolean) => void] {
  const storageKey = `snatter:pref:${key}`;
  const [value, setValue] = useState(() => {
    try {
      const stored = localStorage.getItem(storageKey);
      return stored === null ? initial : stored === "true";
    } catch {
      return initial;
    }
  });
  const set = (next: boolean) => {
    setValue(next);
    try {
      localStorage.setItem(storageKey, String(next));
    } catch {
      // Not remembered; fine for a preference.
    }
  };
  return [value, set];
}

/** Whether the page is visible and focused, so the member is looking at what it shows. */
export function useAttention(): boolean {
  const [attentive, setAttentive] = useState(isAttentive);
  useEffect(() => {
    const update = () => setAttentive(isAttentive());
    window.addEventListener("focus", update);
    window.addEventListener("blur", update);
    document.addEventListener("visibilitychange", update);
    return () => {
      window.removeEventListener("focus", update);
      window.removeEventListener("blur", update);
      document.removeEventListener("visibilitychange", update);
    };
  }, []);
  return attentive;
}

function isAttentive(): boolean {
  return document.visibilityState === "visible" && document.hasFocus();
}

/** The current time, refreshed every {@code intervalMs} while it is not null. */
export function useNow(intervalMs: number | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (intervalMs === null) {
      return;
    }
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
