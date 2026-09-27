/** Whether two plain values hold the same data, such as a form and the form as it opened. */
export function sameData(a: unknown, b: unknown): boolean {
  if (a === b) {
    return true;
  }
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((value, i) => sameData(value, b[i]));
  }
  if (isRecord(a) && isRecord(b)) {
    const keys = Object.keys(a);
    return keys.length === Object.keys(b).length && keys.every((key) => key in b && sameData(a[key], b[key]));
  }
  return false;
}

/** Whether two lists hold the same items, in any order. */
export function sameMembers<T>(a: readonly T[], b: readonly T[]): boolean {
  const set = new Set(b);
  return a.length === set.size && a.every((item) => set.has(item));
}

/** A whole number written in plain digits, or null for anything else ("", "1.5", "1e3", "-2"). */
export function wholeNumber(text: string): number | null {
  const trimmed = text.trim();
  return /^\d+$/.test(trimmed) ? Number(trimmed) : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
