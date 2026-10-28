"use server";

import { z } from "zod";

import { api, unwrap } from "@/lib/api/client";
import { idempotencyHeader } from "@/lib/api/idempotency";
import { toActionError, type ActionResult } from "@/lib/errors";

const newWallet = z.object({
  name: z.string().trim().min(1, "Give the wallet a name").max(60, "Use at most 60 characters"),
  currency: z.enum(["INR", "USD", "EUR"]),
});

export async function createWallet(form: FormData): Promise<ActionResult<{ id: string }>> {
  const parsed = newWallet.safeParse({ name: form.get("name"), currency: form.get("currency") });
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
  try {
    const client = await api();
    const { data, replayed } = await unwrap(
      client.POST("/api/v1/wallets", { params: { header: idempotencyHeader(form) }, body: parsed.data }),
    );
    return { ok: true, data: { id: data.id }, replayed };
  } catch (err) {
    return { ok: false, error: toActionError(err) };
  }
}
