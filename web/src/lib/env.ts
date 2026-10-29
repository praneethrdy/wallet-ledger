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
  const apiKey = process.env.LLM_API_KEY?.trim();
  const model = process.env.LLM_MODEL?.trim();
  if (!apiKey || !model) return undefined;
  const baseUrl = (
    process.env.LLM_BASE_URL?.trim() || "https://generativelanguage.googleapis.com/v1beta/openai"
  ).replace(/\/+$/, "");
  const limit = Number.parseInt(process.env.ASSISTANT_DAILY_LIMIT ?? "", 10);
  return {
    apiKey,
    model,
    baseUrl,
    dailyLimit: Number.isFinite(limit) && limit > 0 ? limit : 20,
    providerName: new URL(baseUrl).hostname.includes("openrouter")
      ? "OpenRouter"
      : new URL(baseUrl).hostname.includes("x.ai")
        ? "xAI"
        : new URL(baseUrl).hostname.includes("googleapis")
          ? "Google Gemini"
          : new URL(baseUrl).hostname,
  };
}

export const env = read();
