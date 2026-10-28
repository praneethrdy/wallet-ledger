"use server";

import { z } from "zod";

import { api, unwrap, type Schemas } from "@/lib/api/client";
import { idempotencyHeader } from "@/lib/api/idempotency";
import { toActionError, type ActionResult } from "@/lib/errors";
import { isUuid } from "@/lib/format";

function invalid(fieldErrors: Record<string, string>) {
  return {
    ok: false as const,
    error: { code: "VALIDATION_FAILED", message: "Please check the highlighted fields.", fieldErrors },
  };
}

/** Freeze or unfreeze a wallet (ADMIN; the backend enforces the role and audits the change). */
export async function setWalletFrozen(form: FormData): Promise<ActionResult<Schemas["AdminWalletView"]>> {
  const id = String(form.get("walletId") ?? "");
  const freeze = form.get("freeze") === "true";
  if (!isUuid(id)) return invalid({ walletId: "Unknown wallet" });
  try {
    const client = await api();
    const params = { header: idempotencyHeader(form), path: { id } };
    const { data, replayed } = await unwrap(
      freeze
        ? client.POST("/api/v1/admin/wallets/{id}/freeze", { params })
        : client.POST("/api/v1/admin/wallets/{id}/unfreeze", { params }),
    );
    return { ok: true, data, replayed };
  } catch (err) {
    return { ok: false, error: toActionError(err) };
  }
}

/** Runs reconciliation now. Synchronous on the backend: this resolves when the run has finished. */
export async function runReconciliation(form: FormData): Promise<ActionResult<Schemas["RunResponse"]>> {
  try {
    const client = await api();
    const { data, replayed } = await unwrap(
      client.POST("/api/v1/admin/reconciliation/run", { params: { header: idempotencyHeader(form) } }),
    );
    return { ok: true, data, replayed };
  } catch (err) {
    return { ok: false, error: toActionError(err) };
  }
}

const reverseInput = z.object({
  transactionId: z.string().refine(isUuid, "Unknown transaction"),
  reason: z.string().trim().min(1, "Say why this is being reversed").max(500, "Use at most 500 characters"),
});

/** Posts a REVERSAL transaction for the original. History isn't changed; the reason is audited. */
export async function reverseTransaction(form: FormData): Promise<ActionResult<Schemas["TransactionDetail"]>> {
  const parsed = reverseInput.safeParse({ transactionId: form.get("transactionId"), reason: form.get("reason") });
  if (!parsed.success) {
    return invalid(Object.fromEntries(parsed.error.issues.map((i) => [String(i.path[0]), i.message])));
  }
  try {
    const client = await api();
    const { data, replayed } = await unwrap(
      client.POST("/api/v1/admin/transactions/{id}/reverse", {
        params: { header: idempotencyHeader(form), path: { id: parsed.data.transactionId } },
        body: { reason: parsed.data.reason },
      }),
    );
    return { ok: true, data, replayed };
  } catch (err) {
    return { ok: false, error: toActionError(err) };
  }
}
