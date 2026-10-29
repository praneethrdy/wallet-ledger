// @vitest-environment jsdom
import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { useIdempotencyKey } from "./idempotency";

describe("useIdempotencyKey", () => {
  it("reuses one key for every re-submit of the same intent (double-click, retry)", () => {
    const { result } = renderHook(() => useIdempotencyKey());
    const first = result.current.get();
    expect(first).toMatch(/^[0-9a-f-]{36}$/);
    expect(result.current.get()).toBe(first);
    expect(result.current.get()).toBe(first);
  });

  it("starts a new intent after reset (form edited, or a definite answer arrived)", () => {
    const { result } = renderHook(() => useIdempotencyKey());
    const first = result.current.get();
    result.current.reset();
    const second = result.current.get();
    expect(second).not.toBe(first);
    expect(result.current.get()).toBe(second);
  });

  it("keeps the key across re-renders", () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey());
    const first = result.current.get();
    rerender();
    expect(result.current.get()).toBe(first);
  });

  it("gives separate forms separate keys", () => {
    const a = renderHook(() => useIdempotencyKey()).result.current.get();
    const b = renderHook(() => useIdempotencyKey()).result.current.get();
    expect(a).not.toBe(b);
  });
});
