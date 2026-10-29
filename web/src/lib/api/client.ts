import "server-only";

import createClient, { type Client } from "openapi-fetch";
import { redirect } from "next/navigation";

import { env } from "@/lib/env";
import { ApiError, type Problem } from "@/lib/errors";
import { getSession } from "@/lib/session/session";

import type { components, paths } from "./schema";

export type Schemas = components["schemas"];

/**
 * The ONLY way the web app talks to the backend: server-side, typed from openapi.json,
 * with the access token taken from the encrypted session cookie.
 */
/**
 * Longest we wait for the backend. Normal calls take well under a second (a login ~2-5 s); a free
 * instance waking up takes a minute or more, and the user is better off told so than left hanging.
 */
const BACKEND_TIMEOUT_MS = 30_000;

function makeClient(accessToken?: string): Client<paths> {
  return createClient<paths>({
    baseUrl: env.apiBaseUrl,
    headers: {
      Accept: "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    cache: "no-store",
    // A timeout throws, which unwrap() reports as NETWORK_ERROR ("the server is waking up").
    fetch: (request: Request) => fetch(request, { signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS) }),
  });
}

/** Unauthenticated client (login, register, refresh, logout). */
export function publicApi(): Client<paths> {
  return makeClient();
}

/** Client authenticated as the signed-in user; sends anonymous users to /login. */
export async function api(): Promise<Client<paths>> {
  const session = await getSession();
  if (!session) redirect("/login");
  return makeClient(session.accessToken);
}

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/**
 * Turns an openapi-fetch result into data, or throws ApiError built from the RFC 7807 problem.
 * A 401 while rendering means the session is gone (the proxy refreshes ahead of expiry), so
 * the user is sent to sign in again.
 */
export async function unwrap<T>(call: Promise<FetchResult<T>>, options: { on401?: "redirect" | "throw" } = {}) {
  let result: FetchResult<T>;
  try {
    result = await call;
  } catch {
    throw new ApiError(0, undefined); // backend unreachable
  }
  if (result.response.ok) {
    return { data: result.data as T, replayed: result.response.headers.get("Idempotent-Replayed") === "true" };
  }
  if (result.response.status === 401 && (options.on401 ?? "redirect") === "redirect") {
    redirect("/login?expired=1");
  }
  throw new ApiError(result.response.status, result.error as Problem | undefined);
}

/** Convenience for reads: just the data. */
export async function get<T>(call: Promise<FetchResult<T>>): Promise<T> {
  return (await unwrap(call)).data;
}
