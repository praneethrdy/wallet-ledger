import { expect, test } from "@playwright/test";

import { signIn } from "./helpers";

test.describe("session", () => {
  test("the browser never sees a token: only an HttpOnly session cookie", async ({ page, context }) => {
    await signIn(page, "alice");
    const cookies = await context.cookies();
    const session = cookies.find((c) => c.name === "wl_session");
    expect(session?.httpOnly).toBe(true);
    expect(session?.sameSite).toBe("Lax");
    expect(await page.evaluate(() => document.cookie)).not.toContain("wl_session");
    const html = await page.content();
    expect(html).not.toMatch(/eyJ[\w-]+\.eyJ[\w-]+\./); // no JWT anywhere in the page
  });

  /**
   * Silent refresh needs an access token that expires during the test. Run the backend with
   * SECURITY_AUTH_ACCESS_TOKEN_TTL=75s and set E2E_SHORT_TOKEN_TTL=1: the proxy refreshes when
   * under 60s remain, so after ~16s the next navigation must refresh and keep the user signed in.
   */
  test("navigation keeps working after the access token nears expiry (silent refresh)", async ({ page, context }) => {
    test.skip(!process.env.E2E_SHORT_TOKEN_TTL, "needs a backend running with a short access-token TTL");
    await signIn(page, "alice");
    const before = (await context.cookies()).find((c) => c.name === "wl_session")?.value;
    await page.waitForTimeout(17_000);
    await page.goto("/wallets");
    await expect(page.getByRole("heading", { name: "Wallets" })).toBeVisible();
    await page.goto("/transactions");
    await expect(page.getByRole("heading", { name: "Transactions" })).toBeVisible();
    const after = (await context.cookies()).find((c) => c.name === "wl_session")?.value;
    expect(after).not.toBe(before); // the session cookie was re-sealed with new tokens
  });

  test("after sign-out, protected pages send you to sign in", async ({ page }) => {
    await signIn(page, "alice");
    await page.getByRole("button", { name: "Account menu" }).click();
    await page.getByRole("menuitem", { name: "Sign out", exact: true }).click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login\?next=%2Fdashboard/);
  });

  test("sign-in ignores off-site redirect targets", async ({ page }) => {
    await page.goto("/login?next=//evil.example");
    await page.fill("#email", "alice@wallet.local");
    await page.fill("#password", "alice-pass-123");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page).toHaveURL(/localhost:\d+\/dashboard$/);
  });
});
