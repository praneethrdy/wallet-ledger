import type { NextRequest } from "next/server";

import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";
import { parseTxFilters, txApiQuery } from "@/lib/tx-filters";

/** Next page of the staff transaction search (audited by the backend like every support read). */
export async function GET(request: NextRequest) {
  const params = Object.fromEntries(request.nextUrl.searchParams);
  return bffJson(async () => {
    const client = await api();
    const { data } = await unwrap(
      client.GET("/api/v1/support/transactions", {
        params: { query: { ...txApiQuery(parseTxFilters(params)), cursor: params.cursor, limit: 25 } },
      }),
      { on401: "throw" },
    );
    return data;
  });
}
