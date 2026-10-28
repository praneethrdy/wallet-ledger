import { cn } from "cn";

/**
 * Original mark for this project: three ledger bars, a debit and a credit that meet in a
 * balanced total. The name is the plain project name, not a brand of any real company.
 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden className={cn("size-7", className)}>
      <rect width="32" height="32" rx="9" className="fill-brand" />
      <rect x="8" y="9" width="10" height="4" rx="2" fill="white" opacity="0.95" />
      <rect x="14" y="14" width="10" height="4" rx="2" fill="white" opacity="0.7" />
      <rect x="8" y="19" width="16" height="4" rx="2" fill="white" opacity="0.95" />
    </svg>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex shrink-0 items-center gap-2.5", className)}>
      <LogoMark />
      <span className="text-[15px] font-semibold tracking-tight whitespace-nowrap">Wallet Ledger</span>
    </span>
  );
}
