"use client";

import type { BffError } from "@/lib/api/bff";

/** Browser-side fetch of a /bff route. 401 means the session ended: go sign in again. */
export async function bffGet<T>(path: string): Promise<T> {
  const res = await fetch(path, { headers: { Accept: "application/json" }, cache: "no-store" });
  if (res.status === 401) {
    // A full page load on purpose: the session is gone, so drop all client state too.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign(`/login?expired=1&next=${encodeURIComponent(window.location.pathname)}`);
    throw new Error("Session ended");
  }
  const body = (await res.json().catch(() => ({}))) as T & Partial<BffError>;
  if (!res.ok) {
    const error = new Error(body.message ?? "Something went wrong.") as Error & { correlationId?: string };
    error.correlationId = body.correlationId;
    throw error;
  }
  return body;
}
