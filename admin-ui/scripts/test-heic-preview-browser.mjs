import assert from "node:assert/strict";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { transform } from "esbuild";

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const root = path.resolve(fileURLToPath(new URL("../../src/main/resources/static/admin-ui/", import.meta.url)));
const fixture = process.env.HEIC_TEST_FIXTURE;
assert.ok(fixture, "Set HEIC_TEST_FIXTURE to a public test image, not user media.");
const worker = fs.readdirSync(path.join(root, "assets")).find(name => /^heicPreview\.worker-.*\.js$/.test(name));
assert.ok(worker, "Build the real worker first.");
const preview = await transform(fs.readFileSync(new URL("../src/admin/imagePreview.ts", import.meta.url), "utf8"), { loader: "ts", format: "esm", target: "es2022" });
let codecRequests = 0;
const server = http.createServer((request, response) => {
  const url = new URL(request.url, "http://localhost");
  if (url.pathname === "/") return response.setHeader("Content-Type", "text/html").end("<!doctype html><title>HEIC preview acceptance</title><body></body>");
  if (url.pathname === "/preview.js") return response.setHeader("Content-Type", "text/javascript").end(preview.code);
  if (url.pathname === "/fixture.heic") return response.end(fs.readFileSync(fixture));
  const relative = url.pathname === "/heicPreview.worker.ts" ? `assets/${worker}` : url.pathname.replace(/^\/admin-ui\//, "");
  const target = path.resolve(root, relative);
  if (!target.startsWith(root + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) return response.writeHead(404).end();
  if (target.endsWith(".wasm")) codecRequests++;
  response.setHeader("Content-Type", target.endsWith(".wasm") ? "application/wasm" : "text/javascript");
  response.end(fs.readFileSync(target));
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
let browser;
try {
  browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  const page = await browser.newPage();
  page.on("console", message => { if (message.type() === "error") console.error(message.text()); });
  await page.addInitScript(() => {
    const NativeWorker = Worker;
    window.Worker = class extends NativeWorker {
      constructor(...args) {
        super(...args);
        this.addEventListener("error", event => console.error("Test worker error:", event.message));
      }
    };
  });
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  await page.evaluate(async () => {
    const { browserImageObjectUrl } = await import("/preview.js");
    const canvas = document.createElement("canvas");
    const png = await new Promise(resolve => canvas.toBlob(resolve));
    const url = await browserImageObjectUrl(png);
    URL.revokeObjectURL(url);
  });
  assert.equal(codecRequests, 0, "Ordinary images must not load the codec.");
  const result = await page.evaluate(async () => {
    const { browserImageObjectUrl } = await import("/preview.js");
    const blob = await (await fetch("/fixture.heic")).blob();
    let ticks = 0;
    const timer = setInterval(() => ticks++, 10);
    const started = performance.now();
    const url = await browserImageObjectUrl(blob, "image/heic");
    clearInterval(timer);
    const jpeg = await (await fetch(url)).blob();
    const bitmap = await createImageBitmap(jpeg);
    const image = document.createElement("img");
    image.src = url; image.style.maxWidth = "100%"; document.body.append(image);
    return { width: bitmap.width, height: bitmap.height, type: jpeg.type, ticks, duration: Math.round(performance.now() - started) };
  });
  assert.ok(result.width > 0 && result.height > 0);
  assert.equal(result.type, "image/jpeg");
  assert.ok(result.ticks > 1, "Main thread must remain responsive while decoding.");
  assert.equal(codecRequests, 1);
  await page.screenshot({ path: fileURLToPath(new URL("../../outputs/security-review/heic-preview-desktop.png", import.meta.url)) });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: fileURLToPath(new URL("../../outputs/security-review/heic-preview-mobile.png", import.meta.url)) });
  const rejected = await page.evaluate(async () => {
    const { browserImageObjectUrl } = await import("/preview.js");
    let errors = 0;
    for (const blob of [new Blob(["corrupt"], { type: "image/heic" }), new Blob([new Uint8Array(26 * 1024 * 1024)], { type: "image/heic" })]) {
      try { await browserImageObjectUrl(blob); } catch { errors++; }
    }
    const url = await browserImageObjectUrl(await (await fetch("/fixture.heic")).blob(), "image/heic");
    URL.revokeObjectURL(url);
    return errors;
  });
  assert.equal(rejected, 2, "Corrupt/oversized inputs must fail and must not poison the queue.");
  console.log("HEIC preview acceptance passed", result);
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
