import type { NextRequest } from "next/server";

import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";

import { webhookStatus } from "@/app/(app)/support/webhook-events/status";

/** Next page of inbound webhook events. */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  return bffJson(async () => {
    const client = await api();
    const { data } = await unwrap(
      client.GET("/api/v1/support/webhook-events", {
        params: {
          query: {
            status: webhookStatus(params.get("status") ?? undefined),
            cursor: params.get("cursor") ?? undefined,
            limit: 20,
          },
        },
      }),
      { on401: "throw" },
    );
    return data;
  });
}
