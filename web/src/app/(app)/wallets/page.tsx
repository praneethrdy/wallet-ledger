import { ArrowRightIcon, WalletIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { Amount } from "@/components/ledger/amount";
import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { FadeIn } from "@/components/motion/fade-in";
import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { shortId } from "@/lib/format";

import { NewWalletDialog } from "./new-wallet-dialog";

export const metadata: Metadata = { title: "Wallets" };

export default function WalletsPage() {
  return (
    <>
      <PageHeader
        title="Wallets"
        description="Balances are always computed from the ledger, never stored."
        actions={<NewWalletDialog />}
      />
      <Suspense fallback={<Grid skeleton />}>
        <WalletGrid />
      </Suspense>
    </>
  );
}

async function WalletGrid() {
  const wallets = await get((await api()).GET("/api/v1/wallets"));
  if (wallets.length === 0) {
    return (
      <div className="surface">
        <EmptyState
          icon={<WalletIcon />}
          title="No wallets yet"
          description="Create a wallet to start receiving and sending money."
          action={<NewWalletDialog variant="outline" />}
        />
      </div>
    );
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {wallets.map((w, i) => (
        <FadeIn key={w.id} delay={i * 0.03}>
          <div className="group relative flex h-full flex-col justify-between gap-6 surface p-5 transition-shadow duration-200 hover:shadow-md">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <Link
                  href={`/wallets/${w.id}`}
                  className="truncate font-medium after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none focus-visible:after:ring-2 focus-visible:after:ring-ring"
                >
                  {w.name}
                </Link>
                <div className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                  <span className="num">{shortId(w.id)}</span>
                  <CopyButton value={w.id} label="Copy wallet ID" className="relative z-10 size-6" />
                </div>
              </div>
              <StatusBadge value={w.status} />
            </div>
            <div className="flex items-end justify-between">
              <div>
                <div className="eyebrow">{w.currency} balance</div>
                <Amount
                  minor={w.balance}
                  currency={w.currency}
                  className="mt-1 block text-2xl font-medium tracking-tight"
                />
              </div>
              <ArrowRightIcon className="size-4 text-muted-foreground transition-transform duration-150 group-hover:translate-x-0.5" />
            </div>
          </div>
        </FadeIn>
      ))}
    </div>
  );
}

function Grid({ skeleton }: { skeleton: true }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy={skeleton}>
      {[0, 1, 2].map((i) => (
        <Skeleton key={i} className="h-40 rounded-xl" />
      ))}
    </div>
  );
}
