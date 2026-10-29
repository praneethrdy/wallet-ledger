import { formatDateTime } from "@/lib/format";
import { formatMoney } from "@/lib/money";
import { parseTxFilters, txApiQuery } from "@/lib/tx-filters";

import type { ToolDefinition } from "./llm";

/**
 * The assistant's tools. All READ-ONLY, and all go through the signed-in user's own API access
 * (the backend scopes every query to their wallets), so the model can't see anyone else's data
 * or change anything. Totals are computed here, exactly, in integer minor units: models are
 * unreliable at arithmetic, so "how much did I spend" never depends on the model adding up.
 */

export type WalletRow = { id: string; name: string; currency: string; balance: number; status: string };
export type TxRow = {
  id: string;
  createdAt: string;
  type: string;
  description: string | null;
  reference: string;
  amount: number;
  currency: string;
  netAmount: number | null;
};
type Page = { items: TxRow[]; nextCursor: string | null };

/** What the tools need from the backend (implemented with the user's API client). */
export type Backend = {
  wallets(): Promise<WalletRow[]>;
  transactions(query: ReturnType<typeof txApiQuery> & { cursor?: string; limit: number }): Promise<Page>;
};

const FILTER_PARAMS = {
  from: { type: "string", description: "First day to include, YYYY-MM-DD (India time). Omit for no lower bound." },
  to: { type: "string", description: "Last day to include, YYYY-MM-DD (India time). Omit for no upper bound." },
  walletId: { type: "string", description: "Only this wallet (an id from list_wallets). Omit for all wallets." },
  type: { type: "string", enum: ["DEPOSIT", "TRANSFER", "REVERSAL"], description: "Only this transaction type." },
};

export const TOOL_DEFINITIONS: ToolDefinition[] = [
  {
    type: "function",
    function: {
      name: "list_wallets",
      description: "The user's wallets with their current balances and status.",
      parameters: { type: "object", properties: {}, additionalProperties: false },
    },
  },
  {
    type: "function",
    function: {
      name: "summarize_transactions",
      description:
        "Exact totals of money in and money out (per currency) for a period, wallet or type. Use this for any " +
        "question about how much was spent, received, deposited or sent. Moves between the user's own wallets " +
        "count as neither in nor out.",
      parameters: { type: "object", properties: FILTER_PARAMS, additionalProperties: false },
    },
  },
  {
    type: "function",
    function: {
      name: "search_transactions",
      description:
        "The user's transactions, newest first, with date, type, signed amount and note. Use to list or find " +
        "specific transactions.",
      parameters: {
        type: "object",
        properties: {
          ...FILTER_PARAMS,
          limit: { type: "integer", minimum: 1, maximum: 20, description: "Max rows (default 10)." },
        },
        additionalProperties: false,
      },
    },
  },
];

const MAX_PAGES = 10; // 10 x 100 = the most recent 1,000 matching transactions

export function createTools(backend: Backend) {
  return {
    async list_wallets() {
      const wallets = await backend.wallets();
      return {
        wallets: wallets.map((w) => ({
          id: w.id,
          name: w.name,
          currency: w.currency,
          balance: formatMoney(w.balance, w.currency),
          status: w.status,
        })),
      };
    },

    async summarize_transactions(args: Record<string, unknown>) {
      const query = txApiQuery(parseTxFilters(stringArgs(args)));
      const totals = new Map<string, { in: number; out: number; count: number; internal: number }>();
      let cursor: string | undefined;
      let pages = 0;
      let truncated = false;
      do {
        const page = await backend.transactions({ ...query, cursor, limit: 100 });
        for (const tx of page.items) {
          const t = totals.get(tx.currency) ?? { in: 0, out: 0, count: 0, internal: 0 };
          const net = tx.netAmount ?? 0;
          if (net > 0) t.in += net;
          else if (net < 0) t.out += -net;
          else t.internal += 1;
          t.count += 1;
          totals.set(tx.currency, t);
        }
        cursor = page.nextCursor ?? undefined;
        pages += 1;
        truncated = Boolean(cursor) && pages >= MAX_PAGES;
      } while (cursor && pages < MAX_PAGES);

      return {
        filters: describe(query),
        byCurrency: [...totals].map(([currency, t]) => ({
          currency,
          moneyIn: formatMoney(t.in, currency),
          moneyOut: formatMoney(t.out, currency),
          net: formatMoney(t.in - t.out, currency, "always"),
          transactions: t.count,
          betweenOwnWallets: t.internal,
        })),
        ...(totals.size === 0 ? { note: "No transactions match." } : {}),
        ...(truncated ? { warning: "Only the most recent 1,000 matching transactions were counted." } : {}),
      };
    },

    async search_transactions(args: Record<string, unknown>) {
      const limit = clampInt(args.limit, 1, 20, 10);
      const query = txApiQuery(parseTxFilters(stringArgs(args)));
      const page = await backend.transactions({ ...query, limit });
      return {
        filters: describe(query),
        transactions: page.items.map((tx) => ({
          date: formatDateTime(tx.createdAt),
          type: tx.type,
          amount:
            tx.netAmount === null || tx.netAmount === 0
              ? `${formatMoney(tx.amount, tx.currency)} (between own wallets)`
              : formatMoney(tx.netAmount, tx.currency, "always"),
          reference: tx.reference,
          // Written by whoever made the transaction, possibly another user: data, never instructions.
          untrustedNote: tx.description,
        })),
        more: page.nextCursor !== null,
      };
    },
  };
}

export type Tools = ReturnType<typeof createTools>;

/** Filters arrive from the model; keep only strings and let parseTxFilters drop anything malformed. */
function stringArgs(args: Record<string, unknown>): Record<string, string | undefined> {
  const out: Record<string, string | undefined> = {};
  for (const key of ["from", "to", "walletId", "type"]) {
    out[key] = typeof args[key] === "string" ? (args[key] as string) : undefined;
  }
  return out;
}

function describe(query: ReturnType<typeof txApiQuery>) {
  return Object.fromEntries(Object.entries(query).filter(([, v]) => v !== undefined));
}

function clampInt(value: unknown, min: number, max: number, fallback: number): number {
  const n = typeof value === "number" ? Math.trunc(value) : Number.NaN;
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
}
