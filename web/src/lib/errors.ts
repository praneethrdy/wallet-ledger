/**
 * The backend answers every error with an RFC 7807 problem: a stable `code`, a human `detail`,
 * a `correlationId` and, for validation, `errors[]`. The UI branches on `code` only. All
 * user-facing copy for those codes lives here, in one place.
 */

export type FieldError = { field: string; message: string };

export type Problem = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  correlationId?: string;
  errors?: FieldError[];
};

/** A failed backend call, carrying everything the UI needs to render it. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId?: string;
  readonly fieldErrors: FieldError[];
  readonly detail?: string;

  constructor(status: number, problem: Problem | undefined) {
    const code = problem?.code ?? codeForStatus(status);
    super(problem?.detail ?? problem?.title ?? code);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.detail = problem?.detail;
    this.correlationId = problem?.correlationId;
    this.fieldErrors = problem?.errors ?? [];
  }

  /** 5xx, network failures and anything we didn't anticipate. */
  get unexpected(): boolean {
    return this.status >= 500 || this.status === 0 || !(this.code in MESSAGES);
  }
}

/** For responses without a problem body (e.g. a proxy's HTML 502). */
function codeForStatus(status: number): string {
  if (status === 0) return "NETWORK_ERROR";
  if (status === 401) return "UNAUTHORIZED";
  if (status === 403) return "FORBIDDEN";
  if (status === 404) return "NOT_FOUND";
  if (status === 413) return "PAYLOAD_TOO_LARGE";
  if (status === 429) return "RATE_LIMITED";
  return status >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
}

export const GENERIC_MESSAGE = "Something went wrong on our side. Please try again in a moment.";

const MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: "Some details need fixing.",
  UNAUTHORIZED: "Your session has ended. Please sign in again.",
  FORBIDDEN: "You don't have permission to do that.",
  NOT_FOUND: "We couldn't find that.",
  WALLET_FROZEN: "This wallet is frozen, so money can't move in or out of it.",
  CURRENCY_MISMATCH: "Both wallets must use the same currency.",
  INSUFFICIENT_FUNDS: "Not enough balance in this wallet.",
  INSUFFICIENT_FUNDS_FOR_REVERSAL: "Can't reverse: the recipient no longer has enough balance to give it back.",
  SAME_WALLET_TRANSFER: "Choose a different wallet to send to.",
  IDEMPOTENCY_KEY_REQUIRED: GENERIC_MESSAGE,
  IDEMPOTENCY_KEY_REUSED: "This form was already used for a different request. Please reload and try again.",
  IDEMPOTENCY_REQUEST_IN_PROGRESS: "This request is still being processed. Please wait a moment.",
  ALREADY_REVERSED: "This transaction has already been reversed.",
  REVERSAL_NOT_ALLOWED: "A reversal can't itself be reversed.",
  EMAIL_ALREADY_REGISTERED: "An account with this email already exists. Try signing in.",
  INVALID_SIGNATURE: GENERIC_MESSAGE,
  RATE_LIMITED: "Too many attempts. Please wait a moment and try again.",
  PAYLOAD_TOO_LARGE: "That request is too large.",
  REQUEST_REJECTED: "That request couldn't be processed.",
  NETWORK_ERROR: "We couldn't reach the server. Check your connection and try again.",
  INTERNAL_ERROR: GENERIC_MESSAGE,
};

/**
 * Friendly copy for a backend error code. `overrides` lets a screen be more specific
 * (e.g. NOT_FOUND on the transfer form means "No wallet with that ID").
 */
export function messageFor(code: string, overrides: Partial<Record<string, string>> = {}): string {
  return overrides[code] ?? MESSAGES[code] ?? GENERIC_MESSAGE;
}

/** Serializable error shape returned by Server Actions to client forms. */
export type ActionError = {
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  /** Present only for unexpected errors, so users can quote it to support. */
  correlationId?: string;
};

export type ActionResult<T> = { ok: true; data: T; replayed?: boolean } | { ok: false; error: ActionError };

/** Any thrown value -> an ActionError a form can render. */
export function toActionError(err: unknown, overrides: Partial<Record<string, string>> = {}): ActionError {
  if (err instanceof ApiError) {
    const fieldErrors: Record<string, string> = {};
    for (const fe of err.fieldErrors) {
      fieldErrors[fe.field] ??= capitalize(fe.message);
    }
    return {
      code: err.code,
      message: messageFor(err.code, overrides),
      fieldErrors,
      correlationId: err.unexpected ? err.correlationId : undefined,
    };
  }
  return { code: "INTERNAL_ERROR", message: GENERIC_MESSAGE, fieldErrors: {} };
}

function capitalize(s: string): string {
  return s.length ? s[0].toUpperCase() + s.slice(1) : s;
}
