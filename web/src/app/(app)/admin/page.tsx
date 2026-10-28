import { ActivityIcon, ArrowLeftRightIcon, ScaleIcon, WebhookIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { StatCard } from "@/components/ledger/stat-card";
import { StatusBadge } from "@/components/ledger/status-badge";
import { FadeIn } from "@/components/motion/fade-in";
import { PageHeader } from "@/components/shell/page-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";
import { formatMoney } from "@/lib/money";

export const metadata: Metadata = { title: "Overview" };

export default function AdminOverviewPage() {
  return (
    <>
      <PageHeader title="Overview" description="Today’s volume, failed webhooks and the last reconciliation." />
      <Suspense fallback={<OverviewSkeleton />}>
        <Overview />
      </Suspense>
    </>
  );
}

async function Overview() {
  const o = await get((await api()).GET("/api/v1/admin/overview"));
  const [primary, ...others] = o.volumeToday;
  const last = o.lastReconciliation;

  return (
    <div className="grid gap-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <FadeIn>
          <StatCard
            label="Volume today"
            icon={<ActivityIcon />}
            value={primary ? formatMoney(primary.amount, primary.currency) : "—"}
            hint={
              others.length
                ? others.map((v) => formatMoney(v.amount, v.currency)).join(" · ")
                : primary
                  ? "Transferred since midnight (IST)"
                  : "Nothing has moved yet today"
            }
          />
        </FadeIn>
        <FadeIn delay={0.04}>
          <StatCard
            label="Transfers today"
            icon={<ArrowLeftRightIcon />}
            value={o.transfersToday.toLocaleString("en-IN")}
            hint="Customer-to-wallet transfers"
          />
        </FadeIn>
        <FadeIn delay={0.08}>
          <Link
            href="/support/webhook-events"
            className="block rounded-xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
          >
            <StatCard
              label="Failed webhooks · 24h"
              icon={<WebhookIcon />}
              value={
                <span className={o.failedWebhooks24h > 0 ? "text-danger-text" : undefined}>{o.failedWebhooks24h}</span>
              }
              hint={o.failedWebhooks24h > 0 ? "Needs a look → view events" : "All provider events processed"}
              className="h-full transition-colors hover:border-line-strong"
            />
          </Link>
        </FadeIn>
        <FadeIn delay={0.12}>
          <Link
            href={last ? `/admin/reconciliation/${last.id}` : "/admin/reconciliation"}
            className="block rounded-xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
          >
            <StatCard
              label="Last reconciliation"
              icon={<ScaleIcon />}
              value={last ? <StatusBadge value={last.status} className="h-7 px-3 text-sm" /> : "Never run"}
              hint={last ? `Started ${formatDateTime(last.startedAt)}` : "Run one from Reconciliation"}
              className="h-full transition-colors hover:border-line-strong"
            />
          </Link>
        </FadeIn>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Console</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: "/admin/wallets", title: "Wallets", text: "Find a wallet by owner email; freeze or unfreeze it." },
            { href: "/support/transactions", title: "Transactions", text: "Search any transaction; reverse it." },
            { href: "/support/users", title: "Users", text: "Look up a customer and their balances." },
            {
              href: "/support/webhook-events",
              title: "Webhooks",
              text: "Every provider event, including rejected ones.",
            },
            { href: "/admin/reconciliation", title: "Reconciliation", text: "Prove the ledger balances; run it now." },
          ].map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="rounded-lg border border-border p-4 transition-colors hover:border-line-strong hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            >
              <p className="text-sm font-medium">{l.title}</p>
              <p className="mt-1 text-xs text-muted-foreground">{l.text}</p>
            </Link>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

function OverviewSkeleton() {
  return (
    <div className="grid gap-6" aria-busy aria-label="Loading overview">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-[126px] rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-64 rounded-xl" />
    </div>
  );
}
