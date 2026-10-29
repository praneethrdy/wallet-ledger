import type { Schemas } from "@/lib/api/client";

type DailyFlow = Schemas["DailyFlow"];

/**
 * Closing balance for each day of the cash-flow window, worked backwards from today's total.
 * Exact, not an estimate: the backend nets every ledger entry on the user's wallets per
 * transaction (moves between their own wallets net to zero), so a day's inflow − outflow is
 * precisely how much the total changed that day. `flows` is oldest first and ends today.
 */
export function balanceSeries(current: number, flows: DailyFlow[]): { date: string; balance: number }[] {
  const series = new Array<{ date: string; balance: number }>(flows.length);
  let balance = current;
  for (let i = flows.length - 1; i >= 0; i--) {
    series[i] = { date: flows[i].date, balance };
    balance -= flows[i].inflow - flows[i].outflow;
  }
  return series;
}

/** Change over the window: today's close minus the close on the day before it started. */
export function windowChange(flows: DailyFlow[]): number {
  return flows.reduce((sum, d) => sum + d.inflow - d.outflow, 0);
}

/**
 * Each wallet's share of the total in one currency, largest first, for the allocation bar.
 * Wallets with nothing in them (or overdrawn system edge cases) don't take up space.
 */
export function walletShares<W extends { id: string; currency: string; balance: number }>(
  wallets: W[],
  currency: string,
): { wallet: W; share: number }[] {
  const held = wallets.filter((w) => w.currency === currency && w.balance > 0);
  const total = held.reduce((sum, w) => sum + w.balance, 0);
  if (total === 0) return [];
  return held.map((wallet) => ({ wallet, share: wallet.balance / total })).sort((a, b) => b.share - a.share);
}
