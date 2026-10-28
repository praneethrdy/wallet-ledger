"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { ReceiptTextIcon, SearchXIcon } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";

import { TransactionRow } from "@/components/ledger/transaction-row";
import { EmptyState, ErrorState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { Schemas } from "@/lib/api/client";
import { bffGet } from "@/lib/bff-client";
import { formatDate, istDay } from "@/lib/format";

type Page = Schemas["PageResponseTransactionSummary"];
type Summary = Schemas["TransactionSummary"];

/** Newest first, grouped by day (IST). Scrolling near the end loads the next page by cursor. */
export function TransactionList({ query, filtered, firstPage }: { query: string; filtered: boolean; firstPage: Page }) {
  const list = useInfiniteQuery({
    queryKey: ["transactions", query],
    queryFn: ({ pageParam }) => {
      const q = new URLSearchParams(query);
      if (pageParam) q.set("cursor", pageParam);
      return bffGet<Page>(`/bff/transactions?${q}`);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    initialData: { pages: [firstPage], pageParams: [null] },
    staleTime: Infinity, // the server render is fresh; later pages are loaded on demand
  });

  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = list;
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) void fetchNextPage();
      },
      { rootMargin: "320px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const items = list.data.pages.flatMap((p) => p.items);
  if (items.length === 0) {
    return filtered ? (
      <EmptyState
        icon={<SearchXIcon />}
        title="No transactions match these filters"
        description="Try a wider date range or another wallet. References must match exactly."
        action={
          <Button variant="outline" asChild>
            <Link href="/transactions">Clear filters</Link>
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

  const days = groupByDay(items);
  return (
    <div>
      {days.map(([day, txs]) => (
        <section key={day} aria-label={formatDate(txs[0].createdAt)}>
          <h2 className="px-3 pt-4 pb-1.5 eyebrow first:pt-2">{formatDate(txs[0].createdAt)}</h2>
          <div className="space-y-0.5">
            {txs.map((tx) => (
              <TransactionRow key={tx.id} tx={tx} href={`/transactions/${tx.id}`} />
            ))}
          </div>
        </section>
      ))}

      <div ref={sentinel} />
      {isFetchingNextPage && (
        <div className="space-y-2 p-3" aria-busy aria-label="Loading more">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      )}
      {list.isFetchNextPageError && (
        <ErrorState title="Couldn’t load more" description={list.error?.message} className="py-8" />
      )}
      {hasNextPage && !isFetchingNextPage && (
        <div className="flex justify-center p-3">
          <Button variant="ghost" size="sm" onClick={() => void fetchNextPage()}>
            Load more
          </Button>
        </div>
      )}
      {!hasNextPage && items.length > 10 && (
        <p className="py-4 text-center text-xs text-muted-foreground">That’s everything.</p>
      )}
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
