import type { Metadata } from "next";
import { Suspense } from "react";

import { PageHeader } from "@/components/shell/page-header";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";
import { requireUser } from "@/lib/auth/current-user";

import { RunNowButton } from "./run-now";
import { RunsList } from "./runs-list";

export const metadata: Metadata = { title: "Reconciliation" };

export default function ReconciliationPage() {
  return (
    <>
      <PageHeader
        title="Reconciliation"
        description="Independent checks that the ledger adds up: every transaction balances, debits equal credits per currency, no wallet below zero, every deposit matched."
        actions={
          <Suspense>
            <AdminActions />
          </Suspense>
        }
      />
      <Suspense fallback={<Skeleton className="h-[480px] rounded-xl" />}>
        <Runs />
      </Suspense>
    </>
  );
}

/** "Run now" is ADMIN only; SUPPORT can read the results. */
async function AdminActions() {
  const user = await requireUser();
  return user.role === "ADMIN" ? <RunNowButton /> : null;
}

async function Runs() {
  const firstPage = await get(
    (await api()).GET("/api/v1/admin/reconciliation/runs", { params: { query: { limit: 20 } } }),
  );
  return (
    <Card className="gap-0 px-2 py-2">
      <RunsList firstPage={firstPage} />
    </Card>
  );
}
