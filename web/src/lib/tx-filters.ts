import { isUuid } from "@/lib/format";

/** Transaction types a customer can filter by (the backend enum also has REFUND and FX_CONVERSION, unused so far). */
export const TX_TYPES = ["DEPOSIT", "TRANSFER", "REVERSAL"] as const;
export type TxType = (typeof TX_TYPES)[number];

/** Filters as they live in the URL: calendar dates (IST), not instants. */
export type TxFilters = { walletId?: string; type?: TxType; from?: string; to?: string; reference?: string };

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const IST = "+05:30";

/** Reads filters from search params, silently dropping anything malformed (a bad link just shows everything). */
export function parseTxFilters(params: Record<string, string | string[] | undefined>): TxFilters {
  const one = (k: string) => (typeof params[k] === "string" ? (params[k] as string).trim() : undefined);
  const walletId = one("walletId");
  const type = one("type");
  const from = one("from");
  const to = one("to");
  const reference = one("reference");
  return {
    walletId: walletId && isUuid(walletId) ? walletId : undefined,
    type: TX_TYPES.find((t) => t === type),
    from: from && validDate(from) ? from : undefined,
    to: to && validDate(to) ? to : undefined,
    reference: reference ? reference.slice(0, 64) : undefined,
  };
}

/** URL query string for these filters (stable key order, empty values left out). */
export function txFiltersQuery(f: TxFilters): string {
  const q = new URLSearchParams();
  for (const k of ["walletId", "type", "from", "to", "reference"] as const) {
    if (f[k]) q.set(k, f[k]);
  }
  return q.toString();
}

/**
 * Backend query: dates become instants at IST midnight. The backend's `to` is exclusive, so an
 * inclusive "to" date becomes the start of the next day.
 */
export function txApiQuery(f: TxFilters) {
  return {
    walletId: f.walletId,
    type: f.type,
    from: f.from ? `${f.from}T00:00:00${IST}` : undefined,
    to: f.to ? `${nextDay(f.to)}T00:00:00${IST}` : undefined,
    reference: f.reference,
  };
}

function validDate(s: string): boolean {
  if (!DATE.test(s)) return false;
  const [y, m, d] = s.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

function nextDay(s: string): string {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
}
