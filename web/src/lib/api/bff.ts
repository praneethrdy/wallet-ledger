import "server-only";

import { NextResponse } from "next/server";

import { ApiError, messageFor } from "@/lib/errors";

/**
 * Wraps a read-only BFF route handler: the browser calls /bff/..., the handler calls the
 * backend with the session's token. Errors come back as a small JSON body the client can map;
 * never as a redirect (a fetch() can't follow a sign-in page).
 */
export async function bffJson<T>(load: () => Promise<T>): Promise<NextResponse> {
  try {
    return NextResponse.json(await load(), { headers: { "Cache-Control": "no-store" } });
  } catch (err) {
    const e = err instanceof ApiError ? err : new ApiError(500, undefined);
    return NextResponse.json(
      { code: e.code, message: messageFor(e.code), correlationId: e.unexpected ? e.correlationId : undefined },
      { status: e.status || 502, headers: { "Cache-Control": "no-store" } },
    );
  }
}

/** Client-side counterpart: fetch a BFF route; on 401 go to sign-in, otherwise throw the message. */
export type BffError = { code: string; message: string; correlationId?: string };
