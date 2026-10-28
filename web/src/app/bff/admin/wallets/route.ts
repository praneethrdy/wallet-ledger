import type { NextRequest } from "next/server";

import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";

import { adminWalletQuery } from "@/app/(app)/admin/wallets/query";

/** Next page of the staff wallet search. */
export async function GET(request: NextRequest) {
  const params = Object.fromEntries(request.nextUrl.searchParams);
  return bffJson(async () => {
    const client = await api();
    const { data } = await unwrap(
      client.GET("/api/v1/admin/wallets", {
        params: { query: { ...adminWalletQuery(params), cursor: params.cursor, limit: 25 } },
      }),
      { on401: "throw" },
    );
    return data;
  });
}
