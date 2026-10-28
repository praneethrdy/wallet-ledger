"use server";

import { z } from "zod";

import { api, unwrap, type Schemas } from "@/lib/api/client";
import { idempotencyHeader } from "@/lib/api/idempotency";
import { toActionError, type ActionResult } from "@/lib/errors";
import { isUuid } from "@/lib/format";
import { moneyParseMessage, parseMoney } from "@/lib/money";

const input = z.object({
  fromWalletId: z.string().refine(isUuid, "Choose a wallet to send from"),
  toWalletId: z.string().trim().refine(isUuid, "Paste a wallet ID like 01a181c2-42c4-7ade-97e5-4557181301dc"),
  amount: z.string(),
  currency: z.enum(["INR", "USD", "EUR"]),
  note: z.string().trim().max(140, "Use at most 140 characters").optional(),
});

/** Field-specific placement for business errors, so they show next to the right input. */
const FIELD_FOR_CODE: Record<string, string> = {
  INSUFFICIENT_FUNDS: "amount",
  SAME_WALLET_TRANSFER: "toWalletId",
  CURRENCY_MISMATCH: "toWalletId",
  NOT_FOUND: "toWalletId",
};

export async function transfer(form: FormData): Promise<ActionResult<Schemas["TransactionDetail"]>> {
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
  const { fromWalletId, toWalletId, currency, note } = parsed.data;
  // Re-validated on the server: never trust the browser's parsing.
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
      client.POST("/api/v1/transfers", {
        params: { header: idempotencyHeader(form) },
        body: { fromWalletId, toWalletId, amount: amount.minor, currency, note: note || undefined },
      }),
    );
    return { ok: true, data, replayed };
  } catch (err) {
    const error = toActionError(err, {
      NOT_FOUND: "No wallet with that ID. Check it and try again.",
      CURRENCY_MISMATCH: "That wallet uses a different currency.",
    });
    const field = FIELD_FOR_CODE[error.code];
    if (field && !error.fieldErrors[field]) error.fieldErrors[field] = error.message;
    return { ok: false, error };
  }
}
