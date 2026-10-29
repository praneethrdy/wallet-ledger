import { randomUUID } from "node:crypto";

import { expect, type Page } from "@playwright/test";

/** The backend, called directly only to arrange data and to check results independently of the UI. */
export const API_ROOT = (process.env.E2E_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const API = `${API_ROOT}/api/v1`;

export const USERS = {
  alice: { email: "alice@wallet.local", password: "alice-pass-123" },
  bob: { email: "bob@wallet.local", password: "bob-pass-123" },
  support: { email: "support@wallet.local", password: "support-pass-123" },
  admin: { email: "admin@wallet.local", password: "admin-pass-123" },
} as const;
export type Who = keyof typeof USERS;

export type Wallet = { id: string; name: string; currency: string; balance: number; status: string };

export async function signIn(page: Page, who: Who) {
  await page.goto("/login");
  await page.fill("#email", USERS[who].email);
  await page.fill("#password", USERS[who].password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
}

const tokens = new Map<Who, string>();

async function token(who: Who): Promise<string> {
  const cached = tokens.get(who);
  if (cached) return cached;
  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(USERS[who]),
  });
  expect(res.ok, `API login as ${who}`).toBe(true);
  const t = ((await res.json()) as { accessToken: string }).accessToken;
  tokens.set(who, t);
  return t;
}

/** Backend call as a user; mutations get a fresh Idempotency-Key. */
export async function api<T = unknown>(who: Who, method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      authorization: `Bearer ${await token(who)}`,
      "content-type": "application/json",
      ...(method === "GET" ? {} : { "Idempotency-Key": randomUUID() }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return (await res.json()) as T;
}

export const wallets = (who: Who) => api<Wallet[]>(who, "GET", "/wallets");

export async function wallet(who: Who, id: string): Promise<Wallet> {
  return api<Wallet>(who, "GET", `/wallets/${id}`);
}

/** Alice's and Bob's main INR wallets, in the seeded demo data. */
export async function mainWallets() {
  const alice = (await wallets("alice")).find((w) => w.name === "Main" && w.currency === "INR");
  const bob = (await wallets("bob")).find((w) => w.name === "Main" && w.currency === "INR");
  if (!alice || !bob) throw new Error("Seed data missing: alice and bob need an INR wallet named Main");
  return { alice, bob };
}

/** Make sure a wallet has at least `minor` to spend (deposits through the dev mock provider). */
export async function ensureBalance(who: Who, walletId: string, minor: number) {
  const w = await wallet(who, walletId);
  if (w.balance >= minor) return;
  const dep = await api<{ providerReference: string }>(who, "POST", "/deposits", {
    walletId,
    amount: minor - w.balance + 100_00,
    currency: w.currency,
  });
  const res = await fetch(`${API_ROOT}/dev/mock-provider/deposits/${dep.providerReference}/complete?outcome=success`, {
    method: "POST",
  });
  expect(res.ok, "mock provider settles the top-up").toBe(true);
}

export function unique(prefix: string) {
  return `${prefix} ${randomUUID().slice(0, 6)}`;
}
