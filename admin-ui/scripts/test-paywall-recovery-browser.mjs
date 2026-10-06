import assert from "node:assert/strict";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const root = path.resolve(fileURLToPath(new URL("../../src/main/resources/static/admin-ui/", import.meta.url)));
const output = fileURLToPath(new URL("../../outputs/paywall-review/", import.meta.url));
fs.mkdirSync(output, { recursive: true });
const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, "http://localhost").pathname;
  const relative = pathname.startsWith("/admin/") ? "index.html" : pathname.replace(/^\/admin-ui\//, "");
  const target = path.resolve(root, relative);
  if (!target.startsWith(root + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) {
    response.writeHead(404).end(); return;
  }
  response.setHeader("Content-Type", target.endsWith(".js") ? "text/javascript" : target.endsWith(".css") ? "text/css" : target.endsWith(".svg") ? "image/svg+xml" : "text/html");
  response.end(fs.readFileSync(target));
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
let browser;
try {
  browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await context.route("**/api/**", route => {
    const url = new URL(route.request().url());
    const json = value => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(value) });
    if (url.pathname.includes("/auth/admin/")) {
      const now = Date.now();
      return json({ token: "fixture-only", adminSession: { sessionId: "fixture-session", serverTime: new Date(now).toISOString(), idleExpiresAt: new Date(now + 900000).toISOString(), absoluteExpiresAt: new Date(now + 28800000).toISOString(), tokenExpiresAt: new Date(now + 3600000).toISOString(), idleTimeoutMs: 900000 } });
    }
    if (url.pathname === "/api/v1/admin/security/me") return json({ role: "OWNER", permissions: ["FINANCE_READ", "TECHNICAL_READ", "AUDIT_READ"], mfaEnabled: true });
    if (url.pathname.endsWith("/overview")) return json({ environment: "sandbox", providerReachable: true, metrics: [] });
    if (url.pathname.endsWith("/charts")) return json({ environment: "sandbox", providerReachable: true, charts: [] });
    if (url.pathname.endsWith("/verification-issues")) {
      const second = url.searchParams.get("page") === "1";
      return json({ content: [{ userId: second ? 44 : 18, status: "REQUIRES_REVIEW", productId: "grun_plus_monthly", resolvedProductId: "grun_pro_yearly", reasonCode: second ? "OWNERSHIP_CONFLICT" : "PROVIDER_NETWORK_ERROR", attempts: 8, createdAt: "2026-10-05T10:00:00Z", nextAttemptAt: second ? null : "2026-10-06T10:00:00Z" }], totalPages: 2, totalElements: 26 });
    }
    if (url.pathname.endsWith("/customer-evidence")) return json({ userId: 18, providerReachable: true, environment: "sandbox", backendSubscription: { planType: "PRO" }, verification: { status: "REQUIRES_REVIEW", productId: "grun_plus_monthly", resolvedProductId: "grun_pro_yearly", reasonCode: "PROVIDER_NETWORK_ERROR", attempts: 8 }, purchases: [] });
    return json({ content: [], totalPages: 0, totalElements: 0 });
  });
  await page.goto(`${origin}/admin/revenuecat/sandbox`);
  await page.getByRole("heading", { name: "Purchase verification issues" }).waitFor();
  await page.getByRole("cell", { name: "PROVIDER_NETWORK_ERROR", exact: true }).waitFor();
  await page.getByLabel("Internal user ID", { exact: true }).fill("18");
  await page.getByRole("button", { name: "Inspect", exact: true }).click();
  await page.getByText("Reason: PROVIDER_NETWORK_ERROR", { exact: true }).waitFor();
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await page.getByRole("cell", { name: "OWNERSHIP_CONFLICT", exact: true }).waitFor();
  await page.getByRole("button", { name: "Previous", exact: true }).click();
  await page.getByRole("cell", { name: "PROVIDER_NETWORK_ERROR", exact: true }).waitFor();
  await page.screenshot({ path: path.join(output, "admin-desktop.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  const closeNavigation = page.locator(".mobile-nav-close");
  if (await page.locator(".mobile-nav-backdrop.visible").count()) await closeNavigation.click();
  await page.waitForFunction(() => document.querySelector(".sidebar").getBoundingClientRect().right <= 0);
  await page.screenshot({ path: path.join(output, "admin-mobile.png"), fullPage: true });
  assert.deepEqual(errors, []);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true, "Mobile page must not overflow horizontally");
  console.log("Admin paywall browser test passed: issue queue, pagination, evidence inspection, desktop/mobile screenshots.");
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
