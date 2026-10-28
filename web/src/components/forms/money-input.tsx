"use client";

import { cn } from "cn";

import { Input } from "@/components/ui/input";
import { currencySymbol } from "@/lib/money";

/**
 * Amount field: currency symbol prefix, Geist Mono, right-aligned, decimal keyboard on mobile.
 * Keeps the raw text; parsing to minor units happens with parseMoney (never parseFloat).
 */
export function MoneyInput({
  currency,
  className,
  ...props
}: Omit<React.ComponentProps<typeof Input>, "type"> & { currency: string }) {
  return (
    <div className="relative">
      <span className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-sm text-muted-foreground">
        {currencySymbol(currency)}
      </span>
      <Input
        type="text"
        inputMode="decimal"
        autoComplete="off"
        placeholder="0.00"
        className={cn("pl-8 text-right num text-[15px]", className)}
        {...props}
      />
    </div>
  );
}
