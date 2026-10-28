export const WEBHOOK_STATUSES = ["RECEIVED", "PROCESSED", "REJECTED", "FAILED"] as const;

/** A known status from the URL, or undefined (= all). */
export function webhookStatus(value: unknown): (typeof WEBHOOK_STATUSES)[number] | undefined {
  return WEBHOOK_STATUSES.find((s) => s === value);
}
