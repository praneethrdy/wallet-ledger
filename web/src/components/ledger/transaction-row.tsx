import { ArrowDownLeftIcon, ArrowLeftRightIcon, ArrowUpRightIcon, Undo2Icon } from "lucide-react";
import Link from "next/link";

import { cn } from "cn";

import { Amount } from "@/components/ledger/amount";
import { StatusBadge } from "@/components/ledger/status-badge";
import type { Schemas } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";

type Summary = Schemas["TransactionSummary"];

export const TX_TYPE_LABEL: Record<string, string> = {
  DEPOSIT: "Deposit",
  TRANSFER: "Transfer",
  REVERSAL: "Reversal",
  REFUND: "Refund",
  FX_CONVERSION: "Currency conversion",
};

/**
 * One transaction as the viewer experiences it: in, out, or between their own wallets (net 0).
 * Links to the full detail with every ledger leg.
 */
export function TransactionRow({ tx, href }: { tx: Summary; href: string }) {
  const net = tx.netAmount ?? 0;
  const Icon =
    tx.type === "REVERSAL" ? Undo2Icon : net > 0 ? ArrowDownLeftIcon : net < 0 ? ArrowUpRightIcon : ArrowLeftRightIcon;

  return (
    <Link
      href={href}
      className="group flex items-center gap-3.5 rounded-lg px-3 py-2.5 transition-colors duration-150 hover:bg-muted/70 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
    >
      <span
        className={cn(
          "grid size-9 shrink-0 place-items-center rounded-full border border-border bg-card text-muted-foreground",
          net > 0 && "border-transparent bg-[color-mix(in_oklab,var(--credit)_12%,transparent)] text-credit-text",
        )}
      >
        <Icon className="size-4" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <span className="truncate text-sm font-medium">{tx.description || TX_TYPE_LABEL[tx.type] || tx.type}</span>
          {tx.reversedByTransactionId && <StatusBadge value="REVERSED" className="h-5 text-[11px]" />}
        </span>
        <span className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
          <span className="whitespace-nowrap">{formatDateTime(tx.createdAt)}</span>
          <span aria-hidden>·</span>
          <span className="truncate num">{tx.reference}</span>
        </span>
      </span>
      <span className="text-right">
        {tx.netAmount === null ? (
          // Staff view with no wallet in focus: there's no "in" or "out", just the size.
          <Amount minor={tx.amount} currency={tx.currency} tone="plain" className="text-sm font-medium" />
        ) : net === 0 ? (
          <span className="text-sm text-muted-foreground">Between your wallets</span>
        ) : (
          <Amount minor={net} currency={tx.currency} tone="signed" className="text-sm font-medium" />
        )}
      </span>
    </Link>
  );
}
