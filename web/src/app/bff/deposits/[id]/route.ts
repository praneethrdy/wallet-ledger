import { api, unwrap } from "@/lib/api/client";
import { bffJson } from "@/lib/api/bff";
import { ApiError } from "@/lib/errors";
import { isUuid } from "@/lib/format";

/** A deposit's current status, polled by the deposit screen until it settles. */
export async function GET(_request: Request, ctx: RouteContext<"/bff/deposits/[id]">) {
  const { id } = await ctx.params;
  return bffJson(async () => {
    if (!isUuid(id)) throw new ApiError(404, { code: "NOT_FOUND" });
    const client = await api();
    const { data } = await unwrap(client.GET("/api/v1/deposits/{id}", { params: { path: { id } } }), {
      on401: "throw",
    });
    return data;
  });
}
