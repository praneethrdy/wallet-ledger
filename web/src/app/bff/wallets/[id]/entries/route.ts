import type { NextRequest } from "next/server";

import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";
import { ApiError } from "@/lib/errors";
import { isUuid } from "@/lib/format";

/** Next page of a wallet's ledger entries (GET only: reads need no CSRF protection). */
export async function GET(request: NextRequest, ctx: RouteContext<"/bff/wallets/[id]/entries">) {
  const { id } = await ctx.params;
  const cursor = request.nextUrl.searchParams.get("cursor") ?? undefined;
  return bffJson(async () => {
    if (!isUuid(id)) throw new ApiError(404, { code: "NOT_FOUND" });
    const client = await api();
    const { data } = await unwrap(
      client.GET("/api/v1/wallets/{id}/entries", { params: { path: { id }, query: { cursor, limit: 25 } } }),
      { on401: "throw" },
    );
    return data;
  });
}
