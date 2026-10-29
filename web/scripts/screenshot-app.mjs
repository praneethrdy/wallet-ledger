// Portfolio screenshots of the main screens, light and dark, into docs/screenshots/.
// Needs the backend (dev profile: seeded demo users + mock payment provider) and a production
// build on :3000:  npm run build && npm start -- -p 3000, then: node scripts/screenshot-app.mjs [outDir]
//
// To keep the pictures free of test leftovers, it registers a fresh customer ("Meera") with a
// few tidy wallets and transactions, and shoots her screens; the admin wallet list is filtered
// to her email.
import { randomUUID } from "node:crypto";

import { chromium } from "@playwright/test";

const out = process.argv[2] ?? "../docs/screenshots";
const WEB = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const ROOT = (process.env.E2E_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const API = `${ROOT}/api/v1`;

async function call(token, method, path, body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...(method === "GET" ? {} : { "Idempotency-Key": randomUUID() }),
    },
    body: body && JSON.stringify(body),
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${JSON.stringify(json)}`);
  return json;
}

async function deposit(token, walletId, amount, currency) {
  const d = await call(token, "POST", "/deposits", { walletId, amount, currency });
  const res = await fetch(`${ROOT}/dev/mock-provider/deposits/${d.providerReference}/complete?outcome=success`, {
    method: "POST",
  });
  if (!res.ok) throw new Error(`mock provider: ${res.status}`);
}

/** A fresh customer with believable activity. */
async function setUp() {
  const email = `meera.${randomUUID().slice(0, 6)}@wallet.local`;
  const password = "meera-demo-pass-2026";
  await call(null, "POST", "/auth/register", { email, password });
  const { accessToken: meera } = await call(null, "POST", "/auth/login", { email, password });
  const main = await call(meera, "POST", "/wallets", { name: "Main", currency: "INR" });
  const savings = await call(meera, "POST", "/wallets", { name: "Savings", currency: "INR" });
  const travel = await call(meera, "POST", "/wallets", { name: "Travel", currency: "USD" });
  await deposit(meera, main.id, 85_000_00, "INR");
  await deposit(meera, travel.id, 1_200_00, "USD");
  await call(meera, "POST", "/transfers", {
    fromWalletId: main.id,
    toWalletId: savings.id,
    amount: 20_000_00,
    currency: "INR",
    note: "Monthly savings",
  });

  const { accessToken: bob } = await call(null, "POST", "/auth/login", {
    email: "bob@wallet.local",
    password: "bob-pass-123",
  });
  const bobMain = (await call(bob, "GET", "/wallets")).find((w) => w.name === "Main" && w.currency === "INR");
  const dinner = await call(meera, "POST", "/transfers", {
    fromWalletId: main.id,
    toWalletId: bobMain.id,
    amount: 1_850_00,
    currency: "INR",
    note: "Dinner split",
  });
  if (bobMain.balance < 3_200_00) await deposit(bob, bobMain.id, 5_000_00, "INR");
  await call(bob, "POST", "/transfers", {
    fromWalletId: bobMain.id,
    toWalletId: main.id,
    amount: 3_200_00,
    currency: "INR",
    note: "Concert tickets",
  });
  return { email, password, dinnerId: dinner.id };
}

async function signIn(page, email, password) {
  await page.goto(`${WEB}/login`);
  await page.fill("#email", email);
  await page.fill("#password", password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"));
}

async function settle(page) {
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(900); // count-ups and fades
}

const meera = await setUp();
const admin = { email: "admin@wallet.local", password: "admin-pass-123" };

/** Each shot: who, file name, and how to get the page into the state worth showing. */
const SHOTS = [
  [meera, "dashboard", (p) => p.goto(`${WEB}/dashboard`)],
  [
    meera,
    "transfer-review",
    async (p) => {
      await p.goto(`${WEB}/transfer`);
      await p.getByRole("button", { name: "Savings", exact: true }).click(); // "your wallets" chip
      await p.fill("#amount", "12500");
      await p.fill("#note", "Emergency fund");
      await p.getByRole("button", { name: "Review" }).click();
      await p.getByRole("button", { name: "Confirm transfer" }).waitFor();
    },
  ],
  [
    meera,
    "deposit-pending",
    async (p) => {
      await p.goto(`${WEB}/deposit`);
      await p.fill("#amount", "2000");
      await p.getByRole("button", { name: "Continue to payment" }).click();
      await p.getByText("Waiting for the payment provider").waitFor();
    },
  ],
  [meera, "transaction-detail", (p) => p.goto(`${WEB}/transactions/${meera.dinnerId}`)],
  [admin, "admin-overview", (p) => p.goto(`${WEB}/admin`)],
  [admin, "admin-wallets", (p) => p.goto(`${WEB}/admin/wallets?email=${encodeURIComponent(meera.email)}`)],
  [
    admin,
    "admin-reconciliation",
    async (p) => {
      await p.goto(`${WEB}/admin/reconciliation`);
      await p.locator('a[href^="/admin/reconciliation/"]').first().click();
      await p.getByRole("heading", { name: /^Run of/ }).waitFor();
    },
  ],
  [
    admin,
    "admin-webhooks",
    async (p) => {
      await p.goto(`${WEB}/support/webhook-events`);
      await p.locator("details summary").first().click();
    },
  ],
];

const browser = await chromium.launch();
for (const scheme of ["light", "dark"]) {
  const contexts = new Map();
  for (const [who, name, open] of SHOTS) {
    if (!contexts.has(who)) {
      const ctx = await browser.newContext({ colorScheme: scheme, viewport: { width: 1440, height: 900 } });
      await signIn(await ctx.newPage(), who.email, who.password);
      contexts.set(who, ctx);
    }
    const page = contexts.get(who).pages()[0];
    await open(page);
    await settle(page);
    await page.screenshot({ path: `${out}/${name}-${scheme}.png` });
    console.log("saved", `${name}-${scheme}`);
  }
  for (const c of contexts.values()) await c.close();
}
await browser.close();
