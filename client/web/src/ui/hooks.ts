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
