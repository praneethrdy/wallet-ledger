import "server-only";

import { ApiError } from "@/lib/errors";

/** Same rule as the backend: 1-255 visible ASCII characters. */
const KEY_FORMAT = /^[\x21-\x7E]{1,255}$/;

/**
 * Reads the Idempotency-Key a form submitted (generated in the browser per intent) and returns
 * the header object for openapi-fetch. A missing/garbled key is a client bug: refuse it.
 */
export function idempotencyHeader(form: FormData): { "Idempotency-Key": string } {
  const key = form.get("idempotencyKey");
  if (typeof key !== "string" || !KEY_FORMAT.test(key)) {
    throw new ApiError(400, { code: "VALIDATION_FAILED", detail: "Missing idempotency key" });
  }
  return { "Idempotency-Key": key };
}
