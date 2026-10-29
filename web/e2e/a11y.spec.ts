import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { mainWallets, signIn, type Who } from "./helpers";

/** No serious or critical axe violations on the main pages, in light and dark. */
async function audit(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(400); // let entrance animations settle (they affect contrast checks)
  const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
  const bad = result.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
  expect(
    bad.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(" ")).join(", ")})`),
    `${path}`,
  ).toEqual([]);
}

const PAGES: [Who | null, string[]][] = [
  [null, ["/", "/login", "/register"]],
  ["alice", ["/dashboard", "/wallets", "/transfer", "/deposit", "/transactions", "/settings"]],
  ["admin", ["/admin", "/admin/wallets", "/admin/reconciliation", "/support/transactions", "/support/webhook-events"]],
  ["support", ["/support/users"]],
];

for (const scheme of ["light", "dark"] as const) {
  test.describe(`accessibility (${scheme})`, () => {
    test.use({ colorScheme: scheme });

    for (const [who, paths] of PAGES) {
      test(`${who ?? "public"} pages`, async ({ page }) => {
        if (who) await signIn(page, who);
        for (const path of paths) await audit(page, path);
      });
    }

    test("wallet and transaction detail", async ({ page }) => {
      const { alice } = await mainWallets();
      await signIn(page, "alice");
      await audit(page, `/wallets/${alice.id}`);
      await page.goto("/transactions");
      await page.locator('a[href^="/transactions/"]').first().click();
      await page.waitForURL(/\/transactions\/[0-9a-f-]{36}$/);
      await audit(page, page.url());
    });
  });
}
