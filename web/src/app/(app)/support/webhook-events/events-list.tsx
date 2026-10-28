"use client";

import { ChevronRightIcon, WebhookIcon } from "lucide-react";

import { cn } from "cn";

import { ListFooter, useCursorList, type CursorPage } from "@/components/data/cursor-list";
import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { EmptyState } from "@/components/states/states";
import type { Schemas } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";

type Event = Schemas["WebhookEventView"];

const PROBLEM = new Set(["REJECTED", "FAILED"]);

/** Each event expands (native <details>, keyboard-accessible) to show its error and raw JSON payload. */
export function EventsList({ status, firstPage }: { status?: string; firstPage: CursorPage<Event> }) {
  const list = useCursorList<Event>(
    ["webhook-events", status ?? "all"],
    `/bff/support/webhook-events${status ? `?status=${status}` : ""}`,
    firstPage,
  );

  if (list.items.length === 0) {
    return (
      <EmptyState
        icon={<WebhookIcon />}
        title={status ? "No events with this status" : "No webhook events yet"}
        description="Events appear here as soon as the provider calls the webhook endpoint."
      />
    );
  }

  return (
    <div>
      <ul className="divide-y divide-border">
        {list.items.map((e) => {
          const problem = PROBLEM.has(e.status);
          const payload = JSON.stringify(e.payload, null, 2);
          return (
            <li key={e.id} className={cn(problem && "bg-[color-mix(in_oklab,var(--danger)_5%,transparent)]")}>
              <details className="group">
                <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none focus-visible:ring-inset [&::-webkit-details-marker]:hidden">
                  <ChevronRightIcon
                    className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
                    aria-hidden
                  />
                  <StatusBadge value={e.status} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate num text-sm">{e.eventType}</span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {e.provider} · <span className="num">{e.providerEventId}</span>
                    </span>
                  </span>
                  {e.error && (
                    <span className="max-w-[40ch] truncate num text-xs text-danger-text" title={e.error}>
                      {e.error}
                    </span>
                  )}
                  <span className="text-xs whitespace-nowrap text-muted-foreground">
                    {formatDateTime(e.receivedAt)}
                  </span>
                </summary>
                <div className="space-y-3 px-5 pb-4 pl-13">
                  <dl className="grid gap-x-4 gap-y-1 text-xs sm:grid-cols-[max-content_1fr]">
                    <dt className="text-muted-foreground">Received</dt>
                    <dd>{formatDateTime(e.receivedAt)}</dd>
                    <dt className="text-muted-foreground">Processed</dt>
                    <dd>{e.processedAt ? formatDateTime(e.processedAt) : "—"}</dd>
                    {e.error && (
                      <>
                        <dt className="text-muted-foreground">Error</dt>
                        <dd className="num break-all text-danger-text">{e.error}</dd>
                      </>
                    )}
                  </dl>
                  <div className="relative">
                    <pre className="max-h-80 overflow-auto rounded-lg border border-border bg-muted/50 p-3 num text-xs leading-relaxed">
                      {payload}
                    </pre>
                    <CopyButton value={payload} label="Copy payload" className="absolute top-2 right-2 size-7" />
                  </div>
                </div>
              </details>
            </li>
          );
        })}
      </ul>
      <ListFooter list={list} />
    </div>
  );
}
