import type { NextRequest } from "next/server";

import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";

/** Next page of reconciliation runs (SUPPORT and ADMIN). */
export async function GET(request: NextRequest) {
  const cursor = request.nextUrl.searchParams.get("cursor") ?? undefined;
  return bffJson(async () => {
    const client = await api();
    const { data } = await unwrap(
      client.GET("/api/v1/admin/reconciliation/runs", { params: { query: { cursor, limit: 20 } } }),
      { on401: "throw" },
    );
    return data;
  });
}
