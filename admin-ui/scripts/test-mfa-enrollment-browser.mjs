// Isolated fixtures only: never enrolls a real admin or calls the live backend.
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import http from "node:http";
import { fileURLToPath } from "node:url";
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const root = path.resolve(fileURLToPath(new URL("../dist/security-s04/", import.meta.url)));
const output = fileURLToPath(new URL("../../outputs/security-review/", import.meta.url));
fs.mkdirSync(output, { recursive: true });
const server = http.createServer((req, res) => {
  const relative = new URL(req.url, "http://localhost").pathname.replace(/^\/admin-ui\//, "");
  const target = path.resolve(root, relative);
  if (!target.startsWith(root + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) {
    res.writeHead(404).end(); return;
  }
  res.setHeader("Content-Type", target.endsWith(".js") ? "text/javascript" : target.endsWith(".css") ? "text/css" : target.endsWith(".svg") ? "image/svg+xml" : "text/html");
  res.end(fs.readFileSync(target));
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
let browser;
try {
  browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport });
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    let enrollmentRequired = true;
    let setupRequests = 0;
    await page.route("**/api/**", async route => {
      const req = route.request(), pathname = new URL(req.url()).pathname;
      let status = 200, body = {};
      if (pathname === "/api/v1/auth/admin/session") { status = 401; body = { message: "Sign in" }; }
      else if (pathname === "/api/v1/auth/admin/login") {
        if (enrollmentRequired) body = { token: "fixture-setup-only", expiresIn: 300, mfaEnrollmentRequired: true };
        else { status = 401; body = { code: "ADMIN_MFA_REQUIRED", message: "Authenticator or recovery code required" }; }
      } else if (pathname.startsWith("/api/v1/auth/admin/mfa/enrollment")) {
        assert.equal(req.headers().authorization, "Bearer fixture-setup-only");
        setupRequests++;
        if (pathname.endsWith("/verify")) {
          assert.equal(req.postDataJSON().code, "123456");
          body = { enabled: true, recoveryCodes: Array.from({ length: 8 }, (_, i) => `ABCD-EFGH-000${i}`) };
          enrollmentRequired = false;
        } else body = { secret: "JBSWY3DPEHPK3PXP", otpauthUri: "otpauth://totp/GRUN%20test?secret=JBSWY3DPEHPK3PXP&issuer=GRUN%20test" };
      } else {
        assert.fail(`Unexpected authenticated API call during setup: ${pathname}`);
      }
      await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    });
    await page.goto(`http://127.0.0.1:${server.address().port}/admin-ui/index.html`);
    await page.getByLabel("Email", { exact: true }).fill("owner@example.com");
    await page.getByLabel("Password", { exact: true }).fill("StrongPass1!");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.getByRole("button", { name: "Set up authenticator" }).click();
    await page.getByRole("img", { name: "Authenticator enrollment QR code" }).waitFor();
    const dimensions = await page.getByRole("img", { name: "Authenticator enrollment QR code" }).boundingBox();
    assert.equal(dimensions.width, 184);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(output, `s04-setup-${viewport.width}.png`), fullPage: true });
    await page.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await page.getByRole("button", { name: "Verify and enable MFA" }).click();
    await page.getByRole("heading", { name: "Save your recovery codes" }).waitFor();
    assert.equal(await page.locator("li code").count(), 8);
    const finish = page.getByRole("button", { name: "Continue to sign in" });
    assert.equal(await finish.isDisabled(), true);
    await page.getByLabel("I saved my recovery codes").check();
    await finish.click();
    await page.getByLabel("Password", { exact: true }).fill("StrongPass1!");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.getByLabel("Authenticator or recovery code", { exact: true }).fill("ABCD-EFGH-0000");
    assert.equal(await page.getByLabel("Authenticator or recovery code", { exact: true }).inputValue(), "ABCD-EFGH-0000");
    assert.equal(setupRequests, 2);
    assert.deepEqual(errors, []);
    await context.close();
  }
  console.log("MFA setup browser fixtures passed: desktop/mobile, QR, recovery codes, no workspace access, recovery-code input.");
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
