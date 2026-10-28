"use client";

import { PlayIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { useIdempotencyKey } from "@/lib/idempotency";

import { runReconciliation } from "../actions";

/** The run is synchronous on the backend, so "pending" is the whole run: say what's happening. */
export function RunNowButton() {
  const router = useRouter();
  const idempotency = useIdempotencyKey();
  const [pending, startTransition] = useTransition();

  function run() {
    const form = new FormData();
    form.set("idempotencyKey", idempotency.get());
    startTransition(async () => {
      const res = await runReconciliation(form);
      if (!res.ok) {
        if (res.error.code !== "INTERNAL_ERROR" && res.error.code !== "NETWORK_ERROR") idempotency.reset();
        toast.error("Reconciliation didn’t run", { description: res.error.message });
        return;
      }
      idempotency.reset();
      const errors = res.data.findings.filter((f) => f.severity === "ERROR").length;
      if (res.data.status === "PASSED") {
        toast.success("Ledger reconciled", {
          description: `${(res.data.checkedTransactions ?? 0).toLocaleString("en-IN")} transactions checked, no errors.`,
        });
      } else {
        toast.error(`Reconciliation found ${errors} error${errors === 1 ? "" : "s"}`);
      }
      router.push(`/admin/reconciliation/${res.data.id}`);
    });
  }

  return (
    <div className="flex items-center gap-3">
      {pending && (
        <span className="text-xs text-muted-foreground" role="status">
          Checking every transaction…
        </span>
      )}
      <Button onClick={run} loading={pending}>
        <PlayIcon /> Run now
      </Button>
    </div>
  );
}
