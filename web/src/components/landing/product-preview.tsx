import { CheckIcon } from "lucide-react";

import { LogoMark } from "@/components/brand/logo";
import { Sparkline, type BalancePoint } from "@/components/charts/sparkline";
import { TransactionRow } from "@/components/ledger/transaction-row";
import type { Schemas } from "@/lib/api/client";
import { formatMoney } from "@/lib/money";

/**
 * A "live-looking" product shot built from the app's real components, with sample data, inside a
 * browser-window frame. Decorative: inert (not focusable, hidden from assistive tech), with a
 * one-line description for screen readers instead.
 */
export function ProductPreview() {
  return (
    <div className="relative" role="img" aria-label="Preview of the Wallet Ledger app with sample data">
      <div className="absolute -inset-x-10 -inset-y-12 -z-10 opacity-80 glow" aria-hidden />
      {/* inert: the real components inside include links; this is a picture, not something to tab into. */}
      <div className="overflow-hidden rounded-2xl border border-border bg-card shadow-[var(--elev-lg)]" inert>
        {/* window chrome */}
        <div className="flex items-center gap-2 border-b border-border bg-muted/40 px-4 py-2.5">
          <span className="size-2.5 rounded-full bg-line-strong" />
          <span className="size-2.5 rounded-full bg-line-strong" />
          <span className="size-2.5 rounded-full bg-line-strong" />
          <span className="mx-auto rounded-md border border-border bg-background px-3 py-0.5 text-[11px] text-muted-foreground">
            wallet-ledger · dashboard
          </span>
        </div>

        <div className="grid gap-3 p-4 sm:p-5 [&>*]:min-w-0">
          <div className="grid items-end gap-4 surface p-5 sm:grid-cols-[auto_1fr] [&>*]:min-w-0">
            <div>
              <div className="flex items-center gap-2">
                <LogoMark className="size-5" />
                <span className="eyebrow">Total balance</span>
              </div>
              <div className="mt-2 num text-3xl font-medium tracking-tight">{formatMoney(8_635_000, "INR")}</div>
              <p className="mt-1 text-xs text-credit-text">+{formatMoney(1_840_000, "INR")} this month</p>
            </div>
            <Sparkline data={SERIES} currency="INR" height={64} />
          </div>
          <div className="surface px-2 py-2">
            {TRANSACTIONS.map((tx) => (
              <TransactionRow key={tx.id} tx={tx} href="/login" />
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 surface px-4 py-3 text-xs">
            <span className="grid size-6 place-items-center rounded-full bg-[color-mix(in_oklab,var(--credit)_14%,transparent)] text-credit-text">
              <CheckIcon className="size-3.5" />
            </span>
            <span className="text-muted-foreground">Nightly reconciliation · debits = credits</span>
            <span className="ml-auto font-medium">Passed · no findings</span>
          </div>
        </div>
      </div>
    </div>
  );
}

// ---- sample data -----------------------------------------------------------------------

const SERIES: BalancePoint[] = Array.from({ length: 30 }, (_, i) => ({
  date: `2026-10-${String(i + 1).padStart(2, "0")}`,
  // A gentle upward month with a couple of spends: deterministic, so server and client agree.
  balance: 6_800_000 + i * 62_000 + (i % 7 === 3 ? -240_000 : 0) + (i > 20 ? 150_000 : 0),
}));

function tx(over: Partial<Schemas["TransactionSummary"]>): Schemas["TransactionSummary"] {
  return {
    id: over.reference ?? "x",
    reference: "TXN-20261029-0000",
    type: "TRANSFER",
    description: null,
    createdAt: "2026-10-29T06:30:00Z",
    currency: "INR",
    amount: 0,
    netAmount: 0,
    reversalOfTransactionId: null,
    reversedByTransactionId: null,
    ...over,
  };
}

const TRANSACTIONS = [
  tx({ reference: "TXN-20261029-K7Q2M9", description: "Concert tickets", amount: 3_200_00, netAmount: 3_200_00 }),
  tx({ reference: "TXN-20261028-1P02F1", description: "Dinner split", amount: 1_850_00, netAmount: -1_850_00 }),
  tx({
    reference: "TXN-20261027-PP3M64",
    description: "Monthly savings",
    amount: 20_000_00,
    netAmount: 0,
    createdAt: "2026-10-27T04:00:00Z",
  }),
];
