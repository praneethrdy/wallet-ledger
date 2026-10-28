import { ArrowRightIcon, ChevronRightIcon, Undo2Icon } from "lucide-react";
import Link from "next/link";

import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { TAccount } from "@/components/ledger/t-account";
import { TX_TYPE_LABEL } from "@/components/ledger/transaction-row";
import { FadeIn } from "@/components/motion/fade-in";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import type { Schemas } from "@/lib/api/client";
import { formatDateTimeFull } from "@/lib/format";
import { formatMoney } from "@/lib/money";

/**
 * One transaction: header, reversal links both ways, the T-account and its identifiers.
 * `basePath` is the list it belongs to (customer /transactions or staff /support/transactions);
 * reversal links stay inside that area. `actions` sits top-right (e.g. the admin's Reverse button).
 */
export function TransactionDetailView({
  tx,
  basePath,
  actions,
  staff = false,
}: {
  tx: Schemas["TransactionDetail"];
  basePath: string;
  actions?: React.ReactNode;
  /** Staff see real wallet names, which can repeat across owners: show account IDs too. */
  staff?: boolean;
}) {
  const label = TX_TYPE_LABEL[tx.type] ?? tx.type;
  // Size of the transaction = total debits per currency (equal to credits when balanced).
  const sizes = [...new Set(tx.legs.map((l) => l.currency))].map((currency) => ({
    currency,
    amount: tx.legs.filter((l) => l.currency === currency && l.direction === "DEBIT").reduce((s, l) => s + l.amount, 0),
  }));

  return (
    <div className="max-w-3xl">
      <FadeIn className="mb-8">
        <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-sm text-muted-foreground">
          <Link href={basePath} className="hover:text-foreground">
            Transactions
          </Link>
          <ChevronRightIcon className="size-3.5" aria-hidden />
          <span className="truncate num text-foreground">{tx.reference}</span>
        </nav>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold tracking-tight">{tx.description || label}</h1>
              <StatusBadge value={tx.type} />
            </div>
            <p className="mt-1.5 text-sm text-muted-foreground">{formatDateTimeFull(tx.createdAt)}</p>
          </div>
          <div className="flex flex-col items-end gap-3">
            <p className="num text-3xl font-medium tracking-tight">
              {sizes.map((s) => formatMoney(s.amount, s.currency)).join(" · ")}
            </p>
            {actions}
          </div>
        </div>
      </FadeIn>

      {tx.reversedByTransactionId && (
        <ReversalLink
          title="This transaction was reversed"
          description="A later transaction posted the exact opposite legs, so its net effect is zero."
          href={`${basePath}/${tx.reversedByTransactionId}`}
          cta="View the reversal"
        />
      )}
      {tx.reversalOfTransactionId && (
        <ReversalLink
          title="This is a reversal"
          description="It undoes an earlier transaction by posting the opposite legs. Nothing was edited or deleted."
          href={`${basePath}/${tx.reversalOfTransactionId}`}
          cta="View the original"
        />
      )}

      <div className="grid gap-6">
        <TAccount legs={tx.legs} showIds={staff} />

        <dl className="grid gap-x-6 gap-y-4 surface p-5 text-sm sm:grid-cols-[140px_1fr]">
          <dt className="text-muted-foreground">Reference</dt>
          <dd className="flex items-center gap-1">
            <span className="num">{tx.reference}</span>
            <CopyButton value={tx.reference} label="Copy reference" className="size-6" />
          </dd>
          <dt className="text-muted-foreground">Transaction ID</dt>
          <dd className="flex min-w-0 items-center gap-1">
            <span className="min-w-0 num break-all">{tx.id}</span>
            <CopyButton value={tx.id} label="Copy transaction ID" className="size-6" />
          </dd>
          <dt className="text-muted-foreground">Type</dt>
          <dd>{label}</dd>
          <dt className="text-muted-foreground">Legs</dt>
          <dd>
            {tx.legs.length} ·{" "}
            <span className="text-muted-foreground">
              posted together in one database transaction, so none can exist without the others
            </span>
          </dd>
        </dl>
      </div>
    </div>
  );
}

function ReversalLink({
  title,
  description,
  href,
  cta,
}: {
  title: string;
  description: string;
  href: string;
  cta: string;
}) {
  return (
    <Alert className="mb-6">
      <Undo2Icon />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p>{description}</p>
        <Link
          href={href}
          className="mt-1 inline-flex items-center gap-1 font-medium text-foreground underline-offset-4 hover:underline"
        >
          {cta} <ArrowRightIcon className="size-3.5" />
        </Link>
      </AlertDescription>
    </Alert>
  );
}

export function TransactionDetailSkeleton() {
  return (
    <div className="max-w-3xl space-y-6" aria-busy aria-label="Loading transaction">
      <Skeleton className="h-20 rounded-xl" />
      <Skeleton className="h-56 rounded-xl" />
      <Skeleton className="h-40 rounded-xl" />
    </div>
  );
}
