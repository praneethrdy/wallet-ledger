/**
 * Money is always an integer number of MINOR units (paise for INR) plus a currency code,
 * exactly as the backend sends it. Nothing here ever turns money into a floating-point
 * "rupees" value: formatting and parsing both work on digit strings and BigInt.
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

/** The currency's symbol on its own ("₹", "$", "€"). */
export function currencySymbol(currency: string): string {
  return (
    wholeFormatter(currency)
      .formatToParts(0)
      .find((p) => p.type === "currency")?.value ?? currency
  );
}

export type SignMode = "auto" | "always" | "never";

/**
 * Minor units -> display string, exactly:
 *   formatMoney(50050, "INR")            -> "₹500.50"
 *   formatMoney(-50050, "INR")           -> "−₹500.50"   (true minus sign, U+2212)
 *   formatMoney(50050, "INR", "always")  -> "+₹500.50"
 */
export function formatMoney(minor: number, currency: string, sign: SignMode = "auto"): string {
  if (!Number.isSafeInteger(minor)) {
    throw new RangeError(`Amount must be a safe integer of minor units, got ${minor}`);
  }
  const digits = minorDigits(currency);
  const { whole, fraction } = split(BigInt(Math.abs(minor)), digits);

  const parts = wholeFormatter(currency).formatToParts(whole);
  const lastInteger = parts.findLastIndex((p) => p.type === "integer");
  const body = parts.map((p, i) => (i === lastInteger && digits > 0 ? `${p.value}.${fraction}` : p.value)).join("");

  if (minor < 0 && sign !== "never") return `−${body}`;
  if (minor > 0 && sign === "always") return `+${body}`;
  return body;
}

/** Minor units -> plain decimal string for an input field: 50050 -> "500.50" (no symbol, no grouping). */
export function toDecimalString(minor: number, currency: string): string {
  const digits = minorDigits(currency);
  const { whole, fraction } = split(BigInt(Math.abs(minor)), digits);
  const text = digits > 0 ? `${whole}.${fraction}` : `${whole}`;
  return minor < 0 ? `-${text}` : text;
}

export type MoneyParseError = "empty" | "format" | "negative" | "precision" | "zero" | "too-large";

export type MoneyParseResult = { ok: true; minor: number } | { ok: false; error: MoneyParseError };

/** Largest amount accepted from user input: 1 trillion currency units, well inside the backend's BIGINT. */
const MAX_MINOR = BigInt(1_000_000_000_000) * BigInt(100);

/**
 * User input -> exact minor units, never via parseFloat.
 *   "500"      -> 50000         "1,00,000.5" -> 10000050      "₹ 1,250.75" -> 125075
 *   "0.1"      -> 10            "1.005"      -> precision      "-5"        -> negative
 * Grouping commas are allowed (Indian or Western) but must sit between digits.
 */
export function parseMoney(input: string, currency: string, options: { allowZero?: boolean } = {}): MoneyParseResult {
  const digits = minorDigits(currency);
  let text = input.normalize("NFKC").trim();
  if (text === "") return { ok: false, error: "empty" };

  // Optional leading currency symbol or code ("₹500", "INR 500").
  const symbol = currencySymbol(currency);
  for (const prefix of [symbol, currency]) {
    if (text.toUpperCase().startsWith(prefix.toUpperCase())) {
      text = text.slice(prefix.length).trim();
      break;
    }
  }
  if (/^[-−]/.test(text)) return { ok: false, error: "negative" };

  // Digits with optional grouping commas, then an optional fraction.
  const match = /^(\d{1,3}(?:,\d{2,3})*|\d+)?(?:\.(\d*))?$/.exec(text);
  if (!match || (match[1] === undefined && !match[2])) return { ok: false, error: "format" };

  const wholePart = (match[1] ?? "0").replaceAll(",", "");
  const fractionPart = match[2] ?? "";
  if (fractionPart.length > digits) return { ok: false, error: "precision" };

  const minor = BigInt(wholePart) * BigInt(10) ** BigInt(digits) + BigInt(fractionPart.padEnd(digits, "0") || "0");
  if (minor > MAX_MINOR) return { ok: false, error: "too-large" };
  if (minor === BigInt(0) && !options.allowZero) return { ok: false, error: "zero" };
  return { ok: true, minor: Number(minor) };
}

/** Friendly copy for a parse error, for form field messages. */
export function moneyParseMessage(error: MoneyParseError, currency: string): string {
  const digits = minorDigits(currency);
  switch (error) {
    case "empty":
      return "Enter an amount";
    case "format":
      return "Enter a number, like 500 or 1,250.50";
    case "negative":
      return "Amount must be positive";
    case "precision":
      return digits === 0 ? `${currency} has no decimal places` : `Use at most ${digits} decimal places`;
    case "zero":
      return "Amount must be more than zero";
    case "too-large":
      return "That amount is too large";
  }
}

function split(abs: bigint, digits: number): { whole: bigint; fraction: string } {
  const scale = BigInt(10) ** BigInt(digits);
  return { whole: abs / scale, fraction: (abs % scale).toString().padStart(digits, "0") };
}
