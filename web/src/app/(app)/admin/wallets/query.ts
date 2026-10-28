export const WALLET_STATUSES = ["ACTIVE", "FROZEN", "CLOSED"] as const;
type Status = (typeof WALLET_STATUSES)[number];

/** Wallet search filters from the URL; malformed values are dropped. */
export function adminWalletQuery(params: Record<string, string | string[] | undefined>) {
  const email = typeof params.email === "string" ? params.email.trim().slice(0, 254) : "";
  const status = WALLET_STATUSES.find((s) => s === params.status) as Status | undefined;
  return { email: email || undefined, status };
}

export function adminWalletQueryString(q: Partial<ReturnType<typeof adminWalletQuery>>): string {
  const s = new URLSearchParams();
  if (q.email) s.set("email", q.email);
  if (q.status) s.set("status", q.status);
  return s.toString();
}
