import { describe, expect, it } from "vitest";

import { balanceSeries, walletShares, windowChange } from "./dashboard";

const flows = [
  { date: "2026-10-27", inflow: 0, outflow: 0 },
  { date: "2026-10-28", inflow: 50_000, outflow: 0 },
  { date: "2026-10-29", inflow: 10_000, outflow: 30_000 },
];

describe("balanceSeries", () => {
  it("works backwards from today's total", () => {
    expect(balanceSeries(100_000, flows)).toEqual([
      { date: "2026-10-27", balance: 70_000 },
      { date: "2026-10-28", balance: 120_000 },
      { date: "2026-10-29", balance: 100_000 },
    ]);
  });

  it("is empty without flows", () => {
    expect(balanceSeries(100_000, [])).toEqual([]);
  });
});

describe("windowChange", () => {
  it("is the sum of daily nets", () => {
    expect(windowChange(flows)).toBe(30_000);
  });
});

describe("walletShares", () => {
  const w = (id: string, balance: number, currency = "INR") => ({ id, balance, currency });

  it("splits one currency, largest first, skipping empty and other-currency wallets", () => {
    const shares = walletShares([w("a", 25_000), w("b", 75_000), w("c", 0), w("d", 9_999, "USD")], "INR");
    expect(shares.map((s) => [s.wallet.id, s.share])).toEqual([
      ["b", 0.75],
      ["a", 0.25],
    ]);
  });

  it("is empty when nothing is held", () => {
    expect(walletShares([w("a", 0)], "INR")).toEqual([]);
  });
});
