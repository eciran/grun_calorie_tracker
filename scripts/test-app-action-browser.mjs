import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';

const require = createRequire(resolve('admin-ui/package.json'));
const { chromium } = require('playwright');
const root = resolve('src/main/resources/static');
const html = await readFile(resolve(root, 'app-action.html'));
let consumed = false;
let validationFailure = false;
let delay = 0;
let confirmations = 0;
let requests = 0;
const server = createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname;
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Referrer-Policy', 'no-referrer');
    if (path.startsWith('/api/')) {
      let raw = '';
      for await (const part of req) raw += part;
      const body = JSON.parse(raw);
      res.setHeader('Content-Type', 'application/json');
      if (path.endsWith('/validate')) {
        if (delay) await new Promise(resolve => setTimeout(resolve, delay));
        res.statusCode = validationFailure ? 503 : 200;
        res.end(JSON.stringify({ valid: body.token === 'valid' && !consumed }));
      } else if (path.endsWith('/confirm')) {
        confirmations++;
        res.statusCode = consumed ? 400 : 200;
        consumed = true;
        res.end('{}');
      } else if (path.endsWith('/request')) {
        requests++;
        res.end('{}');
      } else { res.statusCode = 404; res.end('{}'); }
      return;
    }
    const assets = {
      '/email-assets/grun-logo.png': ['email-assets/grun-logo-192.png', 'image/png'],
      '/app-assets/questrial-regular.ttf': ['app-assets/questrial-regular.ttf', 'font/ttf'],
    };
    if (assets[path]) {
      res.setHeader('Content-Type', assets[path][1]);
      res.end(await readFile(resolve(root, assets[path][0])));
    } else {
      res.setHeader('Content-Type', 'text/html');
      res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'");
      res.end(html);
    }
  } catch { res.statusCode = 500; res.end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({ headless: true, channel: 'chrome' });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const go = token => page.goto(`${base}/reset-password?token=${token}&lang=tr`);
const visible = async selector => { await page.locator(selector).waitFor({ state: 'visible' }); };
const fill = async () => {
  await page.locator('#password').fill('ValidPassword1!');
  await page.locator('#confirm-password').fill('ValidPassword1!');
};
try {
  delay = 600;
  await go('valid');
  assert.equal(await page.locator('#reset-form').isVisible(), false, 'No form during preflight');
  await visible('#reset-form');
  delay = 0;
  assert.equal(new URL(page.url()).searchParams.has('token'), false);
  await page.locator('#password').fill('ValidPassword1!');
  await page.locator('[data-input="password"]').click();
  assert.equal(await page.locator('#password').getAttribute('type'), 'text');
  assert.equal(await page.locator('#confirm-password').getAttribute('type'), 'password');
  await page.locator('[data-input="password"]').click();
  await page.locator('#confirm-password').fill('Mismatch1!');
  await page.locator('#reset-submit').click();
  assert.match(await page.locator('#status').innerText(), /eşleşmiyor/);
  assert.equal(confirmations, 0);
  await fill();
  await page.evaluate(() => document.fonts.ready);
  assert.equal(await page.evaluate(() => document.fonts.check('17px Questrial')), true);
  assert.equal(await page.locator('.brand img').evaluate(img => img.naturalWidth > 0), true);
  await mkdir('outputs/app-action-review', { recursive: true });
  await page.screenshot({ path: 'outputs/app-action-review/mobile-tr.png', fullPage: true });
  for (const width of [320, 390, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  }
  await page.screenshot({ path: 'outputs/app-action-review/desktop-tr.png', fullPage: true });
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.screenshot({ path: 'outputs/app-action-review/dark-tr.png', fullPage: true });
  await page.locator('#reset-submit').click();
  await page.waitForFunction(() => document.querySelector('#status').textContent.includes('Şifren güncellendi'));
  assert.equal(confirmations, 1);
  assert.equal(await page.locator('#reset-form').isVisible(), false);
  assert.equal(await page.locator('#password').inputValue(), '');
  await go('valid');
  await visible('#new-link-wrap');
  assert.equal(await page.locator('#reset-form').isVisible(), false, 'Used token rejected before form');
  for (const token of ['expired', '', 'x'.repeat(513)]) {
    await go(token);
    await visible('#new-link-wrap');
    assert.equal(await page.locator('#reset-form').isVisible(), false);
  }
  consumed = false;
  validationFailure = true;
  await go('valid');
  await visible('#retry');
  assert.match(await page.locator('#status').innerText(), /kontrol edilemiyor/);
  assert.equal(await page.locator('#new-link-wrap').isVisible(), false);
  validationFailure = false;
  await page.locator('#retry').click();
  await visible('#reset-form');
  consumed = true;
  await fill();
  await page.locator('#reset-submit').click();
  await visible('#new-link-wrap');
  assert.equal(await page.locator('#reset-form').isVisible(), false, 'Token consumed after preflight is rejected');
  await page.locator('#new-link').click();
  await visible('#request-form');
  await page.locator('#email').fill('user@example.com');
  await page.locator('#request-submit').click();
  await page.waitForFunction(() => document.querySelector('#status').textContent.includes('kayıtlı bir hesap varsa'));
  assert.equal(requests, 1);
  consumed = false;
  await page.goto(`${base}/reset-password?token=valid`);
  await visible('#reset-form');
  assert.equal(await page.locator('#password-label').innerText(), 'New password');
  // Intercept only the OS handoff boundary; test redirect decisions without opening installed apps.
  for (const [userAgent, mobile] of [
    ['Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', true],
    ['Mozilla/5.0 (Linux; Android 15)', true],
    ['Mozilla/5.0 (Windows NT 10.0; Win64; x64)', false],
  ]) {
    const context = await browser.newContext({ userAgent });
    const handoffPage = await context.newPage();
    await handoffPage.addInitScript(() => { window.__appOpenAttempts = []; });
    await handoffPage.route('**/reset-password?*', async route => {
      const response = await route.fetch();
      const body = (await response.text()).replace('window.location.assign(appUrl)', 'window.__appOpenAttempts.push(appUrl)');
      await route.fulfill({ response, body });
    });
    await handoffPage.goto(`${base}/reset-password?token=valid`);
    await handoffPage.locator('#reset-form').waitFor({ state: 'visible' });
    assert.equal(await handoffPage.evaluate(() => window.__appOpenAttempts.length), mobile ? 1 : 0);
    await handoffPage.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })));
    await handoffPage.locator('#reset-form').waitFor({ state: 'visible' });
    assert.equal(await handoffPage.evaluate(() => window.__appOpenAttempts.length), mobile ? 1 : 0, 'No redirect loop');
    await handoffPage.goto(`${base}/reset-password?token=used`);
    await handoffPage.locator('#new-link-wrap').waitFor({ state: 'visible' });
    assert.equal(await handoffPage.evaluate(() => window.__appOpenAttempts.length), 0, 'Invalid links never hand off');
    await context.close();
  }
  assert.equal(errors.length, 0, errors.join('\n'));
  console.log('PASS: preflight, used/expired/missing token, retry, visibility controls, confirmation race, request link, TR/EN, local assets, responsive layout.');
} finally {
  await browser.close();
  await new Promise(resolve => server.close(resolve));
}
