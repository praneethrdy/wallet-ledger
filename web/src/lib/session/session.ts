import "server-only";

import { cookies } from "next/headers";
import { cache } from "react";

import { env } from "@/lib/env";

import { SESSION_COOKIE, seal, sessionCookieOptions, unseal, type SessionData } from "./data";

/**
 * Reads the session for this request (once per request, thanks to React's cache()).
 * With Cache Components, call this only from components inside a <Suspense> boundary,
 * from Server Actions, or from Route Handlers.
 */
export const getSession = cache(async (): Promise<SessionData | null> => {
  const jar = await cookies();
  return unseal(jar.get(SESSION_COOKIE)?.value, env.sessionSecret);
});

/** Writes the session. Only allowed in Server Actions and Route Handlers. */
export async function saveSession(data: SessionData): Promise<void> {
  const jar = await cookies();
  jar.set(SESSION_COOKIE, await seal(data, env.sessionSecret), sessionCookieOptions(env.production));
}

/** Deletes the session cookie. Only allowed in Server Actions and Route Handlers. */
export async function clearSession(): Promise<void> {
  const jar = await cookies();
  jar.delete(SESSION_COOKIE);
}
