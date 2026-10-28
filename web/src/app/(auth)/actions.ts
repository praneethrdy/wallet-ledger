"use server";

import { redirect } from "next/navigation";
import { z } from "zod";

import { api, publicApi, unwrap } from "@/lib/api/client";
import { ApiError, toActionError, type ActionError } from "@/lib/errors";
import { homeFor, sessionFromTokens, type SessionUser } from "@/lib/session/data";
import { clearSession, getSession, saveSession } from "@/lib/session/session";

export type AuthFormState = { error?: ActionError; email?: string } | undefined;

const credentials = z.object({
  email: z.string().trim().min(1, "Enter your email").max(254),
  password: z.string().min(1, "Enter your password").max(128),
});

/** Same message for unknown email and wrong password: no account enumeration (backend ADR-008). */
const LOGIN_FAILED = "Invalid email or password.";

export async function login(_prev: AuthFormState, form: FormData): Promise<AuthFormState> {
  const parsed = credentials.safeParse({ email: form.get("email"), password: form.get("password") });
  if (!parsed.success) return { error: fieldError(parsed.error), email: String(form.get("email") ?? "") };

  let user: SessionUser;
  try {
    const { data } = await unwrap(publicApi().POST("/api/v1/auth/login", { body: parsed.data }), { on401: "throw" });
    const session = sessionFromTokens(data);
    await saveSession(session);
    user = session.user;
  } catch (err) {
    const error = toActionError(err, { UNAUTHORIZED: LOGIN_FAILED, VALIDATION_FAILED: LOGIN_FAILED });
    return { error, email: parsed.data.email };
  }
  redirect(safeNext(form.get("next")) ?? homeFor(user.role));
}

const registration = z.object({
  email: z.email("Enter a valid email").max(254),
  password: z.string().min(10, "Use at least 10 characters").max(128, "Use at most 128 characters"),
});

export async function register(_prev: AuthFormState, form: FormData): Promise<AuthFormState> {
  const parsed = registration.safeParse({ email: form.get("email"), password: form.get("password") });
  if (!parsed.success) return { error: fieldError(parsed.error), email: String(form.get("email") ?? "") };

  let user: SessionUser;
  try {
    await unwrap(publicApi().POST("/api/v1/auth/register", { body: parsed.data }), { on401: "throw" });
    // Registered: sign straight in with the same credentials.
    const { data } = await unwrap(publicApi().POST("/api/v1/auth/login", { body: parsed.data }), { on401: "throw" });
    const session = sessionFromTokens(data);
    await saveSession(session);
    user = session.user;
  } catch (err) {
    return { error: toActionError(err), email: parsed.data.email };
  }
  redirect(homeFor(user.role));
}

/** Ends this browser's session (revokes its refresh-token family on the backend). */
export async function logout(): Promise<void> {
  const session = await getSession();
  if (session) {
    // Best effort: even if the backend is unreachable, the cookie is cleared below.
    await publicApi()
      .POST("/api/v1/auth/logout", { body: { refreshToken: session.refreshToken } })
      .catch(() => undefined);
  }
  await clearSession();
  redirect("/login");
}

/** Ends every session of this user, on all devices. */
export async function logoutEverywhere(): Promise<void> {
  try {
    await unwrap((await api()).POST("/api/v1/auth/logout-all"), { on401: "throw" });
  } catch (err) {
    if (!(err instanceof ApiError && err.status === 401)) throw err;
  }
  await clearSession();
  redirect("/login?signedOut=all");
}

/** Only same-site relative paths ("/wallets?x=1"), never "//evil.example" or absolute URLs. */
function safeNext(value: FormDataEntryValue | null): string | null {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return null;
  }
  return value;
}

function fieldError(error: z.ZodError): ActionError {
  const fieldErrors: Record<string, string> = {};
  for (const issue of error.issues) {
    const field = String(issue.path[0] ?? "form");
    fieldErrors[field] ??= issue.message;
  }
  return { code: "VALIDATION_FAILED", message: "Please check the highlighted fields.", fieldErrors };
}
