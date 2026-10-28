import { describe, expect, it } from "vitest";

import { formatMoney, minorDigits, parseMoney, toDecimalString } from "./money";

describe("formatMoney", () => {
  it.each([
    [50050, "INR", "auto", "₹500.50"],
    [-50050, "INR", "auto", "−₹500.50"],
    [50050, "INR", "always", "+₹500.50"],
    [0, "INR", "always", "₹0.00"],
    [5, "INR", "auto", "₹0.05"],
    [10_000_000, "INR", "auto", "₹1,00,000.00"], // Indian lakh grouping
    [99, "EUR", "auto", "€0.99"],
    [1500, "JPY", "auto", "¥1,500"], // zero-decimal currency
    [-50050, "INR", "never", "₹500.50"],
  ] as const)("%d %s (%s) -> %s", (minor, currency, sign, expected) => {
    expect(formatMoney(minor, currency, sign)).toBe(expected);
  });

  it("is exact at the largest safe integer (no floating-point rounding)", () => {
    expect(formatMoney(Number.MAX_SAFE_INTEGER, "INR")).toBe("₹9,00,71,99,25,47,409.91");
  });

  it("rejects non-integers", () => {
    expect(() => formatMoney(1.5, "INR")).toThrow(RangeError);
    expect(() => formatMoney(Number.NaN, "INR")).toThrow(RangeError);
  });
});

describe("parseMoney", () => {
  const ok = (input: string, currency = "INR") => parseMoney(input, currency);

  it.each([
    ["500", 50000],
    ["500.5", 50050],
    ["500.50", 50050],
    ["0.1", 10],
    ["0.01", 1],
    [".5", 50],
    ["5.", 500],
    ["1,250.75", 125075],
    ["1,00,000.50", 10000050], // Indian grouping
    ["100,000", 10000000], // Western grouping
    ["₹500", 50000],
    ["₹ 1,250.75", 125075],
    ["INR 500", 50000],
    ["  42  ", 4200],
    ["０.１", 10], // full-width digits normalise
  ])("%s -> %d paise", (input, expected) => {
    expect(ok(input)).toEqual({ ok: true, minor: expected });
  });

  it("avoids the classic float trap: 0.1 + 0.2 style inputs stay exact", () => {
    expect(ok("19.99")).toEqual({ ok: true, minor: 1999 }); // parseFloat*100 gives 1998.9999999999998
    expect(ok("1.15")).toEqual({ ok: true, minor: 115 }); // ...and 114.99999999999999
  });

  it.each([
    ["", "empty"],
    ["   ", "empty"],
    ["abc", "format"],
    ["1.2.3", "format"],
    ["1,,000", "format"],
    [",500", "format"],
    ["500,", "format"],
    ["1,0000", "format"],
    ["1e3", "format"],
    [".", "format"],
    ["-5", "negative"],
    ["−5", "negative"],
    ["1.005", "precision"],
    ["0", "zero"],
    ["0.00", "zero"],
    ["1000000000001", "too-large"],
  ])("%j -> %s", (input, error) => {
    expect(ok(input)).toEqual({ ok: false, error });
  });

  it("respects currency precision", () => {
    expect(parseMoney("1500", "JPY")).toEqual({ ok: true, minor: 1500 });
    expect(parseMoney("15.5", "JPY")).toEqual({ ok: false, error: "precision" });
    expect(minorDigits("JPY")).toBe(0);
  });

  it("can allow zero when asked", () => {
    expect(parseMoney("0", "INR", { allowZero: true })).toEqual({ ok: true, minor: 0 });
  });

  it("round-trips with toDecimalString", () => {
    for (const minor of [1, 10, 99, 100, 50050, 10000050, 123456789]) {
      const parsed = parseMoney(toDecimalString(minor, "INR"), "INR");
      expect(parsed).toEqual({ ok: true, minor });
    }
  });
});
