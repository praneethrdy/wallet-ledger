"use client";

import { ScaleIcon } from "lucide-react";
import Link from "next/link";

import { ListFooter, useCursorList, type CursorPage } from "@/components/data/cursor-list";
import { StatusBadge } from "@/components/ledger/status-badge";
import { EmptyState } from "@/components/states/states";
import type { Schemas } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";

type Run = Schemas["RunResponse"];

export function RunsList({ firstPage }: { firstPage: CursorPage<Run> }) {
  const list = useCursorList<Run>(["recon-runs"], "/bff/admin/reconciliation/runs", firstPage);

  if (list.items.length === 0) {
    return (
      <EmptyState
        icon={<ScaleIcon />}
        title="No runs yet"
        description="Runs happen on a schedule; an admin can also run one now."
      />
    );
  }

  return (
    <div>
      <div className="space-y-0.5">
        {list.items.map((run) => {
          const errors = run.findings.filter((f) => f.severity === "ERROR").length;
          const warnings = run.findings.length - errors;
          return (
            <Link
              key={run.id}
              href={`/admin/reconciliation/${run.id}`}
              className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg px-3 py-3 transition-colors hover:bg-muted/70 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            >
              <StatusBadge value={run.status} />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium">{formatDateTime(run.startedAt)}</span>
                <span className="text-xs text-muted-foreground">
                  {run.trigger === "MANUAL" ? "Run manually" : "Scheduled"}
                  {run.checkedTransactions !== null &&
                    ` · ${run.checkedTransactions.toLocaleString("en-IN")} transactions checked`}
                  {run.finishedAt && ` · ${duration(run.startedAt, run.finishedAt)}`}
                </span>
              </span>
              <span className="text-xs whitespace-nowrap">
                {errors > 0 && (
                  <span className="font-medium text-danger-text">
                    {errors} error{errors === 1 ? "" : "s"}
                  </span>
                )}
                {errors > 0 && warnings > 0 && <span className="text-muted-foreground"> · </span>}
                {warnings > 0 && (
                  <span className="text-warning-text">
                    {warnings} warning{warnings === 1 ? "" : "s"}
                  </span>
                )}
                {run.findings.length === 0 && run.status !== "RUNNING" && (
                  <span className="text-muted-foreground">No findings</span>
                )}
              </span>
            </Link>
          );
        })}
      </div>
      <ListFooter list={list} />
    </div>
  );
}

function duration(start: string, end: string): string {
  const ms = new Date(end).getTime() - new Date(start).getTime();
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}
