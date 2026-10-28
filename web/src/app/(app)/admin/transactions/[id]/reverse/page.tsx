import { ChevronRightIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { TAccount } from "@/components/ledger/t-account";
import { FadeIn } from "@/components/motion/fade-in";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get, type Schemas } from "@/lib/api/client";
import { ApiError } from "@/lib/errors";
import { formatDateTimeFull, isUuid } from "@/lib/format";

import { ReverseForm } from "./reverse-form";

export const metadata: Metadata = { title: "Reverse transaction" };

export default function ReversePage({ params }: PageProps<"/admin/transactions/[id]/reverse">) {
  return (
    <Suspense
      fallback={
        <div className="max-w-3xl space-y-6">
          <Skeleton className="h-20 rounded-xl" />
          <Skeleton className="h-56 rounded-xl" />
        </div>
      }
    >
      <Reverse params={params} />
    </Suspense>
  );
}

async function Reverse({ params }: { params: PageProps<"/admin/transactions/[id]/reverse">["params"] }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();

  let tx: Schemas["TransactionDetail"];
  try {
    tx = await get((await api()).GET("/api/v1/support/transactions/{id}", { params: { path: { id } } }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  return (
    <div className="max-w-3xl">
      <FadeIn className="mb-8">
        <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-sm text-muted-foreground">
          <Link href="/support/transactions" className="hover:text-foreground">
            Transactions
          </Link>
          <ChevronRightIcon className="size-3.5" aria-hidden />
          <Link href={`/support/transactions/${tx.id}`} className="truncate num hover:text-foreground">
            {tx.reference}
          </Link>
          <ChevronRightIcon className="size-3.5" aria-hidden />
          <span className="text-foreground">Reverse</span>
        </nav>
        <h1 className="text-2xl font-semibold tracking-tight">Reverse {tx.reference}</h1>
        <p className="mt-1.5 max-w-2xl text-sm text-muted-foreground">
          {tx.description ? `“${tx.description}” · ` : ""}
          {formatDateTimeFull(tx.createdAt)}. A reversal posts a new transaction with these legs flipped; the original
          stays exactly as it is.
        </p>
      </FadeIn>

      <div className="grid gap-6">
        <section aria-label="Original transaction">
          <p className="mb-2 eyebrow">Original legs</p>
          <TAccount legs={tx.legs} showIds />
        </section>
        <ReverseForm tx={tx} />
      </div>
    </div>
  );
}
