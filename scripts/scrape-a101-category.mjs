#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const BASE_URL = "https://www.a101.com.tr";
const DEFAULT_CATEGORY = `${BASE_URL}/kapida/atistirmalik`;
const DEFAULT_OUTPUT = "outputs/TR_Products/a101-atistirmalik-full/products.json";

function printHelp() {
  console.log(`
A101 Kapıda kategori ürünlerini JSON olarak indirir.

Kullanım:
  node scripts/scrape-a101-category.mjs [seçenekler]

Seçenekler:
  --category <url|slug>    Kategori URL'si veya /kapida sonrası kategori yolu
  --output <dosya>         Çıktı JSON yolu
  --max-products <sayı>    Yalnızca ilk N ürünü yaz (test için)
  --timeout-ms <sayı>      HTTP zaman aşımı; varsayılan 60000
  --browser-mode <mod>     auto, http, browser veya cdp
  --cdp-url <url>          Varsayılan: http://127.0.0.1:9222
  --headed                 Tarayıcıyı görünür çalıştır
  --input-html <dosya>     Önceden kaydedilmiş kategori HTML'sini ayrıştır
  --help                   Bu yardımı göster

Örnek:
  node scripts/scrape-a101-category.mjs \\
    --category "https://www.a101.com.tr/kapida/atistirmalik" \\
    --output "outputs/TR_Products/a101-atistirmalik-full/products.json"
`.trim());
}

function parseArgs(argv) {
  const args = {
    category: DEFAULT_CATEGORY,
    output: DEFAULT_OUTPUT,
    maxProducts: null,
    timeoutMs: 60_000,
    browserMode: "auto",
    headed: false,
    inputHtml: null,
    cdpUrl: "http://127.0.0.1:9222",
  };

  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--help") args.help = true;
    else if (key === "--category" && value) args.category = value, index += 1;
    else if (key === "--output" && value) args.output = value, index += 1;
    else if (key === "--max-products" && value) args.maxProducts = Number(value), index += 1;
    else if (key === "--timeout-ms" && value) args.timeoutMs = Number(value), index += 1;
    else if (key === "--browser-mode" && value) args.browserMode = value, index += 1;
    else if (key === "--headed") args.headed = true;
    else if (key === "--input-html" && value) args.inputHtml = value, index += 1;
    else if (key === "--cdp-url" && value) args.cdpUrl = value, index += 1;
    else throw new Error(`Bilinmeyen veya eksik parametre: ${key}`);
  }

  if (args.maxProducts !== null
      && (!Number.isInteger(args.maxProducts) || args.maxProducts < 1)) {
    throw new Error("--max-products pozitif bir tam sayı olmalıdır");
  }
  if (!Number.isFinite(args.timeoutMs) || args.timeoutMs < 1_000) {
    throw new Error("--timeout-ms en az 1000 olmalıdır");
  }
  if (!["auto", "http", "browser", "cdp"].includes(args.browserMode)) {
    throw new Error("--browser-mode auto, http, browser veya cdp olmalıdır");
  }
  return args;
}

function categoryUrl(value) {
  if (/^https?:\/\//i.test(value)) return new URL(value).toString();
  const slug = value.replace(/^\/+|\/+$/g, "").replace(/^kapida\/?/i, "");
  return `${BASE_URL}/kapida/${slug}`;
}

function categorySegments(url) {
  const parts = new URL(url).pathname.split("/").filter(Boolean);
  const kapidaIndex = parts.indexOf("kapida");
  return kapidaIndex >= 0 ? parts.slice(kapidaIndex + 1) : parts;
}

function slugify(value) {
  return String(value ?? "")
    .toLocaleLowerCase("tr-TR")
    .normalize("NFKD")
    .replace(/\p{M}+/gu, "")
    .replace(/ı/g, "i")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchHtml(url, timeoutMs, attempts = 4) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, {
        redirect: "follow",
        signal: controller.signal,
        headers: {
          Accept: "text/html,application/xhtml+xml",
          "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.7",
          Referer: `${BASE_URL}/kapida`,
          "User-Agent": "GRun-Nutrition-Catalog/0.2",
        },
      });
      const html = await response.text();
      if (response.ok) return { html, finalUrl: response.url };
      const retryable = response.status === 429 || response.status >= 500;
      if (!retryable) throw new Error(`HTTP ${response.status}: ${html.slice(0, 300)}`);
      lastError = new Error(`HTTP ${response.status}: ${html.slice(0, 300)}`);
    } catch (error) {
      lastError = error;
    } finally {
      clearTimeout(timer);
    }
    if (attempt < attempts) await sleep(750 * 2 ** (attempt - 1));
  }
  throw lastError;
}

function isChallengePage(html) {
  return /<title>Just a moment\.\.\.<\/title>|id=["']challenge-running["']|cf-turnstile-response/i.test(html);
}

async function fetchHtmlWithBrowser(url, timeoutMs, headed) {
  let chromium;
  try {
    ({ chromium } = await import("playwright"));
  } catch (error) {
    throw new Error(
      "A101 tarayıcı akışı için playwright bulunamadı. Proje klasöründe npm install playwright çalıştırın.",
      { cause: error },
    );
  }

  const browser = await chromium.launch({ channel: "chrome", headless: !headed });
  try {
    const context = await browser.newContext({ locale: "tr-TR" });
    const page = await context.newPage();
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: timeoutMs });

    const challengeDeadline = Date.now() + (headed ? timeoutMs : Math.min(timeoutMs, 20_000));
    let html = await page.content();
    while (isChallengePage(html) && Date.now() < challengeDeadline) {
      await page.waitForTimeout(1_000);
      html = await page.content();
    }
    if (isChallengePage(html)) {
      throw new Error(
        "A101 doğrulama sayfası otomatik olarak geçilemedi. Komutu --headed ile çalıştırıp doğrulamayı tamamlayın.",
      );
    }
    return { html, finalUrl: page.url() };
  } finally {
    await browser.close();
  }
}

async function fetchHtmlFromCdp(url, timeoutMs, cdpUrl) {
  let chromium;
  try {
    ({ chromium } = await import("playwright"));
  } catch (error) {
    throw new Error("CDP modu için playwright bulunamadı.", { cause: error });
  }

  let connectedBrowser;
  try {
    connectedBrowser = await chromium.connectOverCDP(cdpUrl, { timeout: timeoutMs });
  } catch (error) {
    throw new Error(
      `Normal Chrome bağlantısı kurulamadı (${cdpUrl}). Önce scripts/start-a101-capture-browser.ps1 dosyasını çalıştırın.`,
      { cause: error },
    );
  }

  try {
    const requested = categorySegments(url);
    const pages = connectedBrowser.contexts().flatMap((context) => context.pages());
    let page = pages.find((candidate) => {
      try {
        const candidateUrl = new URL(candidate.url());
        const candidateSegments = categorySegments(candidateUrl);
        if (candidateUrl.hostname !== "www.a101.com.tr") return false;
        if (requested.length === 0) return true;
        if (candidateSegments[0] !== requested[0]) return false;
        return requested.length < 2 || candidateSegments[1] === requested[1];
      } catch {
        return false;
      }
    });
    if (!page) {
      page = pages.find((candidate) => {
        try {
          return new URL(candidate.url()).hostname === "www.a101.com.tr";
        } catch {
          return false;
        }
      }) ?? await connectedBrowser.contexts()[0]?.newPage();
      if (!page) throw new Error("Normal Chrome içinde kullanılabilir sekme bulunamadı.");
      await page.goto(url, { waitUntil: "domcontentloaded", timeout: timeoutMs });
    }

    const deadline = Date.now() + timeoutMs;
    let html = await page.content();
    while (isChallengePage(html) && Date.now() < deadline) {
      await page.waitForTimeout(1_000);
      html = await page.content();
    }
    if (isChallengePage(html)) {
      throw new Error("Normal Chrome sekmesindeki A101 doğrulaması henüz tamamlanmadı.");
    }
    return { html, finalUrl: page.url() };
  } finally {
    await connectedBrowser.close().catch(() => {});
  }
}
async function loadCategoryPage(url, args) {
  if (args.browserMode === "http") return fetchHtml(url, args.timeoutMs);
  if (args.browserMode === "cdp") return fetchHtmlFromCdp(url, args.timeoutMs, args.cdpUrl);
  if (args.browserMode === "browser") {
    return fetchHtmlWithBrowser(url, args.timeoutMs, args.headed);
  }
  try {
    return await fetchHtml(url, args.timeoutMs);
  } catch (error) {
    console.warn(`Doğrudan HTTP isteği başarısız (${error.message}). Tarayıcı akışına geçiliyor.`);
    return fetchHtmlWithBrowser(url, args.timeoutMs, args.headed);
  }
}
function decodeHtmlEntities(value) {
  return value
    .replace(/&quot;/g, "\"")
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function extractNextChunks(html) {
  const chunks = [];
  const scriptPattern = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
  for (const scriptMatch of html.matchAll(scriptPattern)) {
    const script = decodeHtmlEntities(scriptMatch[1]);
    if (!script.includes("self.__next_f.push")) continue;

    let cursor = 0;
    const marker = "self.__next_f.push(";
    while ((cursor = script.indexOf(marker, cursor)) >= 0) {
      const argumentStart = cursor + marker.length;
      const argumentEnd = script.indexOf(")</script>", argumentStart) >= 0
        ? script.indexOf(")</script>", argumentStart)
        : script.lastIndexOf(")");
      if (argumentEnd < argumentStart) break;
      const argument = script.slice(argumentStart, argumentEnd);
      try {
        const pushValue = JSON.parse(argument);
        if (typeof pushValue?.[1] === "string") chunks.push(pushValue[1]);
      } catch {
        // Some analytics scripts may contain similar text; they are irrelevant.
      }
      cursor = argumentEnd + 1;
    }
  }
  return chunks;
}

function unescapePayload(value) {
  let current = value;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const next = current
      .replace(/\\(["\\/bfnrt])/g, (_, character) => {
        const escapes = {
          "\"": "\"", "\\": "\\", "/": "/", b: "\b",
          f: "\f", n: "\n", r: "\r", t: "\t",
        };
        return escapes[character];
      })
      .replace(/\\u([0-9a-fA-F]{4})/g, (_, code) => String.fromCharCode(Number.parseInt(code, 16)));
    if (next === current) break;
    current = next;
  }
  return current;
}

function balancedObject(text, start) {
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = start; index < text.length; index += 1) {
    const character = text[index];
    if (inString) {
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === "\"") inString = false;
      continue;
    }
    if (character === "\"") inString = true;
    else if (character === "{") depth += 1;
    else if (character === "}") {
      depth -= 1;
      if (depth === 0) return text.slice(start, index + 1);
    }
  }
  return null;
}

function extractProductObjects(chunks) {
  const joined = chunks.join("\n");
  const variants = [joined];
  for (let index = 0; index < 3; index += 1) {
    const decoded = unescapePayload(variants.at(-1));
    if (decoded === variants.at(-1)) break;
    variants.push(decoded);
  }

  let best = [];
  for (const text of variants) {
    const found = new Map();
    const startPattern = /\{"id":"(\d+)","isEnabled":(?:true|false),/g;
    for (const match of text.matchAll(startPattern)) {
      const rawObject = balancedObject(text, match.index);
      if (!rawObject) continue;
      try {
        const product = JSON.parse(rawObject);
        if (product?.id && product?.attributes?.name) {
          found.set(String(product.id), product);
        }
      } catch {
        // Try the other escaping variants.
      }
    }
    if (found.size > best.length) best = [...found.values()];
  }
  return best;
}

function isValidGtin(value) {
  if (!/^\d{8}$|^\d{12,14}$/.test(value)) return false;
  const digits = [...value].map(Number);
  const checkDigit = digits.pop();
  const sum = digits
    .reverse()
    .reduce((total, digit, index) => total + digit * (index % 2 === 0 ? 3 : 1), 0);
  return (10 - (sum % 10)) % 10 === checkDigit;
}

function normalizeBarcodes(values) {
  const raw = Array.isArray(values) ? values : values == null ? [] : [values];
  return [...new Set(raw.map((value) => String(value).replace(/\D/g, "")).filter(Boolean))];
}

function inferDisplayPackage(name) {
  const normalized = String(name ?? "").toLocaleUpperCase("tr-TR");
  const multi = normalized.match(
    /(\d+)\s*[X×]\s*(\d+(?:[.,]\d+)?)\s*(KG|GR|G|ML|LT|L|ADET)\b/u,
  );
  if (multi) {
    return {
      pieceCount: Number(multi[1]),
      amountPerPiece: Number(multi[2].replace(",", ".")),
      displayUnit: multi[3] === "LT" ? "L" : multi[3] === "GR" ? "G" : multi[3],
      displayText: multi[0],
    };
  }

  const pieceSuffix = normalized.match(/\b(\d+)\s*['’]?(?:LI|Lİ|LU|LÜ)\s*$/u);
  const amountMatches = [...normalized.matchAll(
    /(\d+(?:[.,]\d+)?)\s*(KG|GR|G|ML|LT|L|ADET)\b/gu,
  )];
  const single = amountMatches.at(-1);
  if (!single) {
    return {
      pieceCount: pieceSuffix ? Number(pieceSuffix[1]) : null,
      amountPerPiece: null,
      displayUnit: null,
      displayText: null,
    };
  }

  const pieceCount = pieceSuffix
    ? Number(pieceSuffix[1])
    : single[2] === "ADET" ? Number(single[1].replace(",", ".")) : 1;
  return {
    pieceCount,
    amountPerPiece: pieceCount > 1 ? null : Number(single[1].replace(",", ".")),
    displayUnit: single[2] === "LT" ? "L" : single[2] === "GR" ? "G" : single[2],
    displayText: single[0],
  };
}

function normalizedNetAmount(name, netWeight) {
  const display = inferDisplayPackage(name);
  const numericWeight = Number(netWeight);
  const hasWeight = Number.isFinite(numericWeight) && numericWeight > 0;
  let unit = null;
  if (display.displayUnit === "G" || display.displayUnit === "KG") unit = "G";
  else if (["ML", "L"].includes(display.displayUnit)) unit = "ML";

  let amount = hasWeight ? numericWeight : null;
  if (amount === null && display.amountPerPiece !== null && unit) {
    const multiplier = display.pieceCount ?? 1;
    const unitMultiplier = display.displayUnit === "KG" || display.displayUnit === "L" ? 1_000 : 1;
    amount = display.amountPerPiece * multiplier * unitMultiplier;
  }
  const amountPerPiece = display.amountPerPiece ?? (
    amount !== null && display.pieceCount > 1 ? amount / display.pieceCount : null
  );
  return { amount, unit, ...display, amountPerPiece };
}
function productCategoryPath(product) {
  return [...new Map(
    (product.categories ?? [])
      .filter((category) => category?.name)
      .map((category) => [String(category.id ?? category.name), category.name]),
  ).values()];
}

function matchesRequestedCategory(product, requestedSegments) {
  if (requestedSegments.length <= 1) return true;
  const categorySlugs = productCategoryPath(product).map(slugify);
  return categorySlugs.includes(slugify(requestedSegments[1]));
}

function productImageUrls(product) {
  return [...new Set(
    (product.images ?? [])
      .filter((image) => image?.imageType === "product" && image?.url)
      .map((image) => image.url),
  )];
}

function normalizeProduct(product, categoryPageUrl) {
  const attributes = product.attributes ?? {};
  const categories = productCategoryPath(product);
  const barcodes = normalizeBarcodes(attributes.barcodes);
  const validBarcodes = barcodes.filter(isValidGtin);
  const packageInfo = normalizedNetAmount(attributes.name, attributes.netWeight);
  const images = productImageUrls(product);
  const reviewNotes = [];
  if (barcodes.length === 0) reviewNotes.push("A101 verisinde barkod bulunmuyor.");
  else if (validBarcodes.length === 0) reviewNotes.push("A101 barkodları GTIN kontrol basamağını geçemedi.");
  if (packageInfo.amount !== null && packageInfo.unit === null) {
    reviewNotes.push("Net ağırlık mevcut fakat g/ml birimi ürün adından belirlenemedi.");
  }

  return {
    source: "A101",
    sourceProductId: String(product.id),
    name: attributes.name?.trim() ?? null,
    brand: attributes.brand?.trim() ?? null,
    mainCategory: categories[0] ?? null,
    subcategory: categories.at(-1) ?? null,
    categoryPath: categories,
    packageAmount: packageInfo.amount,
    packageUnit: packageInfo.unit,
    netWeight: Number.isFinite(Number(attributes.netWeight)) ? Number(attributes.netWeight) : null,
    packageDisplayText: packageInfo.displayText,
    pieceCount: packageInfo.pieceCount,
    amountPerPiece: packageInfo.amountPerPiece,
    amountPerPieceUnit: packageInfo.displayUnit,
    salesUnitOfMeasure: attributes.salesUnitOfMeasure ?? null,
    baseUnitOfMeasure: attributes.baseUnitOfMeasure ?? null,
    nutritionBasis: null,
    nutrition: {},
    nutritionRaw: [],
    barcode: validBarcodes[0] ?? barcodes[0] ?? null,
    primaryBarcode: validBarcodes[0] ?? null,
    barcodes,
    barcodeStatus: validBarcodes.length > 0 ? "MEVCUT" : barcodes.length > 0 ? "KONTROL" : "EKSİK",
    imageSourceUrl: images[0] ?? null,
    imageSourceUrls: images,
    sourceUrl: attributes.seoUrl ?? null,
    categoryPageUrl,
    nutritionStatus: "EKSİK",
    reviewNotes,
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    printHelp();
    return;
  }

  const requestedUrl = categoryUrl(args.category);
  const requestedSegments = categorySegments(requestedUrl);
  const outputPath = path.resolve(args.output);

  console.log(`A101 kategori sayfası alınıyor: ${requestedUrl}`);
  const { html, finalUrl } = args.inputHtml
    ? {
        html: await fs.readFile(path.resolve(args.inputHtml), "utf8"),
        finalUrl: requestedUrl,
      }
    : await loadCategoryPage(requestedUrl, args);
  const chunks = extractNextChunks(html);
  if (chunks.length === 0) {
    throw new Error("A101 Next.js veri parçaları bulunamadı; sayfa yapısı değişmiş olabilir");
  }

  const extracted = extractProductObjects(chunks);
  if (extracted.length === 0) {
    throw new Error("A101 ürün nesneleri ayrıştırılamadı; sayfa yapısı değişmiş olabilir");
  }

  const filtered = extracted.filter((product) => matchesRequestedCategory(product, requestedSegments));
  const selected = args.maxProducts ? filtered.slice(0, args.maxProducts) : filtered;
  const products = selected.map((product, index) => ({
    order: index + 1,
    ...normalizeProduct(product, requestedUrl),
  }));

  const artifact = {
    schemaVersion: 1,
    source: "A101",
    category: {
      slug: requestedSegments.join("/"),
      url: requestedUrl,
      finalUrl,
    },
    summary: {
      embeddedProducts: extracted.length,
      matchedCategoryProducts: filtered.length,
      listedProducts: products.length,
      completedProducts: products.length,
      failedProducts: 0,
      barcodeAvailable: products.filter((product) => product.barcodeStatus === "MEVCUT").length,
      barcodeReview: products.filter((product) => product.barcodeStatus === "KONTROL").length,
      barcodeMissing: products.filter((product) => product.barcodeStatus === "EKSİK").length,
      netAmountAvailable: products.filter(
        (product) => product.packageAmount !== null && product.packageUnit !== null,
      ).length,
      nutritionAvailable: 0,
      nutritionMissing: products.length,
    },
    products,
    failures: [],
  };

  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, `${JSON.stringify(artifact, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(artifact.summary));
  console.log(outputPath);
}

await main();
