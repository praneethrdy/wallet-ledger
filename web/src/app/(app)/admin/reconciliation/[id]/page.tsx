import { CircleAlertIcon, CircleCheckIcon, ChevronRightIcon, TriangleAlertIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { StatusBadge } from "@/components/ledger/status-badge";
import { FadeIn } from "@/components/motion/fade-in";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get, type Schemas } from "@/lib/api/client";
import { ApiError } from "@/lib/errors";
import { formatDateTimeFull, isUuid } from "@/lib/format";

import { checkLabel } from "../findings";

export const metadata: Metadata = { title: "Reconciliation run" };

type Finding = Schemas["Finding"];

export default function RunPage({ params }: PageProps<"/admin/reconciliation/[id]">) {
  return (
    <Suspense
      fallback={
        <div className="max-w-4xl space-y-6">
          <Skeleton className="h-24 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      }
    >
      <Run params={params} />
    </Suspense>
  );
}

async function Run({ params }: { params: PageProps<"/admin/reconciliation/[id]">["params"] }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();

  let run: Schemas["RunResponse"];
  try {
    run = await get((await api()).GET("/api/v1/admin/reconciliation/runs/{id}", { params: { path: { id } } }));
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  const errors = run.findings.filter((f) => f.severity === "ERROR");
  const warnings = run.findings.filter((f) => f.severity === "WARNING");

  return (
    <div className="max-w-4xl">
      <FadeIn className="mb-8">
        <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-sm text-muted-foreground">
          <Link href="/admin/reconciliation" className="hover:text-foreground">
            Reconciliation
          </Link>
          <ChevronRightIcon className="size-3.5" aria-hidden />
          <span className="text-foreground">{formatDateTimeFull(run.startedAt)}</span>
        </nav>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Run of {formatDateTimeFull(run.startedAt)}</h1>
          <StatusBadge value={run.status} />
        </div>
      </FadeIn>

      <dl className="mb-6 grid gap-4 surface p-5 text-sm sm:grid-cols-4">
        <Stat label="Trigger" value={run.trigger === "MANUAL" ? "Manual" : "Scheduled"} />
        <Stat
          label="Transactions checked"
          value={run.checkedTransactions !== null ? run.checkedTransactions.toLocaleString("en-IN") : "—"}
        />
        <Stat
          label="Debits = credits"
          value={
            run.totalDebits !== null && run.totalCredits !== null
              ? run.totalDebits === run.totalCredits
                ? "Yes"
                : "No"
              : "—"
          }
          hint="Sum of all legs, minor units"
        />
        <Stat label="Finished" value={run.finishedAt ? formatDateTimeFull(run.finishedAt) : "Still running"} />
      </dl>

      {run.findings.length === 0 ? (
        <div className="flex items-start gap-3 surface p-5">
          <CircleCheckIcon className="mt-0.5 size-5 text-credit-text" aria-hidden />
          <div>
            <p className="text-sm font-medium">No findings</p>
            <p className="mt-0.5 text-sm text-muted-foreground">
              Every check passed: the ledger balances and every deposit is matched.
            </p>
          </div>
        </div>
      ) : (
        <div className="grid gap-6">
          <FindingGroup title="Errors" findings={errors} tone="error" />
          <FindingGroup title="Warnings" findings={warnings} tone="warning" />
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className="mt-1.5 font-medium">{value}</dd>
      {hint && <dd className="text-xs text-muted-foreground">{hint}</dd>}
    </div>
  );
}

function FindingGroup({ title, findings, tone }: { title: string; findings: Finding[]; tone: "error" | "warning" }) {
  if (findings.length === 0) return null;
  const Icon = tone === "error" ? CircleAlertIcon : TriangleAlertIcon;
  return (
    <section aria-label={title}>
      <h2 className={tone === "error" ? "mb-2 eyebrow text-danger-text" : "mb-2 eyebrow text-warning-text"}>
        {title} · {findings.length}
      </h2>
      <ul className="divide-y divide-border overflow-hidden surface">
        {findings.map((f, i) => (
          <li key={i} className="flex gap-3 p-4">
            <Icon
              className={
                tone === "error"
                  ? "mt-0.5 size-4 shrink-0 text-danger-text"
                  : "mt-0.5 size-4 shrink-0 text-warning-text"
              }
              aria-hidden
            />
            <div className="min-w-0">
              <p className="text-sm font-medium">{checkLabel(f.check)}</p>
              <p className="num text-xs text-muted-foreground">{f.check}</p>
              <dl className="mt-2 grid gap-x-4 gap-y-1 text-xs sm:grid-cols-[max-content_1fr]">
                {Object.entries(f.details).map(([k, v]) => (
                  <Detail key={k} name={k} value={v} />
                ))}
              </dl>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** One finding detail; IDs link to the staff screens that can explain them. */
function Detail({ name, value }: { name: string; value: unknown }) {
  const text = typeof value === "string" || typeof value === "number" ? String(value) : JSON.stringify(value);
  const href =
    name === "transactionId" && isUuid(text)
      ? `/support/transactions/${text}`
      : name === "walletId" && isUuid(text)
        ? `/support/transactions?walletId=${text}`
        : undefined;
  return (
    <>
      <dt className="text-muted-foreground">{name}</dt>
      <dd className="min-w-0 num break-all">
        {href ? (
          <Link href={href} className="underline-offset-4 hover:underline">
            {text}
          </Link>
        ) : (
          text
        )}
      </dd>
    </>
  );
}
