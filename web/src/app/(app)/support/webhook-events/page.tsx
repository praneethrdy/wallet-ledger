import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { cn } from "cn";

import { PageHeader } from "@/components/shell/page-header";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";

import { EventsList } from "./events-list";
import { WEBHOOK_STATUSES, webhookStatus } from "./status";

export const metadata: Metadata = { title: "Webhooks · Support" };

const LABEL: Record<string, string> = {
  RECEIVED: "Received",
  PROCESSED: "Processed",
  REJECTED: "Rejected",
  FAILED: "Failed",
};

export default function WebhookEventsPage({ searchParams }: PageProps<"/support/webhook-events">) {
  return (
    <>
      <PageHeader
        title="Webhook events"
        description="Every event the payment provider sent, newest first. Rejected = failed signature or freshness checks; failed = verified but processing errored."
      />
      <Suspense fallback={<Skeleton className="h-[480px] rounded-xl" />}>
        <Events searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Events({ searchParams }: { searchParams: PageProps<"/support/webhook-events">["searchParams"] }) {
  const status = webhookStatus((await searchParams).status);
  const firstPage = await get(
    (await api()).GET("/api/v1/support/webhook-events", { params: { query: { status, limit: 20 } } }),
  );

  return (
    <div className="grid gap-4">
      <nav
        aria-label="Filter by status"
        className="flex flex-wrap gap-1 rounded-lg border border-border bg-card p-1 sm:w-fit"
      >
        {[undefined, ...WEBHOOK_STATUSES].map((s) => (
          <Link
            key={s ?? "all"}
            href={s ? `/support/webhook-events?status=${s}` : "/support/webhook-events"}
            aria-current={s === status ? "page" : undefined}
            className={cn(
              "rounded-md px-3 py-1.5 text-sm transition-colors focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
              s === status ? "bg-muted font-medium text-foreground" : "text-muted-foreground hover:text-foreground",
            )}
          >
            {s ? LABEL[s] : "All"}
          </Link>
        ))}
      </nav>
      <Card className="gap-0 overflow-hidden py-0">
        <EventsList key={status ?? "all"} status={status} firstPage={firstPage} />
      </Card>
    </div>
  );
}
