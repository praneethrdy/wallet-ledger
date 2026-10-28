import { ArrowDownLeftIcon, ArrowUpRightIcon, PlusIcon, SendIcon, WalletIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { CashFlowChart } from "@/components/charts/cash-flow-chart";
import { Amount } from "@/components/ledger/amount";
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
import { requireUser } from "@/lib/auth/current-user";

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

/** Customers move money; an admin sees its own wallets here but can't transfer or deposit. */
async function QuickActions() {
  const user = await requireUser();
  if (user.role !== "CUSTOMER") return null;
  return (
    <>
      <Button variant="outline" asChild>
        <Link href="/deposit">
          <PlusIcon /> Add money
        </Link>
      </Button>
      <Button asChild>
        <Link href="/transfer">
          <SendIcon /> Send money
        </Link>
      </Button>
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
              summary.totals.length > 1 ? (
                <span className="flex flex-wrap gap-x-3">
                  {summary.totals
                    .filter((t) => t !== primary)
                    .map((t) => (
                      <Amount key={t.currency} minor={t.balance} currency={t.currency} />
                    ))}
                </span>
              ) : (
                `Across ${wallets.length} wallet${wallets.length === 1 ? "" : "s"}`
              )
            }
          />
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
                  <span className="text-xs text-muted-foreground">{w.currency}</span>
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
            <EmptyState title="No transactions yet" description="Add money or make a transfer to see it here." />
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
