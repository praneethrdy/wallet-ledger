import { NextResponse, type NextRequest } from "next/server";

import { env } from "@/lib/env";
import {
  SESSION_COOKIE,
  homeFor,
  seal,
  sessionCookieOptions,
  unseal,
  type SessionData,
  type SessionUser,
} from "@/lib/session/data";
import { refreshSession } from "@/lib/session/refresh";

/** Refresh when the access token has less than this long left. */
const REFRESH_AHEAD_MS = 60_000;

const PUBLIC_PAGES = ["/login", "/register", "/design"];

/** Area prefix -> roles allowed to open it. The backend still enforces everything. */
const AREAS: { prefix: string; roles: SessionUser["role"][] }[] = [
  { prefix: "/admin/reconciliation", roles: ["SUPPORT", "ADMIN"] }, // read-only for SUPPORT; "Run now" is ADMIN
  { prefix: "/admin", roles: ["ADMIN"] },
  { prefix: "/support", roles: ["SUPPORT", "ADMIN"] },
  { prefix: "/dashboard", roles: ["CUSTOMER", "ADMIN"] },
  { prefix: "/wallets", roles: ["CUSTOMER", "ADMIN"] },
  { prefix: "/transactions", roles: ["CUSTOMER", "ADMIN"] },
  { prefix: "/transfer", roles: ["CUSTOMER"] },
  { prefix: "/deposit", roles: ["CUSTOMER"] },
  { prefix: "/settings", roles: ["CUSTOMER", "SUPPORT", "ADMIN"] },
  { prefix: "/bff", roles: ["CUSTOMER", "SUPPORT", "ADMIN"] },
];

/**
 * Runs before every page, Server Action and BFF route:
 *  1. refreshes the access token shortly before it expires (the only place a refresh may happen,
 *     because pages can't write cookies while rendering), and passes the new session on to this
 *     request AND back to the browser;
 *  2. sends anonymous users to /login and signed-in users away from /login;
 *  3. keeps each role inside the areas it can use.
 */
export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  let session = await unseal(request.cookies.get(SESSION_COOKIE)?.value, env.sessionSecret);
  let rotatedSeal: string | undefined;
  let sessionEnded = false;

  if (session && session.accessExpiresAt - Date.now() < REFRESH_AHEAD_MS) {
    const result = await refreshSession(env.apiBaseUrl, session.refreshToken);
    if (result.ok) {
      session = result.session;
      rotatedSeal = await seal(session, env.sessionSecret);
      request.cookies.set(SESSION_COOKIE, rotatedSeal); // visible to this request's render/action
    } else if (result.status === 401 || result.status === 400) {
      session = null; // refresh token expired or revoked: the session is over
      sessionEnded = true;
      request.cookies.delete(SESSION_COOKIE);
    }
    // Network/5xx: keep the current session; the access token may still have seconds left.
  }

  const redirectTo = decide(pathname, search, session, sessionEnded);
  const response = redirectTo
    ? pathname.startsWith("/bff/")
      ? NextResponse.json({ code: "UNAUTHORIZED" }, { status: 401 })
      : NextResponse.redirect(new URL(redirectTo, request.url))
    : NextResponse.next({ request: { headers: request.headers } });

  if (rotatedSeal) {
    response.cookies.set(SESSION_COOKIE, rotatedSeal, sessionCookieOptions(env.production));
  } else if (sessionEnded) {
    response.cookies.delete(SESSION_COOKIE);
  }
  return response;
}

function decide(pathname: string, search: string, session: SessionData | null, ended: boolean): string | null {
  if (pathname === "/") {
    return session ? homeFor(session.user.role) : "/login";
  }
  if (PUBLIC_PAGES.some((p) => pathname === p || pathname.startsWith(`${p}/`))) {
    if (session && (pathname === "/login" || pathname === "/register")) return homeFor(session.user.role);
    return null;
  }

  const area = AREAS.find((a) => pathname === a.prefix || pathname.startsWith(`${a.prefix}/`));
  if (!area) return null; // unknown path: let Next render its 404

  if (!session) {
    const params = new URLSearchParams({ next: pathname + search });
    if (ended) params.set("expired", "1");
    return `/login?${params}`;
  }
  if (!area.roles.includes(session.user.role)) return homeFor(session.user.role);
  return null;
}

export const config = {
  // Everything except Next internals and static files.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico|txt|xml)$).*)"],
};
