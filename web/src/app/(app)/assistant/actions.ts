"use server";

import { z } from "zod";

import { api, get } from "@/lib/api/client";
import { runAssistant } from "@/lib/assistant/agent";
import { createDailyLimiter } from "@/lib/assistant/limits";
import { AssistantError, openAiCompatibleChat } from "@/lib/assistant/llm";
import { createTools, type Backend } from "@/lib/assistant/tools";
import { requireUser } from "@/lib/auth/current-user";
import { env } from "@/lib/env";
import { ApiError } from "@/lib/errors";
import { istDay } from "@/lib/format";

export type AskResult = { ok: true; answer: string } | { ok: false; error: string };

const input = z.object({
  question: z.string().trim().min(1).max(500),
  history: z.array(z.object({ role: z.enum(["user", "assistant"]), content: z.string().max(2000) })).max(10),
});

// Per server process (the deployment runs one); see limits.ts.
const limiter = createDailyLimiter(env.assistant?.dailyLimit ?? 20);

/**
 * One question to "Ask your wallet". The model only ever sees what the read-only tools return,
 * and the tools call the backend as this user, so it can read their wallet and nothing else.
 */
export async function askAssistant(raw: unknown): Promise<AskResult> {
  const config = env.assistant;
  if (!config) return { ok: false, error: "The assistant isn't switched on." };

  const user = await requireUser();
  if (user.role !== "CUSTOMER") return { ok: false, error: "The assistant is for customer wallets." };

  const parsed = input.safeParse(raw);
  if (!parsed.success) return { ok: false, error: "Ask a question of up to 500 characters." };
  if (!limiter.take(user.id)) {
    return {
      ok: false,
      error: `You've asked ${config.dailyLimit} questions today, the daily limit. Try again tomorrow.`,
    };
  }

  const client = await api();
  const backend: Backend = {
    wallets: () => get(client.GET("/api/v1/wallets")),
    transactions: (query) => get(client.GET("/api/v1/transactions", { params: { query } })),
  };

  try {
    const { answer } = await runAssistant({
      question: parsed.data.question,
      history: parsed.data.history,
      chat: openAiCompatibleChat(config),
      tools: createTools(backend),
      today: istDay(new Date().toISOString()),
    });
    return { ok: true, answer };
  } catch (err) {
    if (err instanceof AssistantError) {
      console.warn(`Assistant: ${err.kind}: ${err.message}`);
      return {
        ok: false,
        error:
          err.kind === "rate_limited"
            ? "The AI service is busy right now (its free quota). Try again in a minute."
            : err.kind === "timeout"
              ? "The AI service took too long. Try again."
              : "The AI service isn't responding. Try again in a moment.",
      };
    }
    if (err instanceof ApiError) {
      return { ok: false, error: "Couldn't read your wallet just now. Try again in a moment." };
    }
    throw err;
  }
}
