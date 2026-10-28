import type { Metadata } from "next";
import { Suspense } from "react";

import { TransactionFilters } from "@/components/ledger/transaction-filters";
import { TransactionList } from "@/components/ledger/transaction-list";
import { PageHeader } from "@/components/shell/page-header";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { parseTxFilters, txApiQuery, txFiltersQuery } from "@/lib/tx-filters";

export const metadata: Metadata = { title: "Transactions · Support" };

export default function SupportTransactionsPage({ searchParams }: PageProps<"/support/transactions">) {
  return (
    <>
      <PageHeader
        title="Transactions"
        description="Search every transaction by reference, wallet, type and date. Amounts are signed when a wallet is selected."
      />
      <Suspense
        fallback={
          <div className="grid gap-4">
            <Skeleton className="h-[74px] rounded-xl" />
            <Skeleton className="h-[520px] rounded-xl" />
          </div>
        }
      >
        <Transactions searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Transactions({ searchParams }: { searchParams: PageProps<"/support/transactions">["searchParams"] }) {
  const filters = parseTxFilters(await searchParams);
  const firstPage = await get(
    (await api()).GET("/api/v1/support/transactions", { params: { query: { ...txApiQuery(filters), limit: 25 } } }),
  );
  const query = txFiltersQuery(filters);
  return (
    <div className="grid gap-4">
      <TransactionFilters
        key={`${filters.reference ?? ""}|${filters.walletId ?? ""}`}
        filters={filters}
        basePath="/support/transactions"
      />
      <Card className="gap-0 px-2 py-2">
        <TransactionList
          key={query}
          query={query}
          filtered={query !== ""}
          firstPage={firstPage}
          basePath="/support/transactions"
          bffPath="/bff/support/transactions"
        />
      </Card>
    </div>
  );
}
