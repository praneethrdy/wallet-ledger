"use server";

import { z } from "zod";

import { api, unwrap, type Schemas } from "@/lib/api/client";
import { idempotencyHeader } from "@/lib/api/idempotency";
import { env } from "@/lib/env";
import { ApiError, toActionError, type ActionResult } from "@/lib/errors";
import { isUuid } from "@/lib/format";
import { moneyParseMessage, parseMoney } from "@/lib/money";

const input = z.object({
  walletId: z.string().refine(isUuid, "Choose a wallet"),
  amount: z.string(),
  currency: z.enum(["INR", "USD", "EUR"]),
});

export async function createDeposit(form: FormData): Promise<ActionResult<Schemas["DepositResponse"]>> {
  const parsed = input.safeParse(Object.fromEntries(form));
  if (!parsed.success) {
    return {
      ok: false,
      error: {
        code: "VALIDATION_FAILED",
        message: "Please check the highlighted fields.",
        fieldErrors: Object.fromEntries(parsed.error.issues.map((i) => [String(i.path[0]), i.message])),
      },
    };
  }
  const { walletId, currency } = parsed.data;
  const amount = parseMoney(parsed.data.amount, currency);
  if (!amount.ok) {
    return {
      ok: false,
      error: {
        code: "VALIDATION_FAILED",
        message: "Please check the highlighted fields.",
        fieldErrors: { amount: moneyParseMessage(amount.error, currency) },
      },
    };
  }

  try {
    const client = await api();
    const { data, replayed } = await unwrap(
      client.POST("/api/v1/deposits", {
        params: { header: idempotencyHeader(form) },
        body: { walletId, amount: amount.minor, currency },
      }),
    );
    return { ok: true, data, replayed };
  } catch (err) {
    const error = toActionError(err, { WALLET_FROZEN: "This wallet is frozen, so it can’t take deposits." });
    if (error.code === "WALLET_FROZEN" || error.code === "CURRENCY_MISMATCH")
      error.fieldErrors.walletId = error.message;
    return { ok: false, error };
  }
}

/**
 * Demo only: completes the caller's own pending deposit with a simulated payment. The backend
 * signs a provider event and runs it through the normal webhook pipeline, and it answers 404
 * unless its demo switch (DEMO_PAYMENTS_ENABLED) is on.
 */
export async function completeDemoPayment(form: FormData): Promise<ActionResult<Schemas["DepositResponse"]>> {
  const depositId = String(form.get("depositId") ?? "");
  const outcome = form.get("outcome") === "FAILURE" ? "FAILURE" : "SUCCESS";
  if (!env.demoPaymentsEnabled || !isUuid(depositId)) {
    return { ok: false, error: toActionError(new ApiError(404, { code: "NOT_FOUND" })) };
  }
  try {
    const client = await api();
    const { data, replayed } = await unwrap(
      client.POST("/api/v1/deposits/{id}/demo-payment", {
        params: { header: idempotencyHeader(form), path: { id: depositId } },
        body: { outcome },
      }),
    );
    return { ok: true, data, replayed };
  } catch (err) {
    return { ok: false, error: toActionError(err) };
  }
}
