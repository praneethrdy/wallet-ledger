import { sealData, unsealData } from "iron-session";

/**
 * Session contents, sealed (encrypted + signed) into one HttpOnly cookie.
 * The tokens never reach browser JavaScript; the browser only holds the opaque seal.
 * Shared by the proxy (refresh) and server code (reads) so both use one format.
 */
export type SessionUser = {
  id: string;
  email: string;
  role: "CUSTOMER" | "SUPPORT" | "ADMIN";
  status: "ACTIVE" | "FROZEN";
};

export type SessionData = {
  accessToken: string;
  refreshToken: string;
  /** Epoch ms when the access token expires. */
  accessExpiresAt: number;
  user: SessionUser;
};

export const SESSION_COOKIE = "wl_session";

/** Matches the backend's 7-day refresh-token lifetime. */
export const SESSION_TTL_SECONDS = 7 * 24 * 60 * 60;

export const sessionCookieOptions = (production: boolean) =>
  ({
    httpOnly: true,
    secure: production,
    sameSite: "lax",
    path: "/",
    maxAge: SESSION_TTL_SECONDS,
  }) as const;

export function seal(data: SessionData, password: string): Promise<string> {
  return sealData(data, { password, ttl: SESSION_TTL_SECONDS });
}

/** Returns null for a missing, tampered, expired or malformed cookie. */
export async function unseal(cookie: string | undefined, password: string): Promise<SessionData | null> {
  if (!cookie) return null;
  try {
    const data = await unsealData<Partial<SessionData>>(cookie, { password, ttl: SESSION_TTL_SECONDS });
    if (!data.accessToken || !data.refreshToken || !data.accessExpiresAt || !data.user) return null;
    return data as SessionData;
  } catch {
    return null;
  }
}

/** Builds session data from the backend's TokenResponse. */
export function sessionFromTokens(tokens: {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: SessionUser;
}): SessionData {
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    accessExpiresAt: Date.now() + tokens.expiresIn * 1000,
    user: tokens.user,
  };
}

/** Where each role lands after signing in (and when opening "/"). */
export function homeFor(role: SessionUser["role"]): string {
  switch (role) {
    case "ADMIN":
      return "/admin";
    case "SUPPORT":
      return "/support/users";
    default:
      return "/dashboard";
  }
}
