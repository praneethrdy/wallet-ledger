import { Undo2Icon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { TransactionDetailSkeleton, TransactionDetailView } from "@/components/ledger/transaction-detail";
import { Button } from "@/components/ui/button";
import { api, get, type Schemas } from "@/lib/api/client";
import { requireUser } from "@/lib/auth/current-user";
import { ApiError } from "@/lib/errors";
import { isUuid } from "@/lib/format";

export const metadata: Metadata = { title: "Transaction · Support" };

export default function SupportTransactionPage({ params }: PageProps<"/support/transactions/[id]">) {
  return (
    <Suspense fallback={<TransactionDetailSkeleton />}>
      <Transaction params={params} />
    </Suspense>
  );
}

async function Transaction({ params }: { params: PageProps<"/support/transactions/[id]">["params"] }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();

  const user = await requireUser();
  let tx: Schemas["TransactionDetail"];
  try {
    tx = await get((await api()).GET("/api/v1/support/transactions/{id}", { params: { path: { id } } }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  // Only an original that hasn't been reversed yet can be reversed (the backend re-checks).
  const reversible = user.role === "ADMIN" && !tx.reversedByTransactionId && tx.type !== "REVERSAL";
  return (
    <TransactionDetailView
      tx={tx}
      basePath="/support/transactions"
      staff
      actions={
        reversible && (
          <Button variant="outline" asChild>
            <Link href={`/admin/transactions/${tx.id}/reverse`}>
              <Undo2Icon /> Reverse…
            </Link>
          </Button>
        )
      }
    />
  );
}
