"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef } from "react";

import { Amount } from "@/components/ledger/amount";
import { StatusBadge } from "@/components/ledger/status-badge";
import { EmptyState, ErrorState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Schemas } from "@/lib/api/client";
import { bffGet } from "@/lib/bff-client";
import { formatDateTime } from "@/lib/format";
import { formatMoney } from "@/lib/money";

type Page = Schemas["PageResponseEntryResponse"];

const TYPE_LABEL: Record<string, string> = { DEPOSIT: "Deposit", TRANSFER: "Transfer", REVERSAL: "Reversal" };

/**
 * Newest-first ledger entries with the running balance after each one. The first page comes
 * from the server; scrolling near the end (or the button, for keyboard users) loads the next
 * page by cursor, which stays stable even if new entries arrive meanwhile.
 */
export function EntriesTable({ walletId, firstPage }: { walletId: string; firstPage: Page }) {
  const query = useInfiniteQuery({
    queryKey: ["entries", walletId],
    queryFn: ({ pageParam }) =>
      bffGet<Page>(`/bff/wallets/${walletId}/entries${pageParam ? `?cursor=${encodeURIComponent(pageParam)}` : ""}`),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    initialData: { pages: [firstPage], pageParams: [null] },
    staleTime: Infinity, // the server render is fresh; later pages are loaded on demand
    gcTime: 0, // ...and on the next visit the server renders a fresh first page, so drop this cache on unmount
  });

  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query;
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

  const entries = query.data.pages.flatMap((p) => p.items);
  if (entries.length === 0) {
    return <EmptyState title="No entries yet" description="Money in and out of this wallet will appear here." />;
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead>Date</TableHead>
            <TableHead>Description</TableHead>
            <TableHead className="hidden md:table-cell">Reference</TableHead>
            <TableHead numeric>Amount</TableHead>
            <TableHead numeric className="hidden sm:table-cell">
              Balance
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((e, i) => (
            <TableRow key={`${e.transactionId}-${i}`}>
              <TableCell className="text-muted-foreground">{formatDateTime(e.createdAt)}</TableCell>
              <TableCell>
                <Link
                  href={`/transactions/${e.transactionId}`}
                  className="flex items-center gap-2.5 rounded-sm hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                >
                  <StatusBadge value={e.type} />
                  <span className="max-w-[18rem] truncate">{e.description || TYPE_LABEL[e.type] || e.type}</span>
                </Link>
              </TableCell>
              <TableCell className="hidden num text-xs text-muted-foreground md:table-cell">{e.reference}</TableCell>
              <TableCell numeric>
                <Amount minor={e.amount} currency={e.currency} tone={e.direction === "CREDIT" ? "credit" : "debit"} />
              </TableCell>
              <TableCell numeric className="hidden text-muted-foreground sm:table-cell">
                {formatMoney(e.runningBalance, e.currency)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <div ref={sentinel} className="flex flex-col items-center gap-3 border-t border-border px-5 py-4">
        {query.isFetchingNextPage && (
          <div className="w-full space-y-2" aria-label="Loading more entries">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        )}
        {query.isFetchNextPageError && (
          <ErrorState
            title="Couldn’t load more entries"
            description={(query.error as Error | null)?.message}
            correlationId={(query.error as (Error & { correlationId?: string }) | null)?.correlationId}
            className="py-6"
          />
        )}
        {hasNextPage && !isFetchingNextPage ? (
          <Button variant="outline" size="sm" onClick={() => void fetchNextPage()}>
            Load more
          </Button>
        ) : (
          !hasNextPage && (
            <span className="text-xs text-muted-foreground">Start of history · {entries.length} entries</span>
          )
        )}
      </div>
    </>
  );
}
