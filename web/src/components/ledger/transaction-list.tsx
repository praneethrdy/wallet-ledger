"use client";

import { ReceiptTextIcon, SearchXIcon } from "lucide-react";
import Link from "next/link";

import { ListFooter, useCursorList } from "@/components/data/cursor-list";
import { TransactionRow } from "@/components/ledger/transaction-row";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import type { Schemas } from "@/lib/api/client";
import { formatDate, istDay } from "@/lib/format";

type Page = Schemas["PageResponseTransactionSummary"];
type Summary = Schemas["TransactionSummary"];

/**
 * Newest first, grouped by day (IST). Shared by the customer list (/transactions) and the staff
 * search (/support/transactions): `basePath` is both the page to clear filters on and the
 * detail-link prefix; `bffPath` serves the next pages.
 */
export function TransactionList({
  query,
  filtered,
  firstPage,
  basePath = "/transactions",
  bffPath = "/bff/transactions",
}: {
  query: string;
  filtered: boolean;
  firstPage: Page;
  basePath?: string;
  bffPath?: string;
}) {
  const list = useCursorList([bffPath, query], `${bffPath}?${query}`, firstPage);

  if (list.items.length === 0) {
    return filtered ? (
      <EmptyState
        icon={<SearchXIcon />}
        title="No transactions match these filters"
        description="Try a wider date range or another wallet. References must match exactly."
        action={
          <Button variant="outline" asChild>
            <Link href={basePath}>Clear filters</Link>
          </Button>
        }
      />
    ) : (
      <EmptyState
        icon={<ReceiptTextIcon />}
        title="No transactions yet"
        description="Add money or make a transfer and it will show up here."
      />
    );
  }

  return (
    <div>
      {groupByDay(list.items).map(([day, txs]) => (
        <section key={day} aria-label={formatDate(txs[0].createdAt)}>
          <h2 className="px-3 pt-4 pb-1.5 eyebrow first:pt-2">{formatDate(txs[0].createdAt)}</h2>
          <div className="space-y-0.5">
            {txs.map((tx) => (
              <TransactionRow key={tx.id} tx={tx} href={`${basePath}/${tx.id}`} />
            ))}
          </div>
        </section>
      ))}
      <ListFooter list={list} />
    </div>
  );
}

function groupByDay(items: Summary[]): [string, Summary[]][] {
  const days = new Map<string, Summary[]>();
  for (const tx of items) {
    const day = istDay(tx.createdAt);
    days.set(day, [...(days.get(day) ?? []), tx]);
  }
  return [...days];
}
