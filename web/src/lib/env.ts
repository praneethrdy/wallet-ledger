import "server-only";

/**
 * Server-side configuration, validated once. Nothing here is NEXT_PUBLIC_*: the browser never
 * learns the backend URL or the session secret.
 */
function read() {
  const apiBaseUrl = (process.env.API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");
  const sessionSecret = process.env.SESSION_SECRET ?? "";
  if (sessionSecret.length < 32) {
    throw new Error("SESSION_SECRET must be set to at least 32 characters (see web/.env.example)");
  }
  return {
    apiBaseUrl,
    sessionSecret,
    mockProviderEnabled: process.env.MOCK_PROVIDER_ENABLED === "true",
    // "Add money" completes with a simulated payment (the backend's DEMO_PAYMENTS_ENABLED must match).
    demoPaymentsEnabled: process.env.DEMO_PAYMENTS_ENABLED === "true" || process.env.MOCK_PROVIDER_ENABLED === "true",
    // A public demo customer (production), shown on /login so visitors needn't register.
    demoLogin:
      process.env.DEMO_LOGIN_EMAIL && process.env.DEMO_LOGIN_PASSWORD
        ? { email: process.env.DEMO_LOGIN_EMAIL, password: process.env.DEMO_LOGIN_PASSWORD }
        : undefined,
    assistant: readAssistant(),
    production: process.env.NODE_ENV === "production",
  };
}

/**
 * "Ask your wallet": any OpenAI-compatible chat API with tool calling. Defaults to Google Gemini's;
 * point LLM_BASE_URL at OpenRouter (https://openrouter.ai/api/v1) or xAI (https://api.x.ai/v1)
 * instead. Off (and hidden) unless both a key and a model are set.
 */
function readAssistant() {
  const primary = readProvider("LLM", "https://generativelanguage.googleapis.com/v1beta/openai");
  if (!primary) return undefined;
  const limit = Number.parseInt(process.env.ASSISTANT_DAILY_LIMIT ?? "", 10);
  return {
    ...primary,
    // Used when the primary provider fails (overloaded, quota, timeout, error). Optional.
    fallback: readProvider("LLM_FALLBACK", "https://openrouter.ai/api/v1"),
    dailyLimit: Number.isFinite(limit) && limit > 0 ? limit : 20,
  };
}

/** {PREFIX}_API_KEY + {PREFIX}_MODEL (+ optional {PREFIX}_BASE_URL); undefined unless key and model are set. */
function readProvider(prefix: string, defaultBaseUrl: string) {
  const apiKey = process.env[`${prefix}_API_KEY`]?.trim();
  const model = process.env[`${prefix}_MODEL`]?.trim();
  if (!apiKey || !model) return undefined;
  const baseUrl = (process.env[`${prefix}_BASE_URL`]?.trim() || defaultBaseUrl).replace(/\/+$/, "");
  return { apiKey, model, baseUrl, providerName: providerName(baseUrl) };
}

function providerName(baseUrl: string): string {
  const host = new URL(baseUrl).hostname;
  if (host.includes("openrouter")) return "OpenRouter";
  if (host.includes("x.ai")) return "xAI";
  if (host.includes("googleapis")) return "Google Gemini";
  return host;
}

export const env = read();
