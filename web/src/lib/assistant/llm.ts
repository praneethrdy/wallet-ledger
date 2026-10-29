/**
 * Minimal client for the "OpenAI-compatible" chat completions API with tool calling, which
 * Google Gemini, OpenRouter, xAI (Grok) and OpenAI all speak. The provider is chosen by config:
 * base URL + API key + model. Server-side only (the key must never reach the browser).
 */

export type ToolCall = { id: string; type: "function"; function: { name: string; arguments: string } };

export type ChatMessage =
  | { role: "system" | "user"; content: string }
  | { role: "assistant"; content: string | null; tool_calls?: ToolCall[] }
  | { role: "tool"; tool_call_id: string; name: string; content: string };

export type ToolDefinition = {
  type: "function";
  function: { name: string; description: string; parameters: Record<string, unknown> };
};

export type ChatReply = { content: string | null; tool_calls?: ToolCall[] };

/** One model call: the conversation so far in, the model's next message out. */
export type ChatFn = (messages: ChatMessage[], tools: ToolDefinition[]) => Promise<ChatReply>;

export type LlmConfig = { baseUrl: string; apiKey: string; model: string; timeoutMs?: number };

/** A failure talking to the provider, classified so the UI can say something useful. */
export class AssistantError extends Error {
  constructor(
    readonly kind: "rate_limited" | "provider" | "timeout",
    message: string,
  ) {
    super(message);
    this.name = "AssistantError";
  }
}

export function openAiCompatibleChat(config: LlmConfig, fetchImpl: typeof fetch = fetch): ChatFn {
  const url = `${config.baseUrl.replace(/\/+$/, "")}/chat/completions`;
  return async (messages, tools) => {
    let res: Response;
    try {
      res = await fetchImpl(url, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${config.apiKey}` },
        body: JSON.stringify({
          model: config.model,
          messages,
          tools,
          tool_choice: "auto",
          temperature: 0.2,
          max_tokens: 800,
        }),
        signal: AbortSignal.timeout(config.timeoutMs ?? 30_000),
        cache: "no-store",
      });
    } catch (err) {
      const timedOut = err instanceof Error && (err.name === "TimeoutError" || err.name === "AbortError");
      throw new AssistantError(timedOut ? "timeout" : "provider", timedOut ? "Model timed out" : "Model unreachable");
    }
    if (res.status === 429) throw new AssistantError("rate_limited", "Model rate limit reached");
    if (!res.ok) {
      // The body can echo request details; log the status, not the payload.
      throw new AssistantError("provider", `Model returned HTTP ${res.status}`);
    }
    const body = (await res.json().catch(() => null)) as {
      choices?: { message?: { content?: unknown; tool_calls?: ToolCall[] } }[];
    } | null;
    const message = body?.choices?.[0]?.message;
    if (!message) throw new AssistantError("provider", "Model returned no message");
    return {
      content: typeof message.content === "string" ? message.content : null,
      tool_calls: Array.isArray(message.tool_calls) && message.tool_calls.length ? message.tool_calls : undefined,
    };
  };
}
