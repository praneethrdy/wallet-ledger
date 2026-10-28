"use client";

import { formatMoney } from "@/lib/money";

import { AnimatedNumber } from "./animated-number";

/** A balance that counts up when it changes. (Formatter lives on the client side of the boundary.) */
export function AnimatedMoney({ minor, currency, className }: { minor: number; currency: string; className?: string }) {
  return <AnimatedNumber value={minor} format={(v) => formatMoney(v, currency)} className={className} />;
}
