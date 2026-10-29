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
    // A public demo customer (production), shown on /login so visitors needn't register.
    demoLogin:
      process.env.DEMO_LOGIN_EMAIL && process.env.DEMO_LOGIN_PASSWORD
        ? { email: process.env.DEMO_LOGIN_EMAIL, password: process.env.DEMO_LOGIN_PASSWORD }
        : undefined,
    production: process.env.NODE_ENV === "production",
  };
}

export const env = read();
