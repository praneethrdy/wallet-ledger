import { describe, expect, it } from "vitest";

import { ApiError, GENERIC_MESSAGE, messageFor, toActionError } from "./errors";

describe("ApiError", () => {
  it("takes code, detail, correlationId and field errors from the problem body", () => {
    const err = new ApiError(422, {
      status: 422,
      code: "INSUFFICIENT_FUNDS",
      detail: "Insufficient funds in source wallet",
      correlationId: "abc-123",
    });
    expect(err.code).toBe("INSUFFICIENT_FUNDS");
    expect(err.detail).toBe("Insufficient funds in source wallet");
    expect(err.unexpected).toBe(false);
  });

  it("derives a code from the status when there is no problem body", () => {
    expect(new ApiError(500, undefined).code).toBe("INTERNAL_ERROR");
    expect(new ApiError(404, undefined).code).toBe("NOT_FOUND");
    expect(new ApiError(0, undefined).code).toBe("NETWORK_ERROR");
  });

  it("treats a proxy's 502/503/504 (no problem body) as the backend being unreachable", () => {
    for (const status of [502, 503, 504]) {
      const err = new ApiError(status, undefined);
      expect(err.code).toBe("NETWORK_ERROR");
      expect(toActionError(err).message).toMatch(/waking up/);
    }
    // A 503 the backend itself sends (with a problem body) keeps its own code.
    expect(new ApiError(503, { code: "SOMETHING_SPECIFIC" }).code).toBe("SOMETHING_SPECIFIC");
  });

  it("treats 5xx and unknown codes as unexpected", () => {
    expect(new ApiError(500, { code: "INTERNAL_ERROR" }).unexpected).toBe(true);
    expect(new ApiError(422, { code: "SOMETHING_NEW" }).unexpected).toBe(true);
  });
});

describe("messageFor", () => {
  it("maps every business code the backend can send to friendly copy", () => {
    for (const code of [
      "INSUFFICIENT_FUNDS",
      "WALLET_FROZEN",
      "CURRENCY_MISMATCH",
      "SAME_WALLET_TRANSFER",
      "ALREADY_REVERSED",
      "REVERSAL_NOT_ALLOWED",
      "INSUFFICIENT_FUNDS_FOR_REVERSAL",
      "IDEMPOTENCY_KEY_REUSED",
      "IDEMPOTENCY_REQUEST_IN_PROGRESS",
      "EMAIL_ALREADY_REGISTERED",
    ]) {
      const message = messageFor(code);
      expect(message, code).not.toBe(GENERIC_MESSAGE);
      expect(message, code).not.toContain("_"); // never leak a raw code
    }
  });

  it("falls back to a generic message for unknown codes", () => {
    expect(messageFor("SOMETHING_NEW")).toBe(GENERIC_MESSAGE);
  });

  it("lets a screen override a message", () => {
    expect(messageFor("NOT_FOUND", { NOT_FOUND: "No wallet with that ID" })).toBe("No wallet with that ID");
  });
});

describe("toActionError", () => {
  it("keeps business errors friendly and hides the correlationId", () => {
    const e = toActionError(new ApiError(422, { code: "WALLET_FROZEN", correlationId: "c-1" }));
    expect(e).toEqual({
      code: "WALLET_FROZEN",
      message: messageFor("WALLET_FROZEN"),
      fieldErrors: {},
      correlationId: undefined,
    });
  });

  it("exposes the correlationId for unexpected errors so users can quote it", () => {
    const e = toActionError(new ApiError(500, { code: "INTERNAL_ERROR", correlationId: "c-2" }));
    expect(e.message).toBe(GENERIC_MESSAGE);
    expect(e.correlationId).toBe("c-2");
  });

  it("maps validation errors to fields (first message per field wins)", () => {
    const e = toActionError(
      new ApiError(400, {
        code: "VALIDATION_FAILED",
        errors: [
          { field: "amount", message: "must be greater than 0" },
          { field: "amount", message: "second message" },
          { field: "note", message: "size must be between 0 and 140" },
        ],
      }),
    );
    expect(e.fieldErrors).toEqual({ amount: "Must be greater than 0", note: "Size must be between 0 and 140" });
  });

  it("never leaks a non-API error's message", () => {
    const e = toActionError(new Error("ECONNREFUSED 10.0.0.5:8080"));
    expect(e.message).toBe(GENERIC_MESSAGE);
    expect(JSON.stringify(e)).not.toContain("ECONNREFUSED");
  });
});
