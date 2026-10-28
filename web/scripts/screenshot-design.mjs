// Screenshots of the /design style guide (light, dark, mobile) for review.
// Usage: npm run build && npm start -- -p 3100, then: node scripts/screenshot-design.mjs [outDir]
import { chromium } from "@playwright/test";
const out = process.argv[2] ?? "../docs/screenshots";
const browser = await chromium.launch();
for (const [name, opts] of [
  ["light", { colorScheme: "light", viewport: { width: 1440, height: 900 } }],
  ["dark", { colorScheme: "dark", viewport: { width: 1440, height: 900 } }],
  ["mobile-light", { colorScheme: "light", viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 }],
]) {
  const page = await browser.newPage(opts);
  await page.goto("http://localhost:3100/design", { waitUntil: "networkidle" });
  await page.waitForTimeout(900); // let count-ups and fades settle
  await page.screenshot({ path: `${out}/design-${name}.png`, fullPage: true });
  console.log("saved", name, await page.evaluate(() => document.documentElement.className));
  await page.close();
}
await browser.close();
