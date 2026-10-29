import type { Metadata } from "next";
import { Suspense } from "react";

import { PageHeader } from "@/components/shell/page-header";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { parseTxFilters, txApiQuery, txFiltersQuery } from "@/lib/tx-filters";

import { TransactionFilters } from "@/components/ledger/transaction-filters";
import { QuickActions } from "@/components/ledger/quick-actions";
import { TransactionList } from "@/components/ledger/transaction-list";

export const metadata: Metadata = { title: "Transactions" };

export default function TransactionsPage({ searchParams }: PageProps<"/transactions">) {
  return (
    <>
      <PageHeader title="Transactions" description="Everything that moved money in or out of your wallets." />
      <Suspense fallback={<ListSkeleton />}>
        <Transactions searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Transactions({ searchParams }: { searchParams: PageProps<"/transactions">["searchParams"] }) {
  const filters = parseTxFilters(await searchParams);
  const client = await api();
  const [wallets, firstPage] = await Promise.all([
    get(client.GET("/api/v1/wallets")),
    get(client.GET("/api/v1/transactions", { params: { query: { ...txApiQuery(filters), limit: 25 } } })),
  ]);
  const query = txFiltersQuery(filters);

  return (
    <div className="grid gap-4">
      <TransactionFilters key={filters.reference ?? ""} wallets={wallets} filters={filters} />
      <Card className="gap-0 px-2 py-2">
        {/* Keyed by the filters: new filters start a fresh list (and cursor) from the server's first page. */}
        <TransactionList
          key={query}
          query={query}
          filtered={query !== ""}
          firstPage={firstPage}
          emptyAction={<QuickActions />}
        />
      </Card>
    </div>
  );
}

function ListSkeleton() {
  return (
    <div className="grid gap-4" aria-busy aria-label="Loading transactions">
      <Skeleton className="h-[74px] rounded-xl" />
      <Skeleton className="h-[520px] rounded-xl" />
    </div>
  );
}
