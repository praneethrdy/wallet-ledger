import { cn } from "cn";

import { formatMoney } from "@/lib/money";

type AmountProps = {
  /** Minor units (paise for INR). */
  minor: number;
  currency: string;
  /**
   * credit: money in, emerald with "+".
   * debit:  money out, default text colour with "−" (outgoing money is not an error).
   * signed: sign and colour follow the value (positive = credit, negative = debit).
   * plain:  no sign, default colour (balances, totals).
   */
  tone?: "credit" | "debit" | "signed" | "plain";
  className?: string;
};

/** A money value: Geist Mono, tabular figures, exact minor-unit formatting. */
export function Amount({ minor, currency, tone = "plain", className }: AmountProps) {
  let text: string;
  let positive = false;
  switch (tone) {
    case "credit":
      text = formatMoney(Math.abs(minor), currency, "always");
      positive = minor !== 0;
      break;
    case "debit":
      text = minor === 0 ? formatMoney(0, currency) : `−${formatMoney(Math.abs(minor), currency)}`;
      break;
    case "signed":
      text = formatMoney(minor, currency, "always");
      positive = minor > 0;
      break;
    default:
      text = formatMoney(minor, currency);
  }
  return <span className={cn("num whitespace-nowrap", positive && "text-credit-text", className)}>{text}</span>;
}
