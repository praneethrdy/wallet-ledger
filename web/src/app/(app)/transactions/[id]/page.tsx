import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { TransactionDetailSkeleton, TransactionDetailView } from "@/components/ledger/transaction-detail";
import { api, get, type Schemas } from "@/lib/api/client";
import { ApiError } from "@/lib/errors";
import { isUuid } from "@/lib/format";

export const metadata: Metadata = { title: "Transaction" };

export default function TransactionPage({ params }: PageProps<"/transactions/[id]">) {
  return (
    <Suspense fallback={<TransactionDetailSkeleton />}>
      <Transaction params={params} />
    </Suspense>
  );
}

async function Transaction({ params }: { params: PageProps<"/transactions/[id]">["params"] }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();

  let tx: Schemas["TransactionDetail"];
  try {
    tx = await get((await api()).GET("/api/v1/transactions/{id}", { params: { path: { id } } }));
  } catch (err) {
    // 404 = missing OR not touching any of your wallets (the backend never says which).
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }
  return <TransactionDetailView tx={tx} basePath="/transactions" />;
}
