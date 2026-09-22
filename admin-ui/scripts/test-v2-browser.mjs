// Isolated built-UI smoke test. All API responses are fixtures; no live backend.
// Use PLAYWRIGHT_MODULE (absolute import URL) / BROWSER_EXECUTABLE when not installed locally.
import assert from "node:assert/strict";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const root = fileURLToPath(new URL("../../src/main/resources/static/admin-ui/", import.meta.url));
const routes = JSON.parse(fs.readFileSync(path.join(root, "routes.json"), "utf8"));
const server = http.createServer((request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
  const relative = Object.values(routes).includes(pathname.replace(/\/$/, "")) ? "index.html" : pathname.replace(/^\/admin-ui\//, "");
  const target = path.resolve(root, relative);
  if (!target.startsWith(root) || !fs.existsSync(target) || !fs.statSync(target).isFile()) { response.writeHead(404).end(); return; }
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
  page.setDefaultTimeout(10000);
  const assertAccessibleSurface = async label => {
    const violations = await page.evaluate(() => {
      const visible = element => {
        const style = getComputedStyle(element), box = element.getBoundingClientRect();
        return style.display !== "none" && style.visibility !== "hidden" && box.width > 0 && box.height > 0;
      };
      const name = element => (element.getAttribute("aria-label") ||
        (element.getAttribute("aria-labelledby") || "").split(/\s+/).map(id => document.getElementById(id)?.textContent || "").join(" ") ||
        ("labels" in element ? [...element.labels].map(item => item.textContent || "").join(" ") : "") ||
        element.getAttribute("title") || element.textContent || element.getAttribute("alt") || "").trim();
      const unnamed = [...document.querySelectorAll("button, input:not([type=hidden]), select, textarea, [role=button], [role=img]")]
        .filter(element => visible(element) && !name(element))
        .map(element => `${element.tagName.toLowerCase()}${element.id ? `#${element.id}` : ""}${element.getAttribute("role") ? `[role=${element.getAttribute("role")}]` : ""}`);
      const ids = [...document.querySelectorAll("[id]")].map(element => element.id).filter(Boolean);
      const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
      const missingAlt = [...document.querySelectorAll("img")].filter(image => visible(image) && !image.hasAttribute("alt")).map(image => image.src);
      return { mainCount: document.querySelectorAll("main#admin-main").length, unnamed, duplicateIds, missingAlt };
    });
    assert.equal(violations.mainCount, 1, `${label}: one main landmark is required`);
    assert.deepEqual(violations.unnamed, [], `${label}: visible controls and charts need accessible names`);
    assert.deepEqual(violations.duplicateIds, [], `${label}: element IDs must be unique`);
    assert.deepEqual(violations.missingAlt, [], `${label}: visible images need alt text`);
  };
  const errors = [], calls = [], chunks = [];
  let permissions = ["AUDIT_READ", "TECHNICAL_READ"], role = "OWNER", denyProfile = false, breakPush = false, errorReadFailure = false, errorNetworkFailure = false, inboxView = false;
  const errorQueries = [], ownerAlertQueries = [], ownerAlertActions = [], errorGroupActions = [], aiQueries = [], barcodeUpdates = [], clientTelemetry = [];
  const errorEvent = { id: 41, occurredAt: "2026-09-17T12:00:00Z", status: 500, source: "BACKEND", method: "POST", route: "/api/v1/products/{id}", correlationId: "9dcf870e-d9a0-4b27-9748-ccca9afcd5e9", errorCode: "UNEXPECTED_ERROR", exceptionType: "java.lang.IllegalStateException", technicalLocation: "com.grun.calorietracker.fixture.Test.run:42", durationMs: 140 };
  const contextUser = { id: 42, name: "Context User", email: "context@example.com", role: "STANDARD", accountEnabled: true, accountLocked: false, emailVerified: true, marketRegion: "UK_IE", preferredLanguage: "EN" };
  page.on("pageerror", error => errors.push(error.message));
  page.on("request", request => { if (request.url().endsWith(".js")) chunks.push(request.url()); });
  await context.route("**/*", async route => {
    const request = route.request(), url = new URL(request.url());
    if (url.origin !== origin) return route.abort();
    if (breakPush && /PushDeliveryView-.*\.js$/.test(url.pathname)) return route.abort();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    calls.push(url.pathname);
    const json = body => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
    if (request.method() === "POST" && url.pathname === "/api/v1/error-telemetry/client") {
      clientTelemetry.push({ body: JSON.parse(request.postData() ?? "{}"), authorization: request.headers().authorization });
      return route.fulfill({ status: 202, body: "" });
    }
    if (url.pathname === "/api/v1/auth/admin/session" || url.pathname === "/api/v1/auth/admin/refresh") {
      const now = Date.now();
      return json({ token: "fixture-only", adminSession: { sessionId: "fixture-session", serverTime: new Date(now).toISOString(), idleExpiresAt: new Date(now + 900000).toISOString(), absoluteExpiresAt: new Date(now + 28800000).toISOString(), tokenExpiresAt: new Date(now + 3600000).toISOString(), idleTimeoutMs: 900000 } });
    }
    if (url.pathname === "/api/v1/admin/security/me") return denyProfile ? route.fulfill({ status: 503, body: "Fixture unavailable" }) : json({ role, permissions, mfaEnabled: true });
    if (url.pathname === "/api/v1/admin/errors") {
      errorQueries.push(url.searchParams);
      if (errorNetworkFailure) { errorNetworkFailure = false; return route.abort("connectionfailed"); }
      if (errorReadFailure) return route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ message: "Fixture unavailable" }) });
      const empty = url.searchParams.get("status") === "422", second = url.searchParams.get("page") === "1";
      return json({ content: empty ? [] : [{ ...errorEvent, id: second ? 42 : 41 }], page: second ? 1 : 0, size: 25, totalElements: empty ? 0 : 26, totalPages: empty ? 0 : 2 });
    }
    if (url.pathname === "/api/v1/admin/errors/health") return json({ queued: 2, saved: 26, dropped: 1, writeFailures: 3, retentionDays: 30, maxRows: 100000 });
    if (url.pathname === "/api/v1/admin/errors/groups") return json([{ fingerprint: "a".repeat(64), source: "BACKEND", status: 500, method: "POST", route: errorEvent.route, errorCode: errorEvent.errorCode, occurrenceCount: 7, firstOccurredAt: "2026-09-17T10:00:00Z", lastOccurredAt: errorEvent.occurredAt, maxDurationMs: 540, affectedVersions: 0, lifecycleStatus: inboxView ? "INVESTIGATING" : "NEW" }]);
    if (request.method() === "POST" && /^\/api\/v1\/admin\/errors\/groups\/[0-9a-f]{64}\/state$/.test(url.pathname)) {
      const proof=request.headers()["x-admin-reauth-token"];
      if(!proof)return route.fulfill({status:428,contentType:"application/json",body:JSON.stringify({code:"FRESH_OWNER_MFA_REQUIRED",message:"Fresh owner MFA re-authentication is required.",requiredPurpose:"OWNER_ERROR_ACTION"})});
      errorGroupActions.push({body:JSON.parse(request.postData()??"{}"),token:proof}); return route.fulfill({status:204,body:""});
    }
    if (/^\/api\/v1\/admin\/errors\/\d+$/.test(url.pathname)) return json({ ...errorEvent, id: Number(url.pathname.split("/").at(-1)) });
    if (url.pathname === "/api/v1/admin/owner-alerts/daily-summary") return json({ date: url.searchParams.get("date"), generatedAt: "2026-09-17T22:55:00Z", backendErrorGroups: 2, backendErrorOccurrences: 7, financialApprovalRequests: 3, operationalApprovalRequests: 1, pendingApprovals: 2, approvedToday: 4, rejectedToday: 1, sentAlerts: 5, failedAlerts: 1, newErrorGroups: 2, investigatingErrorGroups: 3, resolvedErrorGroups: 4, reopenedErrorGroups: 1 });
    if (url.pathname === "/api/v1/admin/owner-alerts") {
      ownerAlertQueries.push(url.searchParams);
      const failed = url.searchParams.get("status") === "FAILED";
      return json({ content: [{ id: failed ? 72 : 71, category: "BACKEND_ERROR", severity: "CRITICAL", status: failed ? "FAILED" : "RETRY", titleEn: "Critical backend error", titleTr: "Kritik backend hatası", messageEn: "2 grouped failures require attention.", messageTr: "Gruplanmış 2 hata inceleme bekliyor.", targetPath: `/admin/system/errors?correlationId=${errorEvent.correlationId}`, occurrenceCount: 2, firstOccurredAt: "2026-09-17T10:00:00Z", lastOccurredAt: "2026-09-17T12:00:00Z", attemptCount: failed ? 5 : 1, nextAttemptAt: failed ? undefined : "2026-09-17T12:05:00Z", lastErrorType: "MailSendException" }], page: Number(url.searchParams.get("page") ?? 0), size: Number(url.searchParams.get("size") ?? 25), totalElements: 1, totalPages: 1, first: true, last: true });
    }
    if (request.method() === "POST" && url.pathname === "/api/v1/admin/security/mfa/reauthenticate") return json({ token: "fresh-owner-proof" });
    if (request.method() === "POST" && /^\/api\/v1\/admin\/owner-alerts\/\d+\/(retry|acknowledge)$/.test(url.pathname)) { ownerAlertActions.push({ path: url.pathname, body: JSON.parse(request.postData() ?? "{}"), token: request.headers()["x-admin-reauth-token"] }); return json({ id: 72, status: "ACKNOWLEDGED" }); }
    if (request.method() === "POST" && url.pathname === "/api/v1/admin/approvals") return json({ id: 901, actionType: "SUBSCRIPTION_UPDATE", status: "PENDING", makerEmail: "owner@example.com", targetKey: "42", requestReason: "Update subscription for user 42", payload: JSON.parse(request.postData() ?? "{}").payload, createdAt: "2026-09-17T12:00:00Z", expiresAt: "2026-09-18T12:00:00Z" });
    if (url.pathname === "/api/v1/admin/approvals") return json({ content: [{ id: 901, actionType: "SUBSCRIPTION_UPDATE", status: "PENDING", makerEmail: "finance@example.com", targetKey: "42", requestReason: "Correct verified billing state", payload: { planType: "PRO" }, createdAt: "2026-09-17T12:00:00Z", expiresAt: "2026-09-18T12:00:00Z" }], page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true });
    if (url.pathname === "/api/v1/notifications") return json({ content: [{ id: 801, type: "system_alert", severity: "CRITICAL", source: "MAIL_PROVIDER", message: "Mail delivery needs attention.", targetRoute: "mail", read: false, createdAt: "2026-09-17T12:10:00Z" }], page: 0, size: 25, totalElements: 1, totalPages: 1, first: true, last: true });
    if (url.pathname === "/api/v1/admin/products/contributions") return json({ content: [{ id: 301, productName: "Fixture cereal", barcode: "4006381333931", status: "PENDING", createdAt: "2026-09-17T11:30:00Z" }], page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true });
    if (request.method() === "PATCH" && url.pathname === "/api/v1/admin/products/77/barcode") {
      const body = JSON.parse(request.postData() ?? "{}"); barcodeUpdates.push(body);
      return json({ id: 77, barcode: body.barcode, normalizedBarcode: body.barcode, productName: "Fixture product", brand: "Fixture", verificationStatus: "VERIFIED", imageStatus: "APPROVED" });
    }
    if (request.method() !== "GET") throw new Error(`Unexpected mutation ${request.method()} ${url.pathname}`);
    if (url.pathname === "/api/v1/admin/audits") return json({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    if (url.pathname === "/api/v1/admin/users") return json({ content: [contextUser], page: 0, size: 25, totalElements: 1, totalPages: 1, first: true, last: true });
    if (url.pathname === "/api/v1/admin/users/42/customer-360") return json({
      profile: contextUser,
      subscription: { plan: "PRO", status: "ACTIVE", billingPeriod: "MONTHLY", autoRenew: true, activeFeatures: ["BARCODE_SCANNER"] },
      ai: { totalRequests: 0, recentStatusCounts: {}, recentRequestTypeCounts: {} },
      notifications: { unread: 0, recent: [] },
      security: { activeSessions: 0, recentEvents: [] },
      consent: { total: 0, recent: [] },
      activity: { foodLogCount: 0, productEventCount: 0, recentProductEvents: [] },
      supportNotes: []
    });
    if (url.pathname === "/api/v1/admin/subscriptions/features") return json([]);
    if (url.pathname === "/api/v1/admin/ai-credit-pricing") return json([]);
    if (url.pathname === "/api/v1/admin/revenuecat/config") return json({ productionReady: false, warnings: [], missingRequiredConfig: [] });
    if (url.pathname === "/api/v1/admin/subscriptions/users/42/features") return json({ planType: "PRO", barcodeScanner: true, activeEntitlement: true });
    if (url.pathname === "/api/v1/admin/subscriptions/users/42") return json({ planType: "PRO", status: "ACTIVE", billingPeriod: "MONTHLY", autoRenew: true, aiMonthlyQuota: 150, aiUsedThisPeriod: 0 });
    if (url.pathname === "/api/v1/admin/ai/requests") { aiQueries.push(url.searchParams); return json({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, first: true, last: true }); }
    if (url.pathname === "/api/v1/admin/ai/requests/summary") return json({ windowHours: 24, requestStatuses: [], segments: [], estimatedCostByCurrency: {}, subscriptionRevenueByCurrency: {}, costToRevenueRatioByCurrency: {} });
    if (url.pathname === "/api/v1/admin/ai/monitoring/policy") return json({ version: 1, circuitOpen: false, failureRateThreshold: 0.2, rejectionRateThreshold: 0.4, maxTokensPer24Hours: 1000000, maxCostPer24Hours: 20, costCurrency: "USD", activePhotoProvider: "OPENAI" });
    if (url.pathname === "/api/v1/admin/ai/meal-drafts/501/inspection") return json({ requestId: 501, correlationId: errorEvent.correlationId, userId: 42, userEmail: "context@example.com", requestType: "PHOTO_MEAL_LOG", provider: "OPENAI", model: "fixture-vision", status: "CONFIRMED", createdAt: "2026-09-17T12:00:00Z", requestContext: { source: "camera", language: "en" }, result: { items: [{ name: "Apple", quantity: 1, unit: "piece", confidence: 0.98 }] }, confirmation: { confirmedItems: 1 }, correctionSummary: "No correction required" });
    if (url.pathname === "/api/v1/admin/product-intakes") return json({ content: [{ id: 12, source: "USER_OCR", status: "SUBMITTED", marketRegion: "UK_IE", barcode: "3017620422003", resolutionMode: "UPDATE_EXISTING", riskLevel: "MEDIUM", createdAt: "2026-09-17T11:00:00Z", updatedAt: "2026-09-17T12:00:00Z" }], page: 0, size: 25, totalElements: 1, totalPages: 1, first: true, last: true });
    if (url.pathname === "/api/v1/admin/product-intakes/12") return json({ summary: { id: 12, source: "USER_OCR", status: "SUBMITTED", marketRegion: "UK_IE", barcode: "3017620422003", resolutionMode: "UPDATE_EXISTING", riskLevel: "MEDIUM" }, fieldComparisons: [{ field: "calories", submittedValue: 120, catalogValue: 118, equal: false, highImpact: false }], warnings: [], evidence: [], corroboratingEvidence: [], ocrRuns: [{ id: 44, correlationId: errorEvent.correlationId, parserVersion: "nutrition-v4", model: "gemini-fixture", fallbackInvoked: true, v3Fields: { calories: 110 }, v4Fields: { calories: 120 }, fallbackFields: { calories: 118 }, confirmedFields: { calories: 120 }, v3ExactMatchRate: 0, v4ExactMatchRate: 1, fallbackExactMatchRate: 0, v3BasisExact: false, v4BasisExact: true, fallbackBasisExact: true, latencyMs: 240, estimatedCostUsd: 0.002, reconciliation: { selected: "v4" }, createdAt: "2026-09-17T12:00:00Z" }] });
    if (url.pathname === "/api/v1/admin/products/review") {
      const barcode = "3017620422003";
      const content = url.searchParams.get("query") === barcode ? [{ id: 77, barcode, normalizedBarcode: barcode, productName: "Fixture product", brand: "Fixture", verificationStatus: "VERIFIED", imageStatus: "APPROVED" }] : [];
      return json({ content, page: 0, size: Number(url.searchParams.get("size") ?? 20), totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true });
    }
    if (url.pathname === "/api/v1/admin/system/push-monitoring") return json({ activeTokensByProvider: {}, enabled: false });
    return route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "Unexpected fixture endpoint" }) });
  });
  // V1 bookmarks still work and are replaced without an extra history entry.
  await page.goto(`${origin}/admin-ui/index.html#/audits`);
  await page.getByRole("heading", { name: "Admin action audits" }).waitFor();
  assert.equal(await page.evaluate(() => {
    const selector = "a[href], button:not([disabled]), input:not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex='-1'])";
    return document.querySelector(selector)?.classList.contains("skip-link") === true;
  }), true, "The skip link must be the first keyboard target in document order.");
  await page.locator(".skip-link").focus();
  await page.keyboard.press("Enter");
  assert.equal(await page.locator("#admin-main").evaluate(element => element === document.activeElement), true, "The skip link must move focus to main content.");
  await assertAccessibleSurface("audit log");
  assert.equal(new URL(page.url()).pathname, "/admin/audits");
  assert.equal(new URL(page.url()).hash, "");
  assert.ok(!chunks.some(url => /RecipeAdminView-|AdminEChart-|UsersView-/.test(url)), "Unvisited pages/charts must not load.");
  const actionFilter = page.getByRole("combobox", { name: /^Action type/ });
  await page.getByRole("navigation", { name: "Table pagination" }).waitFor();
  await page.getByRole("button", { name: "Refresh Admin action audits", exact: true }).waitFor();
  await actionFilter.selectOption("SUBSCRIPTION_UPDATE");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("tr");
  await page.getByRole("heading", { name: "Denetim kayıtları", exact: true }).waitFor();
  assert.equal(await actionFilter.inputValue(), "SUBSCRIPTION_UPDATE", "Changing locale must preserve page state.");
  await page.getByRole("navigation", { name: "Tablo sayfalama" }).waitFor();
  await page.getByRole("button", { name: "Admin action audits verilerini yenile", exact: true }).waitFor();
  await page.getByText("Sonuç bulunamadı", { exact: true }).waitFor();
  assert.equal(await page.getByRole("button", { name: "Önceki sayfa", exact: true }).isDisabled(), true);
  await page.getByRole("combobox", { name: "Dil", exact: true }).selectOption("en");
  await page.getByRole("navigation", { name: "Table pagination" }).waitFor();
  assert.equal(await actionFilter.inputValue(), "SUBSCRIPTION_UPDATE");
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("tr");
  assert.equal(await page.locator("html").getAttribute("lang"), "tr");
  await page.getByLabel("Sayfa ara", { exact: true }).fill("push");
  const popupPromise = context.waitForEvent("page");
  await page.getByRole("link", { name: "Push teslimatı Cihaz token takibi" }).click({ modifiers: ["Control"] });
  const popup = await popupPromise;
  await popup.getByRole("heading", { name: "Push delivery monitoring" }).waitFor();
  assert.equal(new URL(popup.url()).pathname, "/admin/notifications/push-delivery");
  await popup.close();
  await page.getByRole("link", { name: "Push teslimatı Cihaz token takibi" }).click();
  await page.getByRole("heading", { name: "Push delivery monitoring" }).waitFor();
  await page.goBack();
  await page.getByRole("heading", { name: "Admin action audits" }).waitFor();
  await page.goForward();
  await page.getByRole("heading", { name: "Push delivery monitoring" }).waitFor();
  await page.reload();
  await page.getByRole("heading", { name: "Push teslimatı", exact: true }).waitFor();
  assert.equal(await page.getByRole("combobox", { name: "Dil", exact: true }).inputValue(), "tr");
  for (const width of [390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    if (width === 390) await page.waitForFunction(() => document.querySelector("#admin-navigation").getBoundingClientRect().right <= 0);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `No horizontal document overflow at ${width}px`);
    if (process.env.V2_SCREENSHOT_DIR && width !== 768) await page.screenshot({ path: path.join(process.env.V2_SCREENSHOT_DIR, `admin-v2-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Menüyü aç", exact: true }).click();
  await page.getByLabel("Sayfa ara", { exact: true }).fill("denetim");
  await page.getByRole("link", { name: "Denetim kayıtları Yönetici işlemleri" }).click();
  await page.getByRole("heading", { name: "Admin action audits" }).waitFor();
  assert.equal(await page.getByRole("button", { name: "Menüyü aç", exact: true }).getAttribute("aria-expanded"), "false");
  await page.getByRole("button", { name: "Menüyü aç", exact: true }).click();
  await page.keyboard.press("Escape");
  await page.waitForFunction(() => document.activeElement?.classList.contains("mobile-nav-trigger"));
  permissions = ["CATALOG_READ", "CATALOG_MANAGE"];
  await page.goto(`${origin}/admin/products`);
  await page.getByRole("heading", { name: "Barkodu tara veya gir", exact: true }).waitFor();
  const barcodeInput = page.getByRole("textbox", { name: "Barkod", exact: true });
  await barcodeInput.fill("3017620422004");
  await page.getByRole("button", { name: "Ürünü bul", exact: true }).click();
  await page.getByText("Barkod kontrol basamağı geçersiz.", { exact: true }).waitFor();
  await barcodeInput.fill("3017620422003");
  await barcodeInput.press("Enter");
  await page.getByRole("dialog", { name: "Product review detail" }).waitFor();
  assert.equal(new URL(page.url()).searchParams.get("query"), "3017620422003", "Barcode lookup must preserve the exact product filter.");
  assert.equal(new URL(page.url()).searchParams.get("productId"), "77", "The open product must be addressable.");
  await page.reload();
  await page.getByRole("dialog", { name: "Product review detail" }).waitFor();
  await page.getByRole("textbox", { name: "Yeni barkod", exact: true }).fill("4006381333931");
  await page.getByRole("textbox", { name: "Değişiklik gerekçesi", exact: true }).fill("Fixture label verified");
  await page.getByRole("button", { name: "Barkodu değiştir", exact: true }).click();
  await page.getByRole("alertdialog", { name: "Barkod değiştirilsin mi?", exact: true }).waitFor();
  await page.getByRole("button", { name: "Barkodu güncelle", exact: true }).click();
  await page.getByText("Mevcut barkod: 4006381333931", { exact: true }).waitFor();
  assert.deepEqual(barcodeUpdates, [{ barcode: "4006381333931", reason: "Fixture label verified" }]);
  await page.getByRole("dialog", { name: "Product review detail", exact: true }).getByLabel("Close", { exact: true }).click();
  await barcodeInput.fill("96385074");
  await barcodeInput.press("Enter");
  const candidateLink = page.getByRole("link", { name: "İnceleme adayı oluştur", exact: true });
  await candidateLink.waitFor();
  assert.equal(new URL(await candidateLink.getAttribute("href"), origin).searchParams.get("barcode"), "96385074");
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), "Barcode workflow must fit mobile width.");
  permissions = ["USERS_READ", "FINANCE_READ"];
  calls.length = 0;
  await page.goto(`${origin}/admin/users`);
  await page.getByRole("heading", { name: "App users", exact: true }).waitFor();
  await page.getByLabel("Search", { exact: true }).fill("context@example.com");
  const userPlanFilter = page.locator(".user-filter-panel label").filter({ hasText: "Plan" }).locator("select");
  await userPlanFilter.selectOption("PRO");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await page.getByText("Context User", { exact: true }).click();
  await page.getByRole("dialog", { name: "Customer 360", exact: true }).waitFor();
  await page.getByRole("button", { name: "Subscription", exact: true }).click();
  await page.getByRole("button", { name: "Open entitlement controls", exact: true }).click();
  await page.getByText("KULLANICI BAĞLAMI", { exact: true }).waitFor();
  assert.equal(new URL(page.url()).pathname, "/admin/subscriptions/access");
  assert.equal(new URL(page.url()).searchParams.get("userId"), "42");
  assert.equal(new URL(page.url()).searchParams.get("userEmail"), "context@example.com");
  const contextUserPicker = page.locator(".user-search-field input[role='combobox']");
  assert.equal(await contextUserPicker.inputValue(), "context@example.com");
  assert.ok(calls.includes("/api/v1/admin/subscriptions/users/42/features"), "Context transition must load the selected user's access.");
  await page.reload();
  await page.getByText("KULLANICI BAĞLAMI", { exact: true }).waitFor();
  assert.equal(await contextUserPicker.inputValue(), "context@example.com", "Reload must preserve the selected user context.");
  await page.getByRole("button", { name: "Kullanıcı bağlamını temizle", exact: true }).click();
  assert.equal(new URL(page.url()).search, "", "Clearing context must remove user identity from the URL.");
  await page.goBack();
  await page.getByRole("dialog", { name: "Customer 360", exact: true }).waitFor();
  assert.equal(await page.getByLabel("Search", { exact: true }).inputValue(), "context@example.com", "Back navigation must restore the user search.");
  assert.equal(await userPlanFilter.inputValue(), "PRO", "Back navigation must restore user filters.");
  assert.equal(new URL(page.url()).searchParams.get("userId"), "42", "Back navigation must reopen the selected Customer 360 user.");
  await page.getByRole("dialog", { name: "Customer 360", exact: true }).getByLabel("Close", { exact: true }).click();
  permissions = ["USERS_READ", "TECHNICAL_READ"];
  await page.goto(`${origin}/admin/users`);
  await page.getByText("Context User", { exact: true }).click();
  await page.getByRole("dialog", { name: "Customer 360", exact: true }).waitFor();
  await page.getByRole("button", { name: "AI & refunds", exact: true }).click();
  await page.getByRole("button", { name: "Open AI requests", exact: true }).click();
  await page.getByText("KULLANICI BAĞLAMI", { exact: true }).waitFor();
  assert.equal(new URL(page.url()).pathname, "/admin/ai/requests");
  assert.equal(new URL(page.url()).searchParams.get("userId"), "42");
  assert.equal(aiQueries.at(-1).get("userId"), "42", "AI operations must request only the selected user's rows.");
  await page.reload();
  await page.getByText("KULLANICI BAĞLAMI", { exact: true }).waitFor();
  assert.equal(aiQueries.at(-1).get("userId"), "42", "AI user filter must survive reload.");
  await page.getByRole("button", { name: "Kullanıcı bağlamını temizle", exact: true }).click();
  await page.waitForFunction(() => !window.location.search);
  await page.waitForFunction(() => document.body.textContent?.includes("AI requests and OCR review"));
  assert.equal(aiQueries.at(-1).get("userId"), null, "Clearing AI context must restore the unfiltered request list.");
  await page.goto(`${origin}/admin/ai/requests?requestType=PHOTO_MEAL_LOG&status=FAILED&refundableOnly=true&page=1&size=10&requestId=501`);
  await page.getByRole("dialog", { name: "Request #501", exact: true }).waitFor();
  await page.getByText(/OCR \/ AI (extraction|çıkarımı)/).first().waitFor();
  const aiFilterSelects = page.locator(".ai-review-filter-grid select");
  assert.equal(await aiFilterSelects.nth(0).inputValue(), "PHOTO_MEAL_LOG");
  assert.equal(await aiFilterSelects.nth(1).inputValue(), "FAILED");
  assert.equal(await page.locator(".ai-review-filter-grid input[type='checkbox']").isChecked(), true);
  assert.equal(aiQueries.at(-1).get("requestType"), "PHOTO_MEAL_LOG");
  assert.equal(aiQueries.at(-1).get("status"), "FAILED");
  assert.equal(aiQueries.at(-1).get("refundableOnly"), "true");
  assert.equal(aiQueries.at(-1).get("page"), "1");
  assert.equal(aiQueries.at(-1).get("size"), "10");
  await page.reload();
  await page.getByRole("dialog", { name: "Request #501", exact: true }).waitFor();
  assert.equal(await aiFilterSelects.nth(0).inputValue(), "PHOTO_MEAL_LOG", "AI request filters must survive reload.");
  await page.getByRole("button", { name: "Close AI request inspection", exact: true }).click();
  assert.equal(new URL(page.url()).searchParams.has("requestId"), false, "Closing AI inspection must clear only its addressable selection.");
  assert.equal(new URL(page.url()).searchParams.get("requestType"), "PHOTO_MEAL_LOG", "Closing inspection must preserve list filters.");
  assert.equal(new URL(page.url()).searchParams.get("page"), "1", "Closing inspection must preserve pagination.");
  await page.goto(`${origin}/admin/ai/requests?requestId=501`);
  await page.getByRole("dialog", { name: "Request #501", exact: true }).waitFor();
  await page.getByRole("link", { name: /Hata Merkezi'nde aç|Open in Error Center/ }).click();
  await page.getByRole("heading", { name: /İstek hata geçmişi|Request error history/ }).waitFor();
  assert.equal(new URL(page.url()).pathname, "/admin/system/errors");
  assert.equal(errorQueries.at(-1).get("correlationId"), errorEvent.correlationId, "AI inspection must open the exact correlated owner error history.");
  permissions = ["CATALOG_READ", "CATALOG_MANAGE"];
  await page.goto(`${origin}/admin/products/contributions`);
  await page.getByText("#12", { exact: true }).click();
  await page.getByRole("dialog", { name: "Product intake review", exact: true }).waitFor();
  await page.getByRole("region", { name: /OCR çalışması|OCR run/ }).waitFor();
  await page.getByText("100%", { exact: true }).waitFor();
  await page.getByRole("link", { name: /Hata Merkezi|Error Center/ }).waitFor();
  permissions = ["AUDIT_READ"];
  calls.length = 0;
  await page.goto(`${origin}/admin/notifications/push-delivery?case=denied`);
  await page.getByRole("heading", { name: "Admin action audits" }).waitFor();
  assert.ok(!calls.includes("/api/v1/admin/system/push-monitoring"), "Forbidden deep link must not fetch page data.");
  assert.equal(new URL(page.url()).pathname, "/admin/audits");
  permissions = [];
  await page.reload();
  await page.getByText("Erişebileceğiniz sayfa bulunmuyor", { exact: true }).waitFor();
  denyProfile = true;
  await page.reload();
  await page.getByRole("button", { name: "Yetki kontrolünü tekrar dene" }).waitFor();
  denyProfile = false; permissions = ["AUDIT_READ"];
  await page.getByRole("button", { name: "Yetki kontrolünü tekrar dene" }).click();
  await page.getByRole("heading", { name: "Admin action audits" }).waitFor();
  assert.deepEqual(errors, []);
  // A failed lazy import keeps navigation usable. Fresh document clears cached modules.
  permissions = ["AUDIT_READ", "TECHNICAL_READ"]; breakPush = true;
  await page.goto(`${origin}/admin/notifications/push-delivery?case=broken-chunk`);
  await page.getByText("Bu sayfa yüklenemedi.", { exact: true }).waitFor();
  await page.getByRole("button", { name: "Menüyü aç", exact: true }).click();
  await page.getByLabel("Sayfa ara", { exact: true }).fill("denetim");
  await page.getByRole("link", { name: "Denetim kayıtları Yönetici işlemleri" }).click();
  await page.getByRole("heading", { name: "Admin action audits" }).waitFor();
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`${origin}/admin/system/errors?case=owner`);
  await page.getByRole("heading", { name: "İstek hata geçmişi" }).waitFor();
  await page.getByRole("heading", { name: "Tekrarlanan hata grupları" }).waitFor();
  await page.getByRole("button",{name:"INVESTIGATING",exact:true}).click();
  await page.getByLabel("İşlem gerekçesi",{exact:true}).fill("İlk inceleme başlatıldı.");
  await page.getByRole("button",{name:"MFA ile uygula",exact:true}).click();
  await page.getByLabel("Authenticator or recovery code",{exact:true}).fill("123456");
  await page.getByRole("button",{name:"Verify and continue",exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('[aria-label="Fresh owner MFA required"]'));
  assert.equal(errorGroupActions.at(-1).body.lifecycleStatus,"INVESTIGATING");
  assert.equal(errorGroupActions.at(-1).token,"fresh-owner-proof");
  await page.getByRole("button", { name: "Detay #41", exact: true }).click();
  await page.getByText(errorEvent.technicalLocation, { exact: true }).waitFor();
  await page.getByRole("button", { name: "Sonraki", exact: true }).click();
  await page.getByRole("button", { name: "Detay #42", exact: true }).waitFor();
  await page.getByLabel("HTTP kodu", { exact: true }).fill("422");
  await page.getByRole("button", { name: "Filtrele", exact: true }).click();
  await page.getByText("Bu filtrelerle kayıt bulunamadı.", { exact: true }).waitFor();
  assert.equal(errorQueries.at(-1).get("status"), "422");
  assert.equal(errorQueries.at(-1).get("page"), "0");
  await page.getByRole("button", { name: "Temizle", exact: true }).click();
  await page.getByRole("button", { name: "Detay #41", exact: true }).waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForFunction(() => document.querySelector("#admin-navigation").getBoundingClientRect().right <= 0);
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), "Error center must fit mobile width.");
  if (process.env.V2_SCREENSHOT_DIR) await page.screenshot({ path: path.join(process.env.V2_SCREENSHOT_DIR, "owner-errors-mobile.png"), fullPage: true });
  await page.getByRole("combobox", { name: "Dil", exact: true }).selectOption("en");
  await page.getByRole("heading", { name: "Request error history" }).waitFor();
  await assertAccessibleSurface("owner error center");
  errorReadFailure = true;
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByText(/Fixture unavailable/).waitFor();
  errorReadFailure = false;
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "Details #41", exact: true }).waitFor();
  errorNetworkFailure = true;
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByText(/ERR_CONNECTION_FAILED|Network|Failed to fetch/i).waitFor();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "Details #41", exact: true }).waitFor();
  for (let attempt = 0; attempt < 20 && clientTelemetry.length === 0; attempt++) await page.waitForTimeout(50);
  assert.ok(clientTelemetry.length > 0, "A queued network failure must be sent after connectivity recovers.");
  assert.equal(clientTelemetry.at(-1).body.source, "ADMIN_WEB");
  assert.equal(clientTelemetry.at(-1).body.failureKind, "NETWORK");
  assert.equal(clientTelemetry.at(-1).body.route, "/api/v1/admin/errors");
  assert.ok(!JSON.stringify(clientTelemetry.at(-1).body).includes("fixture-only"), "Client telemetry must not contain the bearer token.");
  await page.goto(`${origin}/admin/reports/owner-alerts?status=RETRY&category=BACKEND_ERROR&page=1&size=10`);
  await page.getByRole("heading", { name: "Owner critical alerts", exact: true }).waitFor();
  await page.getByText("Critical backend error", { exact: true }).waitFor();
  assert.equal(ownerAlertQueries.at(-1).get("status"), "RETRY");
  assert.equal(ownerAlertQueries.at(-1).get("category"), "BACKEND_ERROR");
  assert.equal(ownerAlertQueries.at(-1).get("page"), "1");
  assert.equal(ownerAlertQueries.at(-1).get("size"), "10");
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), "Owner alert outbox must fit mobile width.");
  await page.reload();
  await page.getByText("MailSendException", { exact: true }).waitFor();
  assert.equal(new URL(page.url()).searchParams.get("status"), "RETRY", "Owner alert filters must survive reload.");
  await page.getByText("Backend failures", { exact: true }).waitFor();
  await page.getByText("Error group workflow", { exact: true }).waitFor();
  await page.getByText("7", { exact: true }).first().waitFor();
  await page.locator(".review-filter-grid select").selectOption("FAILED");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await page.getByRole("button", { name: "Acknowledge", exact: true }).click();
  await page.getByLabel("Action reason", { exact: true }).fill("Reviewed incident and accepted follow-up.");
  await page.getByLabel("Authenticator or recovery code", { exact: true }).fill("123456");
  await page.getByRole("button", { name: "Verify and apply", exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('[aria-label="Owner alert action"]'));
  assert.deepEqual(ownerAlertActions.at(-1), { path: "/api/v1/admin/owner-alerts/72/acknowledge", body: { reason: "Reviewed incident and accepted follow-up." }, token: "fresh-owner-proof" });
  permissions = ["DASHBOARD_READ", "CATALOG_READ", "TECHNICAL_READ", "ADMIN_TEAM_READ", "ADMIN_TEAM_MANAGE"];
  inboxView = true;
  await page.goto(`${origin}/admin/inbox`);
  await page.getByRole("heading", { name: "My work inbox", exact: true }).waitFor();
  await assertAccessibleSurface("work inbox");
  await page.getByText("Unified work list", { exact: true }).waitFor();
  await page.getByText("Waiting for owner decision", { exact: true }).waitFor();
  await page.getByText("Owner alert delivery failed", { exact: true }).waitFor();
  await page.getByText("Product label review", { exact: true }).waitFor();
  await page.getByText("OCR product candidate", { exact: true }).waitFor();
  await page.getByText("Open error group", { exact: true }).waitFor();
  await page.getByText("Mail delivery needs attention.", { exact: true }).first().waitFor();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("tr");
  await page.getByText("Bildirim dağılımı", { exact: true }).waitFor();
  await page.getByRole("button", { name: "Tümünü okundu işaretle", exact: true }).waitFor();
  await page.getByRole("combobox", { name: "Dil", exact: true }).selectOption("en");
  await page.getByRole("button", { name: "Review decision", exact: true }).click();
  await page.waitForURL(url => url.pathname === "/admin/approvals");
  inboxView = false;
  permissions = ["FINANCE_READ", "FINANCE_MANAGE", "ADMIN_TEAM_READ", "ADMIN_TEAM_MANAGE"];
  await page.goto(`${origin}/admin/subscriptions/access?userId=42&userEmail=context%40example.com`);
  await page.getByRole("button", { name: "Apply subscription", exact: true }).first().evaluate(button => button.click());
  await page.getByText("Approval request #901", { exact: true }).waitFor();
  await page.getByRole("link", { name: "Open owner approval record", exact: true }).click();
  await page.waitForURL(url => url.pathname === "/admin/approvals" && url.searchParams.get("approvalId") === "901");
  await page.getByText("Whitelisted change payload", { exact: true }).waitFor();
  calls.length = 0;
  await page.goto(`${origin}/admin/approvals`);
  await page.getByRole("heading", { name: "Owner approval center", exact: true }).waitFor();
  await page.getByText("Correct verified billing state", { exact: true }).click();
  await page.getByText("Whitelisted change payload", { exact: true }).waitFor();
  assert.equal(new URL(page.url()).searchParams.get("approvalId"), "901", "Selected approval must be addressable in the URL.");
  await page.reload();
  await page.getByText("Financial impact", { exact: true }).waitFor();
  await page.getByText(/Plan: PRO/).waitFor();
  await page.getByText("Not applied yet; waiting for the owner decision.", { exact: true }).waitFor();
  await page.getByRole("button", { name: "Close", exact: true }).click();
  assert.equal(new URL(page.url()).searchParams.has("approvalId"), false, "Closing approval details must clear the URL selection.");
  assert.ok(calls.includes("/api/v1/admin/approvals"), "Owner approval page must load the protected decision queue.");
  role = "ADMIN_FINANCE"; permissions = ["FINANCE_READ", "FINANCE_MANAGE"]; calls.length = 0;
  await page.goto(`${origin}/admin/approvals?case=non-owner`);
  await page.waitForURL(url => url.pathname === "/admin/revenuecat/production");
  assert.ok(!calls.includes("/api/v1/admin/approvals"), "Non-owner deep links must not load approval data.");
  role = "ADMIN_TECHNICAL"; permissions = ["TECHNICAL_READ"]; calls.length=0;
  await page.goto(`${origin}/admin/system/errors?case=non-owner`);
  await page.getByRole("heading", { name: "Integrations", exact: true }).waitFor();
  assert.ok(!calls.some(path => path.startsWith("/api/v1/admin/errors")), "Non-owner must not fetch list, detail or collection status.");
  calls.length=0;
  await page.goto(`${origin}/admin/reports/owner-alerts?case=non-owner`);
  await page.getByRole("heading", { name: "Integrations", exact: true }).waitFor();
  assert.ok(!calls.includes("/api/v1/admin/owner-alerts"), "Non-owner must not fetch the owner alert outbox.");
  console.log("V2 browser passed: navigation, mobile/TR/EN, unified role inbox, addressable product operations, Customer 360 handoffs, owner approvals/errors/alert outbox and non-owner denial. All API traffic mocked.");
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
