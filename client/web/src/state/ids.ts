// Message ids are UUID version 7 from one server, which are strictly
// increasing: comparing them orders messages, and the first 48 bits are the
// creation time in milliseconds.

/** Whether message id {@code a} comes after {@code b}; nothing comes before null. */
export function isAfter(a: string, b: string | null): boolean {
  return b === null || a.toLowerCase() > b.toLowerCase();
}

/** The later of two message ids, where null is before everything. */
export function later(a: string | null, b: string | null): string | null {
  if (a === null) {
    return b;
  }
  return b === null || isAfter(a, b) ? a : b;
}

/** When the message with this id was created, in epoch milliseconds. */
export function timeOfId(id: string): number {
  return parseInt(id.replaceAll("-", "").slice(0, 12), 16);
}
