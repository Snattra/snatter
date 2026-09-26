const clock = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" });
const date = new Intl.DateTimeFormat(undefined, { year: "numeric", month: "2-digit", day: "2-digit" });

/** "18:31", in the reader's locale. */
export function shortTime(at: Date): string {
  return clock.format(at);
}

/** "Today at 18:31", "Yesterday at 18:31", or the date and time further back. */
export function longTime(at: Date, now: Date): string {
  const days = dayNumber(now) - dayNumber(at);
  if (days === 0) {
    return `Today at ${shortTime(at)}`;
  }
  if (days === 1) {
    return `Yesterday at ${shortTime(at)}`;
  }
  return `${date.format(at)} ${shortTime(at)}`;
}

/** Like {@link longTime}, but only the time for today. */
export function sinceTime(at: Date, now: Date): string {
  return dayNumber(now) === dayNumber(at) ? shortTime(at) : longTime(at, now);
}

export function sameDay(a: Date, b: Date): boolean {
  return dayNumber(a) === dayNumber(b);
}

/** Days since the epoch in local time, so "today" follows the reader's clock. */
function dayNumber(at: Date): number {
  return Math.floor((at.getTime() - at.getTimezoneOffset() * 60_000) / 86_400_000);
}
