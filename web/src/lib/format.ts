/**
 * Dates are formatted in the platform's reporting zone (Asia/Kolkata), the same zone the backend
 * uses for "today" and daily buckets, so server- and client-rendered dates always agree.
 */
const ZONE = "Asia/Kolkata";
const LOCALE = "en-IN";

const dateTime = new Intl.DateTimeFormat(LOCALE, {
  timeZone: ZONE,
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});
const dateTimeWithYear = new Intl.DateTimeFormat(LOCALE, {
  timeZone: ZONE,
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});
const dateOnly = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONE, day: "numeric", month: "short", year: "numeric" });
const dayMonth = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONE, day: "numeric", month: "short" });

/** "28 Oct, 10:42" */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** "28 Oct 2026, 10:42" */
export function formatDateTimeFull(iso: string): string {
  return dateTimeWithYear.format(new Date(iso));
}

/** "28 Oct 2026" */
export function formatDate(iso: string): string {
  return dateOnly.format(new Date(iso));
}

/** "28 Oct" for a calendar date like "2026-10-28" (no time zone shift). */
export function formatDay(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  return dayMonth.format(new Date(Date.UTC(y, m - 1, d, 12)));
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID.test(value);
}

/** "01a181c2…01dc" for showing long IDs compactly (the full ID stays copyable). */
export function shortId(id: string): string {
  return id.length > 14 ? `${id.slice(0, 8)}…${id.slice(-4)}` : id;
}

const isoDay = new Intl.DateTimeFormat("en-CA", { timeZone: ZONE, year: "numeric", month: "2-digit", day: "2-digit" });

/** The IST calendar day of an instant, as "2026-10-28" (for grouping by day). */
export function istDay(iso: string): string {
  return isoDay.format(new Date(iso));
}
