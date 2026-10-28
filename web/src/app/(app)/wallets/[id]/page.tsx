import { ChevronRightIcon, PlusIcon, SendIcon, SnowflakeIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Sparkline } from "@/components/charts/sparkline";
import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { AnimatedMoney } from "@/components/motion/animated-money";
import { FadeIn } from "@/components/motion/fade-in";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { requireUser } from "@/lib/auth/current-user";
import { ApiError } from "@/lib/errors";
import { isUuid } from "@/lib/format";

import { EntriesTable } from "./entries-table";

export const metadata: Metadata = { title: "Wallet" };

export default function WalletPage({ params }: PageProps<"/wallets/[id]">) {
  return (
    <Suspense fallback={<WalletSkeleton />}>
      <Wallet params={params} />
    </Suspense>
  );
}

async function Wallet({ params }: { params: PageProps<"/wallets/[id]">["params"] }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();

  const client = await api();
  const user = await requireUser();
  let wallet, history, entries;
  try {
    [wallet, history, entries] = await Promise.all([
      get(client.GET("/api/v1/wallets/{id}", { params: { path: { id } } })),
      get(client.GET("/api/v1/wallets/{id}/balance-history", { params: { path: { id }, query: { days: 30 } } })),
      get(client.GET("/api/v1/wallets/{id}/entries", { params: { path: { id }, query: { limit: 25 } } })),
    ]);
  } catch (err) {
    // 404 = missing OR someone else's wallet (the backend never says which).
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  const canMoveMoney = user.role === "CUSTOMER" && wallet.status === "ACTIVE";
  const first = history[0]?.balance ?? 0;
  const change = wallet.balance - first;

  return (
    <>
      <FadeIn className="mb-8">
        <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-sm text-muted-foreground">
          <Link href="/wallets" className="hover:text-foreground">
            Wallets
          </Link>
          <ChevronRightIcon className="size-3.5" aria-hidden />
          <span className="truncate text-foreground">{wallet.name}</span>
        </nav>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              <h1 className="truncate text-2xl font-semibold tracking-tight">{wallet.name}</h1>
              <StatusBadge value={wallet.status} />
            </div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
              <span className="num">{wallet.id}</span>
              <CopyButton value={wallet.id} label="Copy wallet ID" className="size-6" />
            </div>
          </div>
          {canMoveMoney && (
            <div className="flex gap-2">
              <Button variant="outline" asChild>
                <Link href={`/deposit?wallet=${wallet.id}`}>
                  <PlusIcon /> Add money
                </Link>
              </Button>
              <Button asChild>
                <Link href={`/transfer?from=${wallet.id}`}>
                  <SendIcon /> Send from this wallet
                </Link>
              </Button>
            </div>
          )}
        </div>
      </FadeIn>

      {wallet.status === "FROZEN" && (
        <Alert className="mb-6 border-[color-mix(in_oklab,var(--warning)_35%,transparent)] bg-[color-mix(in_oklab,var(--warning)_8%,transparent)]">
          <SnowflakeIcon className="text-warning-text" />
          <AlertTitle>This wallet is frozen</AlertTitle>
          <AlertDescription>
            Transfers in and out are blocked. Settled deposits still arrive. Contact support if you think this is a
            mistake.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6">
        <Card>
          <CardContent className="grid gap-6 md:grid-cols-[minmax(0,320px)_1fr] md:items-end">
            <div>
              <div className="eyebrow">{wallet.currency} balance</div>
              <AnimatedMoney
                minor={wallet.balance}
                currency={wallet.currency}
                className="mt-2 block num text-4xl font-medium tracking-tight"
              />
              <p className="mt-2 text-xs text-muted-foreground">
                {change === 0 ? (
                  "No change in the last 30 days"
                ) : (
                  <>
                    <span className={change > 0 ? "num text-credit-text" : "num text-foreground"}>
                      {change > 0 ? "+" : "−"}
                      <AnimatedMoney minor={Math.abs(change)} currency={wallet.currency} />
                    </span>{" "}
                    over 30 days
                  </>
                )}
              </p>
            </div>
            <Sparkline data={history} currency={wallet.currency} height={88} />
          </CardContent>
        </Card>

        <Card className="gap-0 pb-0">
          <CardHeader className="pb-4">
            <CardTitle>Ledger entries</CardTitle>
          </CardHeader>
          <EntriesTable walletId={wallet.id} firstPage={entries} />
        </Card>
      </div>
    </>
  );
}

function WalletSkeleton() {
  return (
    <div className="space-y-6" aria-busy aria-label="Loading wallet">
      <Skeleton className="h-4 w-40" />
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-44 rounded-xl" />
      <Skeleton className="h-96 rounded-xl" />
    </div>
  );
}
