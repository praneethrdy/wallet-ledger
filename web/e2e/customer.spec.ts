import { expect, test } from "@playwright/test";

import { api, ensureBalance, mainWallets, signIn, unique, wallet } from "./helpers";

test.describe("customer", () => {
  test("dashboard shows totals, the cash-flow chart and recent activity", async ({ page }) => {
    await signIn(page, "alice");
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText("Total balance")).toBeVisible();
    await expect(page.getByText("Cash flow · last 30 days")).toBeVisible();
    await expect(page.locator(".recharts-surface").first()).toBeVisible();
    await expect(page.getByText("Recent transactions")).toBeVisible();
  });

  test("create a wallet, transfer to bob, see the entry and both legs", async ({ page }) => {
    const { alice, bob } = await mainWallets();
    await ensureBalance("alice", alice.id, 50_00);
    await signIn(page, "alice");

    // New wallet
    const name = unique("E2E");
    await page.goto("/wallets");
    await page.getByRole("button", { name: "New wallet" }).first().click();
    await page.fill("#wallet-name", name);
    await page.getByRole("button", { name: "Create wallet" }).click();
    await expect(page.getByRole("heading", { name })).toBeVisible();

    // Transfer 12.34 from Main to bob
    await page.goto(`/transfer?from=${alice.id}&to=${bob.id}`);
    await page.fill("#amount", "12.34");
    await page.fill("#note", "e2e transfer");
    await page.getByRole("button", { name: "Review" }).click();
    await page.getByRole("button", { name: "Confirm transfer" }).click();
    await expect(page.getByText("₹12.34 sent")).toBeVisible();
    expect((await wallet("alice", alice.id)).balance).toBe(alice.balance - 1234);

    // The transaction detail shows both legs
    await page.getByRole("link", { name: "View transaction" }).click();
    await expect(page.getByRole("heading", { name: "e2e transfer" })).toBeVisible();
    // Next keeps the previous route mounted but hidden (Activity), so match visible elements only.
    await expect(page.getByText("Debits").filter({ visible: true })).toBeVisible();
    await expect(page.getByText("Another user's wallet").filter({ visible: true })).toBeVisible();
    await expect(page.getByText("Balanced").filter({ visible: true })).toBeVisible();

    // The wallet's entries show the debit at the top
    await page.goto(`/wallets/${alice.id}`);
    await expect(page.getByRole("row").nth(1)).toContainText("₹12.34");
  });

  test("double-clicking confirm creates exactly one transaction", async ({ page }) => {
    const { alice, bob } = await mainWallets();
    await ensureBalance("alice", alice.id, 50_00);
    const before = (await wallet("alice", alice.id)).balance;
    await signIn(page, "alice");
    await page.goto(`/transfer?from=${alice.id}&to=${bob.id}`);
    await page.fill("#amount", "1.11");
    await page.getByRole("button", { name: "Review" }).click();
    await page.getByRole("button", { name: "Confirm transfer" }).dblclick();
    await expect(page.getByText("₹1.11 sent")).toBeVisible();
    expect((await wallet("alice", alice.id)).balance).toBe(before - 111);
  });

  test("deposit through the mock provider credits exactly the amount", async ({ page }) => {
    const { alice } = await mainWallets();
    const before = (await wallet("alice", alice.id)).balance;
    await signIn(page, "alice");
    await page.goto(`/deposit?wallet=${alice.id}`);
    await page.fill("#amount", "25.50");
    await page.getByRole("button", { name: "Continue to payment" }).click();
    await expect(page.getByText("Waiting for the payment provider")).toBeVisible();
    await page.getByRole("button", { name: /^Pay / }).click();
    await expect(page.getByText("Payment confirmed by provider")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Succeeded")).toBeVisible();
    expect((await wallet("alice", alice.id)).balance).toBe(before + 2550);
  });

  test("a transfer above the balance is refused with a clear message", async ({ page }) => {
    const { alice, bob } = await mainWallets();
    await signIn(page, "alice");
    await page.goto(`/transfer?from=${alice.id}&to=${bob.id}`);
    await page.fill("#amount", "99999999");
    await page.getByRole("button", { name: "Review" }).click();
    await expect(page.getByText(/More than this wallet’s balance/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Confirm transfer" })).toHaveCount(0);
  });

  test("the server refuses insufficient funds too (balance changed after review)", async ({ page }) => {
    const { alice, bob } = await mainWallets();
    await ensureBalance("alice", alice.id, 10_00);
    const balance = (await wallet("alice", alice.id)).balance;
    await signIn(page, "alice");
    await page.goto(`/transfer?from=${alice.id}&to=${bob.id}`);
    await page.fill("#amount", (balance / 100).toFixed(2));
    await page.getByRole("button", { name: "Review" }).click();
    // Meanwhile, the balance drops elsewhere.
    await api("alice", "POST", "/transfers", {
      fromWalletId: alice.id,
      toWalletId: bob.id,
      amount: 1,
      currency: "INR",
    });
    await page.getByRole("button", { name: "Confirm transfer" }).click();
    await expect(page.getByText("Not enough balance in this wallet.")).toBeVisible();
  });

  test("bob opening alice's wallet gets not-found", async ({ page }) => {
    const { alice } = await mainWallets();
    await signIn(page, "bob");
    await page.goto(`/wallets/${alice.id}`);
    await expect(page.getByText("We couldn’t find that")).toBeVisible();
    await expect(page.getByText(alice.name, { exact: true })).toHaveCount(0);
  });
});
