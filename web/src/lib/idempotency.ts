"use client";

import { useCallback, useRef } from "react";

/**
 * One Idempotency-Key per user intent.
 *
 * The key is created lazily on the first submit and REUSED for every re-submit of the same
 * payload (double-click, browser retry, slow network), so the backend replays the original
 * result instead of moving money twice. It is discarded (`reset`) when the user edits the form
 * or once a definite answer arrives, so the next intent gets a fresh key. Without that reset,
 * correcting an amount after an error would reuse the key with a different body and the backend
 * would answer IDEMPOTENCY_KEY_REUSED.
 */
export function useIdempotencyKey() {
  const key = useRef<string | null>(null);
  const get = useCallback(() => (key.current ??= crypto.randomUUID()), []);
  const reset = useCallback(() => {
    key.current = null;
  }, []);
  return { get, reset };
}
