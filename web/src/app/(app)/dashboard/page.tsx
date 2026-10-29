import { ArrowDownLeftIcon, ArrowUpRightIcon, PlusIcon, WalletIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { CashFlowChart } from "@/components/charts/cash-flow-chart";
import { Sparkline } from "@/components/charts/sparkline";
import { Amount } from "@/components/ledger/amount";
import { QuickActions } from "@/components/ledger/quick-actions";
import { StatCard } from "@/components/ledger/stat-card";
import { StatusBadge } from "@/components/ledger/status-badge";
import { TransactionRow } from "@/components/ledger/transaction-row";
import { AnimatedMoney } from "@/components/motion/animated-money";
import { FadeIn } from "@/components/motion/fade-in";
import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { balanceSeries, walletShares, windowChange } from "@/lib/dashboard";

export const metadata: Metadata = { title: "Dashboard" };

export default function DashboardPage() {
  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Your balances, this month’s cash flow and recent activity."
        actions={
          <Suspense>
            <QuickActions />
          </Suspense>
        }
      />
      <Suspense fallback={<DashboardSkeleton />}>
        <Dashboard />
      </Suspense>
    </>
  );
}

async function Dashboard() {
  const client = await api();
  const [summary, wallets] = await Promise.all([
    get(client.GET("/api/v1/dashboard/summary")),
    get(client.GET("/api/v1/wallets")),
  ]);
  const flowCurrency = summary.thisMonth.currency;
  const primary = summary.totals.find((t) => t.currency === flowCurrency) ?? summary.totals[0];
  // The flows are in the default currency, so the history only applies when that's the headline total.
  const history = primary?.currency === flowCurrency ? balanceSeries(primary.balance, summary.cashFlow30d) : [];
  const shares = walletShares(wallets, flowCurrency);
  // Colour dots and percentages only make sense next to the bar, which needs two or more wallets.
  const shareIndex = new Map(shares.length > 1 ? shares.map((s, i) => [s.wallet.id, i]) : []);

  if (wallets.length === 0) {
    return (
      <div className="surface">
        <EmptyState
          icon={<WalletIcon />}
          title="Create your first wallet"
          description="Wallets hold money in one currency. You can have several, for example Main and Savings."
          action={
            <Button asChild>
              <Link href="/wallets">
                <PlusIcon /> New wallet
              </Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="grid gap-4 md:grid-cols-3">
        <FadeIn>
          <StatCard
            label="Total balance"
            icon={<WalletIcon />}
            value={<AnimatedMoney minor={primary?.balance ?? 0} currency={primary?.currency ?? flowCurrency} />}
            hint={
              <span className="flex flex-wrap gap-x-3">
                {history.length > 0 && (
                  <span>
                    <Amount minor={windowChange(summary.cashFlow30d)} currency={flowCurrency} tone="signed" /> in 30
                    days
                  </span>
                )}
                {summary.totals.length > 1
                  ? summary.totals
                      .filter((t) => t !== primary)
                      .map((t) => <Amount key={t.currency} minor={t.balance} currency={t.currency} />)
                  : `Across ${wallets.length} wallet${wallets.length === 1 ? "" : "s"}`}
              </span>
            }
          >
            {history.length > 1 && <Sparkline data={history} currency={flowCurrency} height={44} />}
          </StatCard>
        </FadeIn>
        <FadeIn delay={0.04}>
          <StatCard
            label="In this month"
            icon={<ArrowDownLeftIcon />}
            value={<Amount minor={summary.thisMonth.inflow} currency={flowCurrency} tone="credit" />}
            hint="Deposits and money received"
          />
        </FadeIn>
        <FadeIn delay={0.08}>
          <StatCard
            label="Out this month"
            icon={<ArrowUpRightIcon />}
            value={<Amount minor={summary.thisMonth.outflow} currency={flowCurrency} tone="debit" />}
            hint="Money sent · moves between your own wallets don’t count"
          />
        </FadeIn>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Cash flow · last 30 days</CardTitle>
            <div className="flex items-center gap-4 text-xs text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <span className="size-2 rounded-sm bg-credit" /> In
              </span>
              <span className="flex items-center gap-1.5">
                <span className="size-2 rounded-sm bg-line-strong" /> Out
              </span>
            </div>
          </CardHeader>
          <CardContent className="flex-1">
            <CashFlowChart data={summary.cashFlow30d} currency={flowCurrency} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>Wallets</CardTitle>
            <Link href="/wallets" className="text-xs text-muted-foreground hover:text-foreground">
              Manage
            </Link>
          </CardHeader>
          <CardContent className="space-y-1 px-2">
            {shares.length > 1 && (
              <div className="px-3 pb-3">
                <div
                  role="img"
                  aria-label={`How your ${flowCurrency} balance is split: ${shares
                    .map(({ wallet, share }) => `${wallet.name} ${percent(share)}`)
                    .join(", ")}`}
                  className="flex h-2 origin-left animate-grow-x gap-0.5 overflow-hidden rounded-full motion-reduce:animate-none"
                >
                  {shares.map(({ wallet, share }, i) => (
                    <span key={wallet.id} className="min-w-1" style={{ flex: `${share} 1 0`, background: shade(i) }} />
                  ))}
                </div>
              </div>
            )}
            {wallets.map((w) => (
              <Link
                key={w.id}
                href={`/wallets/${w.id}`}
                className="flex items-center justify-between gap-3 rounded-lg px-3 py-2.5 transition-colors hover:bg-muted/70 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              >
                <span className="min-w-0">
                  <span className="flex items-center gap-2">
                    <span className="truncate text-sm font-medium">{w.name}</span>
                    {w.status !== "ACTIVE" && <StatusBadge value={w.status} />}
                  </span>
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    {shareIndex.has(w.id) && (
                      <span
                        className="size-1.5 rounded-full"
                        style={{ background: shade(shareIndex.get(w.id)!) }}
                        aria-hidden
                      />
                    )}
                    {w.currency}
                    {shareIndex.has(w.id) && ` · ${percent(shares[shareIndex.get(w.id)!].share)}`}
                  </span>
                </span>
                <Amount minor={w.balance} currency={w.currency} className="text-sm font-medium" />
              </Link>
            ))}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Recent transactions</CardTitle>
          <Link href="/transactions" className="text-xs text-muted-foreground hover:text-foreground">
            View all
          </Link>
        </CardHeader>
        <CardContent className="px-2">
          {summary.recentTransactions.length === 0 ? (
            <EmptyState
              title="No transactions yet"
              description="Add money or make a transfer to see it here."
              action={<QuickActions />}
            />
          ) : (
            <div className="space-y-0.5">
              {summary.recentTransactions.map((tx) => (
                <TransactionRow key={tx.id} tx={tx} href={`/transactions/${tx.id}`} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/** Allocation colours: the accent, then lighter tints of it; anything past four is neutral. */
function shade(i: number): string {
  const mix = [100, 65, 40, 22][i];
  return mix === undefined ? "var(--line-strong)" : `color-mix(in oklab, var(--brand) ${mix}%, var(--card))`;
}

function percent(share: number): string {
  return share < 0.01 ? "<1%" : `${Math.round(share * 100)}%`;
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" aria-busy aria-label="Loading dashboard">
      <div className="grid gap-4 md:grid-cols-3">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-[126px] rounded-xl" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <Skeleton className="h-80 rounded-xl" />
        <Skeleton className="h-80 rounded-xl" />
      </div>
      <Skeleton className="h-96 rounded-xl" />
    </div>
  );
}
