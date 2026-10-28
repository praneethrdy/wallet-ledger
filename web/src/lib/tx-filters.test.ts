import { describe, expect, it } from "vitest";

import { parseTxFilters, txApiQuery, txFiltersQuery } from "./tx-filters";

const WALLET = "01a181c2-42c4-7ade-97e5-4557181301dc";

describe("parseTxFilters", () => {
  it("keeps valid filters", () => {
    expect(
      parseTxFilters({ walletId: WALLET, type: "DEPOSIT", from: "2026-10-01", to: "2026-10-28", reference: " txn-1 " }),
    ).toEqual({ walletId: WALLET, type: "DEPOSIT", from: "2026-10-01", to: "2026-10-28", reference: "txn-1" });
  });

  it("drops malformed values instead of failing", () => {
    expect(
      parseTxFilters({ walletId: "nope", type: "HACK", from: "2026-02-30", to: "28/10/2026", reference: "" }),
    ).toEqual({ walletId: undefined, type: undefined, from: undefined, to: undefined, reference: undefined });
  });

  it("ignores repeated params", () => {
    expect(parseTxFilters({ type: ["DEPOSIT", "TRANSFER"] }).type).toBeUndefined();
  });
});

describe("txApiQuery", () => {
  it("turns IST calendar days into a half-open instant range", () => {
    expect(txApiQuery({ from: "2026-10-01", to: "2026-10-28" })).toMatchObject({
      from: "2026-10-01T00:00:00+05:30",
      to: "2026-10-29T00:00:00+05:30",
    });
  });

  it("rolls the exclusive end over month and year boundaries", () => {
    expect(txApiQuery({ to: "2026-12-31" }).to).toBe("2027-01-01T00:00:00+05:30");
    expect(txApiQuery({ to: "2028-02-28" }).to).toBe("2028-02-29T00:00:00+05:30");
  });
});

describe("txFiltersQuery", () => {
  it("writes only set filters in a stable order", () => {
    expect(txFiltersQuery({ reference: "TXN-1", type: "TRANSFER" })).toBe("type=TRANSFER&reference=TXN-1");
    expect(txFiltersQuery({})).toBe("");
  });
});
