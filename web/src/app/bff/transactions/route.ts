import type { NextRequest } from "next/server";

import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";
import { parseTxFilters, txApiQuery } from "@/lib/tx-filters";

/** Next page of the caller's transactions for the given filters. */
export async function GET(request: NextRequest) {
  const params = Object.fromEntries(request.nextUrl.searchParams);
  const cursor = request.nextUrl.searchParams.get("cursor") ?? undefined;
  return bffJson(async () => {
    const client = await api();
    const { data } = await unwrap(
      client.GET("/api/v1/transactions", {
        params: { query: { ...txApiQuery(parseTxFilters(params)), cursor, limit: 25 } },
      }),
      { on401: "throw" },
    );
    return data;
  });
}
