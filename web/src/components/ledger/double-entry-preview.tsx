import { ArrowDownIcon, CheckIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { formatMoney } from "@/lib/money";

type Party = { label: string; detail?: string };

type DoubleEntryPreviewProps = {
  from: Party;
  to: Party;
  /** Minor units. */
  amount: number;
  currency: string;
};

/**
 * Transfer review step: shows the two ledger legs a transfer will post, and that they net to
 * zero. The same rule the backend enforces (debits = credits), made visible to the user.
 */
export function DoubleEntryPreview({ from, to, amount, currency }: DoubleEntryPreviewProps) {
  return (
    <div className="overflow-hidden surface">
      <div className="flex items-center justify-between border-b border-border px-5 py-3">
        <span className="eyebrow">Ledger preview</span>
        <span className="text-xs text-muted-foreground">posted atomically, both or neither</span>
      </div>

      <Leg party={from} direction="DEBIT" value={`−${formatMoney(amount, currency)}`} />
      <div className="relative h-0">
        <span className="absolute top-1/2 left-5 z-10 grid size-6 -translate-y-1/2 place-items-center rounded-full border border-border bg-card text-muted-foreground shadow-xs">
          <ArrowDownIcon className="size-3.5" aria-hidden />
        </span>
      </div>
      <Leg party={to} direction="CREDIT" value={formatMoney(amount, currency, "always")} credit />

      <div className="flex items-center justify-between border-t border-dashed border-border bg-muted/50 px-5 py-3">
        <span className="text-sm text-muted-foreground">Net</span>
        <span className="flex items-center gap-3">
          <span className="num text-sm">{formatMoney(0, currency)}</span>
          <Badge variant="success">
            <CheckIcon aria-hidden /> Balanced
          </Badge>
        </span>
      </div>
    </div>
  );
}

function Leg({
  party,
  direction,
  value,
  credit = false,
}: {
  party: Party;
  direction: "DEBIT" | "CREDIT";
  value: string;
  credit?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-4 px-5 py-4 pl-14">
      <div className="min-w-0">
        <div className="truncate text-sm font-medium">{party.label}</div>
        {party.detail && <div className="truncate num text-xs text-muted-foreground">{party.detail}</div>}
      </div>
      <div className="text-right">
        <div className={credit ? "num text-[15px] font-medium text-credit-text" : "num text-[15px] font-medium"}>
          {value}
        </div>
        <div className="eyebrow">{direction}</div>
      </div>
    </div>
  );
}
