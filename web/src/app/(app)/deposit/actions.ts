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
 * DEV ONLY: plays the payment provider for one of the caller's deposits. The backend's mock
 * endpoint signs a webhook and delivers it to itself, so the real verification path runs.
 * The deposit is first read with the caller's token: you can only "pay" your own deposits.
 */
export async function completeMockPayment(
  depositId: string,
  outcome: "success" | "failure",
): Promise<ActionResult<null>> {
  if (!env.mockProviderEnabled || !isUuid(depositId)) {
    return { ok: false, error: toActionError(new ApiError(404, { code: "NOT_FOUND" })) };
  }
  try {
    const client = await api();
    const { data: deposit } = await unwrap(
      client.GET("/api/v1/deposits/{id}", { params: { path: { id: depositId } } }),
    );
    if (deposit.status !== "PENDING") return { ok: true, data: null }; // already settled: nothing to do

    const res = await fetch(
      `${env.apiBaseUrl}/dev/mock-provider/deposits/${encodeURIComponent(deposit.providerReference)}/complete?outcome=${outcome}`,
      { method: "POST", cache: "no-store" },
    );
    if (!res.ok) throw new ApiError(res.status, undefined);
    return { ok: true, data: null };
  } catch (err) {
    return { ok: false, error: toActionError(err) };
  }
}
