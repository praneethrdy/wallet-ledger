import { expect, test } from "@playwright/test";

import { api, ensureBalance, mainWallets, signIn, wallet } from "./helpers";

test.describe("staff console", () => {
  test("admin freezes alice's wallet, her transfer is blocked, then unfreezes", async ({ page, browser }) => {
    const { alice, bob } = await mainWallets();
    await ensureBalance("alice", alice.id, 10_00);
    await signIn(page, "admin");

    // UUIDv7 IDs share their leading (timestamp) digits: the table's short ID ends with the last 4.
    const row = () =>
      page
        .getByRole("row")
        .filter({ hasText: "alice@wallet.local" })
        .filter({ hasText: `…${alice.id.slice(-4)}` });
    await page.goto("/admin/wallets?email=alice");
    await row().getByRole("button", { name: "Freeze" }).click();
    await expect(page.getByRole("alertdialog")).toContainText("won’t be able to send");
    await page.getByRole("button", { name: "Freeze wallet" }).click();
    await expect(row()).toContainText("Frozen");

    try {
      // Alice, in her own browser, can't send from it any more.
      const ctx = await browser.newContext();
      const alicePage = await ctx.newPage();
      await signIn(alicePage, "alice");
      await alicePage.goto(`/wallets/${alice.id}`);
      await expect(alicePage.getByText("This wallet is frozen")).toBeVisible();
      const refused = await api<{ code: string }>("alice", "POST", "/transfers", {
        fromWalletId: alice.id,
        toWalletId: bob.id,
        amount: 100,
        currency: "INR",
      });
      expect(refused.code).toBe("WALLET_FROZEN");
      await ctx.close();
    } finally {
      await row().getByRole("button", { name: "Unfreeze" }).click();
      await page.getByRole("button", { name: "Unfreeze wallet" }).click();
      await expect(row()).toContainText("Active");
    }
    expect((await wallet("alice", alice.id)).status).toBe("ACTIVE");
  });

  test("admin reverses a transfer with a reason; reversing again is refused", async ({ page }) => {
    const { alice, bob } = await mainWallets();
    await ensureBalance("alice", alice.id, 10_00);
    const tx = await api<{ id: string; reference: string }>("alice", "POST", "/transfers", {
      fromWalletId: alice.id,
      toWalletId: bob.id,
      amount: 2_00,
      currency: "INR",
      note: "e2e to reverse",
    });
    await signIn(page, "admin");

    await page.goto(`/support/transactions?reference=${tx.reference}`);
    await page.getByText(tx.reference, { exact: true }).click();
    await page.getByRole("link", { name: /Reverse/ }).click();
    await page.getByRole("button", { name: /Reverse transaction/ }).click();
    await expect(page.getByText("Say why this is being reversed")).toBeVisible();
    await page.fill("#reason", "e2e: wrong recipient");
    await page.getByRole("button", { name: /Reverse transaction/ }).click();
    await expect(page.getByRole("alertdialog")).toContainText(
      "This posts a new REVERSAL transaction; history is not changed",
    );
    await page.getByRole("button", { name: "Post reversal" }).click();
    await expect(page.getByText("This is a reversal")).toBeVisible();

    // The original now says "Reversed by" (both in the staff view and for alice)
    await page.goto(`/support/transactions/${tx.id}`);
    await expect(page.getByText("This transaction was reversed")).toBeVisible();
    await expect(page.getByRole("link", { name: /Reverse/ })).toHaveCount(0);
    const original = await api<{ reversedByTransactionId: string | null }>("alice", "GET", `/transactions/${tx.id}`);
    expect(original.reversedByTransactionId).not.toBeNull();

    // Trying again: the page explains it's already reversed; the API refuses with ALREADY_REVERSED
    await page.goto(`/admin/transactions/${tx.id}/reverse`);
    await expect(page.getByText("Already reversed")).toBeVisible();
    const again = await api<{ code: string }>("admin", "POST", `/admin/transactions/${tx.id}/reverse`, {
      reason: "again",
    });
    expect(again.code).toBe("ALREADY_REVERSED");
  });

  test("admin runs reconciliation and sees the result", async ({ page }) => {
    await signIn(page, "admin");
    await page.goto("/admin/reconciliation");
    await page.getByRole("button", { name: "Run now" }).click();
    await expect(page.getByRole("heading", { name: /^Run of/ })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("Transactions checked", { exact: true }).filter({ visible: true })).toBeVisible();
    await expect(page.getByText("No findings").filter({ visible: true }).first()).toBeVisible();
  });

  test("support finds alice, sees her wallets and webhook events, but no admin actions", async ({ page }) => {
    await signIn(page, "support");
    await expect(page).toHaveURL(/\/support\/users$/);
    await expect(page.getByText("Your lookups are recorded in the audit log")).toBeVisible();

    await page.fill("#u-email", "ALICE");
    await page.getByRole("button", { name: "Search" }).click();
    await page.getByRole("link", { name: "alice@wallet.local" }).click();
    await expect(page.getByRole("heading", { name: "alice@wallet.local" })).toBeVisible();
    await expect(page.getByRole("cell", { name: /Main/ }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: "Manage wallets" })).toHaveCount(0);

    await page.goto("/support/webhook-events");
    const first = page.locator("details").first();
    await first.locator("summary").click();
    await expect(first.locator("pre")).toContainText('"type"');

    await page.goto("/admin/reconciliation");
    await expect(page.getByRole("heading", { name: "Reconciliation" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Run now" })).toHaveCount(0);

    await page.goto("/admin/wallets");
    await expect(page).toHaveURL(/\/support\/users$/);
  });

  test("customers can't reach the staff console or its BFF routes", async ({ page }) => {
    await signIn(page, "alice");
    await page.goto("/support/users");
    await expect(page).toHaveURL(/\/dashboard$/);
    const status = await page.evaluate(async () => (await fetch("/bff/admin/wallets")).status);
    expect(status).toBe(403);
  });
});
