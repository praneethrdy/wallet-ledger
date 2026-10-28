/**
 * Money is always an integer number of MINOR units (paise for INR) plus a currency code,
 * exactly as the backend sends it. Never convert to a floating-point "rupees" value for maths.
 *
 * Step 1 needs only formatting; parsing user input (string -> minor units) and its unit tests
 * arrive in step 2.
 */

const LOCALE = "en-IN";
const wholeFormatters = new Map<string, Intl.NumberFormat>();

/** Formats only the whole-currency part, with symbol and grouping (₹5,00,000 in en-IN). */
function wholeFormatter(currency: string): Intl.NumberFormat {
  let f = wholeFormatters.get(currency);
  if (!f) {
    f = new Intl.NumberFormat(LOCALE, {
      style: "currency",
      currency,
      currencyDisplay: "narrowSymbol",
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });
    wholeFormatters.set(currency, f);
  }
  return f;
}

/** Decimal places for a currency (INR/USD/EUR: 2, JPY: 0). */
export function minorDigits(currency: string): number {
  return new Intl.NumberFormat(LOCALE, { style: "currency", currency }).resolvedOptions().maximumFractionDigits ?? 2;
}

export type SignMode = "auto" | "always" | "never";

/**
 * Minor units -> display string, exactly (no floating point anywhere):
 *   formatMoney(50050, "INR")            -> "₹500.50"
 *   formatMoney(-50050, "INR")           -> "−₹500.50"   (true minus sign, U+2212)
 *   formatMoney(50050, "INR", "always")  -> "+₹500.50"
 */
export function formatMoney(minor: number, currency: string, sign: SignMode = "auto"): string {
  if (!Number.isSafeInteger(minor)) {
    throw new RangeError(`Amount must be a safe integer of minor units, got ${minor}`);
  }
  const digits = minorDigits(currency);
  const abs = BigInt(Math.abs(minor));
  const scale = BigInt(10) ** BigInt(digits);
  const whole = abs / scale;
  const fraction = (abs % scale).toString().padStart(digits, "0");

  const parts = wholeFormatter(currency).formatToParts(whole);
  const lastInteger = parts.findLastIndex((p) => p.type === "integer");
  const body = parts.map((p, i) => (i === lastInteger && digits > 0 ? `${p.value}.${fraction}` : p.value)).join("");

  if (minor < 0 && sign !== "never") return `−${body}`;
  if (minor > 0 && sign === "always") return `+${body}`;
  return body;
}
