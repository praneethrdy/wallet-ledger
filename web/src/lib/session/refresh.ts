import { sessionFromTokens, type SessionData, type SessionUser } from "./data";

/**
 * Access-token refresh with SINGLE-FLIGHT + a short GRACE window.
 *
 * The backend detects refresh-token reuse: presenting a refresh token that was already used
 * revokes the whole session family (ADR-008). Two things in a browser can easily cause that:
 *   - parallel requests (several RSC prefetches) all noticing the token is about to expire;
 *   - a request that left the browser before the new cookie arrived, still carrying the old seal.
 * So, per refresh token, exactly one backend call is made; concurrent callers share its promise,
 * and for GRACE_MS afterwards anyone presenting that same (now used) refresh token gets the
 * already-rotated session instead of calling the backend again.
 *
 * Scope: one Next.js server process. With several instances behind a load balancer, use sticky
 * sessions or move this map into a shared store (documented in web/README.md).
 */

const GRACE_MS = 60_000;

export type RefreshResult = { ok: true; session: SessionData } | { ok: false; status: number };

type Entry = { promise: Promise<RefreshResult>; settledAt?: number };
const inFlight = new Map<string, Entry>();

export function refreshSession(apiBaseUrl: string, refreshToken: string): Promise<RefreshResult> {
  sweep();
  const existing = inFlight.get(refreshToken);
  if (existing) return existing.promise;

  const entry: Entry = {
    promise: callBackend(apiBaseUrl, refreshToken).then((result) => {
      entry.settledAt = Date.now();
      if (!result.ok && result.status !== 401) {
        // Transient failure (network, 5xx): don't cache it, so the next request can retry.
        inFlight.delete(refreshToken);
      }
      return result;
    }),
  };
  inFlight.set(refreshToken, entry);
  return entry.promise;
}

async function callBackend(apiBaseUrl: string, refreshToken: string): Promise<RefreshResult> {
  try {
    const res = await fetch(`${apiBaseUrl}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ refreshToken }),
      cache: "no-store",
    });
    if (!res.ok) return { ok: false, status: res.status };
    const tokens = (await res.json()) as {
      accessToken: string;
      refreshToken: string;
      expiresIn: number;
      user: SessionUser;
    };
    return { ok: true, session: sessionFromTokens(tokens) };
  } catch {
    return { ok: false, status: 0 };
  }
}

function sweep() {
  const now = Date.now();
  for (const [key, entry] of inFlight) {
    if (entry.settledAt !== undefined && now - entry.settledAt > GRACE_MS) inFlight.delete(key);
  }
}

/** Test hook. */
export function _resetRefreshCacheForTests() {
  inFlight.clear();
}
