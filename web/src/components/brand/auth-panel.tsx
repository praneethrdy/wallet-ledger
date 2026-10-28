import { CheckIcon } from "lucide-react";

import { Logo, LogoMark } from "@/components/brand/logo";
import { formatMoney } from "@/lib/money";

/** Right-hand brand panel on the auth screens (desktop only): dots, glow, floating balance card. */
export function AuthBrandPanel() {
  return (
    <div className="relative hidden overflow-hidden border-l border-border bg-background lg:block">
      <div className="absolute inset-0 bg-dots [mask-image:radial-gradient(ellipse_at_center,black,transparent_75%)]" />
      <div className="absolute top-1/2 left-1/2 size-[640px] -translate-x-1/2 -translate-y-1/2 glow" />
      <div className="relative flex h-full flex-col justify-between p-12">
        <Logo />
        <div className="mx-auto w-80">
          <div className="rotate-[-2deg] surface p-6 shadow-lg">
            <div className="flex items-center justify-between">
              <span className="eyebrow">Available balance</span>
              <LogoMark className="size-5" />
            </div>
            <div className="mt-2 num text-3xl font-medium tracking-tight">{formatMoney(5_976_200, "INR")}</div>
            <div className="mt-5 space-y-2.5 border-t border-border pt-4 text-xs">
              <Row label="Rent share" value={`−${formatMoney(1_850_000, "INR")}`} />
              <Row label="Deposit" value={formatMoney(5_000_000, "INR", "always")} credit />
            </div>
          </div>
          <div className="mt-4 ml-auto w-fit translate-x-6 rotate-[1.5deg] surface px-4 py-3 shadow-md">
            <div className="flex items-center gap-2 text-xs">
              <span className="grid size-5 place-items-center rounded-full bg-[color-mix(in_oklab,var(--credit)_14%,transparent)] text-credit-text">
                <CheckIcon className="size-3" aria-hidden />
              </span>
              <span className="text-muted-foreground">Debits = credits</span>
              <span className="font-medium">Balanced</span>
            </div>
          </div>
        </div>
        <div>
          <p className="max-w-sm text-lg leading-snug font-medium tracking-tight">Every rupee, accounted for twice.</p>
          <p className="mt-2 max-w-sm text-sm text-muted-foreground">
            Each movement is a balanced pair of ledger entries. Nothing is ever edited, only corrected.
          </p>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, credit = false }: { label: string; value: string; credit?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className={credit ? "num text-credit-text" : "num"}>{value}</span>
    </div>
  );
}
