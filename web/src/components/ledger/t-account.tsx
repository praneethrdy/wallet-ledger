import { CheckIcon, TriangleAlertIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { shortId } from "@/lib/format";
import { formatMoney } from "@/lib/money";

export type TLeg = {
  accountName: string;
  accountId?: string;
  direction: "DEBIT" | "CREDIT";
  /** Minor units, always positive (the sign lives in `direction`). */
  amount: number;
  currency: string;
};

/**
 * Transaction detail as a classic T-account: debits on the left, credits on the right, totals
 * per currency, and a "Balanced" badge computed from the legs (not assumed). `showIds` adds each
 * account's short ID under its name (staff views, where two wallets can share a name).
 */
export function TAccount({ legs, showIds = false }: { legs: TLeg[]; showIds?: boolean }) {
  const debits = legs.filter((l) => l.direction === "DEBIT");
  const credits = legs.filter((l) => l.direction === "CREDIT");
  const currencies = [...new Set(legs.map((l) => l.currency))];
  const totals = currencies.map((c) => ({
    currency: c,
    debit: debits.filter((l) => l.currency === c).reduce((s, l) => s + l.amount, 0),
    credit: credits.filter((l) => l.currency === c).reduce((s, l) => s + l.amount, 0),
  }));
  const balanced = totals.every((t) => t.debit === t.credit);

  return (
    <div className="overflow-hidden surface">
      <div className="grid grid-cols-2 border-b border-border">
        <div className="px-5 py-3 eyebrow">Debits</div>
        <div className="border-l border-border px-5 py-3 text-right eyebrow">Credits</div>
      </div>
      <div className="grid grid-cols-2">
        <Column legs={debits} showIds={showIds} />
        <Column legs={credits} credit showIds={showIds} className="border-l border-border" />
      </div>
      {totals.map((t) => (
        <div key={t.currency} className="grid grid-cols-2 border-t-2 border-double border-border bg-muted/50">
          <div className="px-5 py-3 num text-sm font-medium">{formatMoney(t.debit, t.currency)}</div>
          <div className="border-l border-border px-5 py-3 text-right num text-sm font-medium">
            {formatMoney(t.credit, t.currency)}
          </div>
        </div>
      ))}
      <div className="flex items-center justify-between border-t border-border px-5 py-3">
        <span className="text-xs text-muted-foreground">
          {legs.length} legs · {currencies.join(", ")}
        </span>
        {balanced ? (
          <Badge variant="success">
            <CheckIcon aria-hidden /> Balanced
          </Badge>
        ) : (
          <Badge variant="danger">
            <TriangleAlertIcon aria-hidden /> Unbalanced
          </Badge>
        )}
      </div>
    </div>
  );
}

function Column({
  legs,
  credit = false,
  showIds = false,
  className = "",
}: {
  legs: TLeg[];
  credit?: boolean;
  showIds?: boolean;
  className?: string;
}) {
  return (
    <ul className={`divide-y divide-border/70 ${className}`}>
      {legs.map((leg, i) => (
        <li key={`${leg.accountId ?? leg.accountName}-${i}`} className={`px-5 py-3 ${credit ? "text-right" : ""}`}>
          <div className="truncate text-sm">{leg.accountName}</div>
          {showIds && leg.accountId && (
            <div className="num text-xs text-muted-foreground">{shortId(leg.accountId)}</div>
          )}
          <div className={`num text-sm ${credit ? "text-credit-text" : ""}`}>
            {formatMoney(leg.amount, leg.currency)}
          </div>
        </li>
      ))}
      {legs.length === 0 && <li className="px-5 py-3 text-sm text-muted-foreground">—</li>}
    </ul>
  );
}
