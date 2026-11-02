#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const BASE_URL = "https://www.diyetkolik.com";
const ALLOWED_HOST = "www.diyetkolik.com";
const DEFAULT_CATEGORY_URL = `${BASE_URL}/kac-kalori/kategori/et-tavuk-balik`;
const DEFAULT_OUTPUT = "outputs/diyetkolik/ettavukbalik.products.json";
const DEFAULT_CSV_OUTPUT = "outputs/diyetkolik/ettavukbalik.import.csv";
const MAX_RESPONSE_BYTES = 12 * 1024 * 1024;
const MAX_PAGES = 250;
const MAX_PRODUCTS = 10_000;
const SEARCH_API_URL = `${BASE_URL}/api/nutritions/search/`;
const SEARCH_ALPHABET = [..."abcçdefgğhıijklmnoöprsştuüvyzqwx0123456789"];

const NUTRIENT_FIELDS = new Map([
  ["karbonhidrat", "carbohydrates"],
  ["protein", "protein"],
  ["yag", "fat"],
  ["lif", "fiber"],
  ["kolesterol", "cholesterol"],
  ["sodyum", "sodium"],
  ["potasyum", "potassium"],
  ["kalsiyum", "calcium"],
  ["demir", "iron"],
  ["magnezyum", "magnesium"],
  ["fosfor", "phosphorus"],
  ["vitamin c", "vitaminC"],
  ["vitamin e", "vitaminE"],
  ["vitamin b6", "vitaminB6"],
  ["vitamin b12", "vitaminB12"],
  ["cinko", "zinc"],
  ["tiyamin", "thiamine"],
  ["riboflavin", "riboflavin"],
]);

const IMPORT_HEADERS = [
  "catalog_type", "data_source", "nutrition_basis", "nutrition_reference_unit",
  "barcode", "source_key", "name", "display_name_tr", "short_display_name_tr",
  "source_language", "brand", "calories", "protein", "fat", "carbs", "fiber",
  "sugar", "sodium", "potassium", "cholesterol", "calcium", "iron", "magnesium",
  "zinc", "vitamin_c", "vitamin_e", "vitamin_b12", "saturated_fat", "trans_fat",
  "serving_size_grams", "serving_quantity", "serving_unit", "serving_options_json",
  "preparation_state", "market_region", "market_regions", "source_categories",
  "source_category_slug", "source_category_name", "source_category_url",
  "source_catalog_id", "source_providers", "source_urls_json",
];

function printHelp() {
  console.log(`
Diyetkolik kategori sayfalarini ve urun detaylarini kontrollu olarak tarar.
Tum sayfalardaki urun baglantilarini tekillestirir; besin ve porsiyon verisini
JSON/CSV olarak yazar. Recipe, ingredient ve instruction alanlarini yazmaz.

Kullanim:
  node scripts/scrape-diyetkolik-category-v1.mjs [secenekler]

Secenekler:
  --url <kategori-url>     /kac-kalori/kategori/<slug> adresi
  --output <dosya>         Normalize JSON cikti yolu
  --csv-output <dosya>     GRUN_STANDARD aday CSV cikti yolu
  --max-pages <sayi>       0 = kaynakta bulunan tum sayfalar (varsayilan 0)
  --max-products <sayi>    0 = bulunan tum urunler (varsayilan 0)
  --discovery-mode <mod>   auto, pagination veya hybrid (varsayilan auto)
  --max-search-terms <n>   Hybrid test limiti; 0 = tum ikili terimler
  --max-search-pages <n>   Terim basina test limiti; 0 = tum sayfalar
  --concurrency <1-3>      Eszamanli detay istegi (varsayilan 2)
  --delay-ms <sayi>        Her istek sonrasi gecikme (varsayilan 900, en az 750)
  --timeout-ms <sayi>      Istek zaman asimi (varsayilan 30000)
  --no-resume              Onceki partial JSONL kaydini kullanma
  --help                   Yardimi goster

Not: Cikti REVIEW_REQUIRED durumundadir. Ticari kullanimdan once kaynak lisansi
ve kullanim kosullari ayrica dogrulanmalidir.
`.trim());
}

function parseArgs(argv) {
  const args = {
    url: DEFAULT_CATEGORY_URL,
    output: DEFAULT_OUTPUT,
    csvOutput: DEFAULT_CSV_OUTPUT,
    maxPages: 0,
    maxProducts: 0,
    discoveryMode: "auto",
    maxSearchTerms: 0,
    maxSearchPages: 0,
    concurrency: 2,
    delayMs: 900,
    timeoutMs: 30_000,
    resume: true,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--help") args.help = true;
    else if (key === "--no-resume") args.resume = false;
    else if (key === "--url" && value) args.url = value, index += 1;
    else if (key === "--output" && value) args.output = value, index += 1;
    else if (key === "--csv-output" && value) args.csvOutput = value, index += 1;
    else if (key === "--max-pages" && value) args.maxPages = Number(value), index += 1;
    else if (key === "--max-products" && value) args.maxProducts = Number(value), index += 1;
    else if (key === "--discovery-mode" && value) args.discoveryMode = value, index += 1;
    else if (key === "--max-search-terms" && value) args.maxSearchTerms = Number(value), index += 1;
    else if (key === "--max-search-pages" && value) args.maxSearchPages = Number(value), index += 1;
    else if (key === "--concurrency" && value) args.concurrency = Number(value), index += 1;
    else if (key === "--delay-ms" && value) args.delayMs = Number(value), index += 1;
    else if (key === "--timeout-ms" && value) args.timeoutMs = Number(value), index += 1;
    else throw new Error(`Bilinmeyen veya eksik parametre: ${key}`);
  }
  if (!Number.isInteger(args.maxPages) || args.maxPages < 0 || args.maxPages > MAX_PAGES) {
    throw new Error(`--max-pages 0 ile ${MAX_PAGES} arasinda tam sayi olmalidir`);
  }
  if (!Number.isInteger(args.maxProducts) || args.maxProducts < 0 || args.maxProducts > MAX_PRODUCTS) {
    throw new Error(`--max-products 0 ile ${MAX_PRODUCTS} arasinda tam sayi olmalidir`);
  }
  if (!new Set(["auto", "pagination", "hybrid"]).has(args.discoveryMode)) {
    throw new Error("--discovery-mode auto, pagination veya hybrid olmalidir");
  }
  for (const [name, value] of [["--max-search-terms", args.maxSearchTerms], ["--max-search-pages", args.maxSearchPages]]) {
    if (!Number.isInteger(value) || value < 0) throw new Error(`${name} sifir veya pozitif tam sayi olmalidir`);
  }
  if (!Number.isInteger(args.concurrency) || args.concurrency < 1 || args.concurrency > 3) {
    throw new Error("--concurrency 1 ile 3 arasinda tam sayi olmalidir");
  }
  if (!Number.isFinite(args.delayMs) || args.delayMs < 750) {
    throw new Error("--delay-ms en az 750 olmalidir");
  }
  if (!Number.isInteger(args.timeoutMs) || args.timeoutMs < 5_000 || args.timeoutMs > 120_000) {
    throw new Error("--timeout-ms 5000 ile 120000 arasinda olmalidir");
  }
  return args;
}

function allowedUrl(value) {
  const url = new URL(value, BASE_URL);
  if (url.protocol !== "https:" || url.hostname !== ALLOWED_HOST) {
    throw new Error(`Yalnizca https://${ALLOWED_HOST} adresleri kabul edilir`);
  }
  return url;
}

function categoryUrl(value) {
  const url = allowedUrl(value);
  if (!/^\/kac-kalori\/kategori\/[^/]+\/?$/.test(url.pathname)) {
    throw new Error("--url /kac-kalori/kategori/<slug> biciminde olmalidir");
  }
  url.search = "";
  url.hash = "";
  return url;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function round(value, scale = 3) {
  if (!Number.isFinite(value)) return null;
  const factor = 10 ** scale;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

function number(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(String(value).replace(",", "."));
  return Number.isFinite(parsed) ? parsed : null;
}

function cleanText(value) {
  if (value === null || value === undefined) return null;
  const result = String(value).replace(/[\u0000-\u001F\u007F]/g, " ").replace(/\s+/g, " ").trim();
  return result || null;
}

function decodeHtml(value) {
  return String(value ?? "")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#x27;|&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

function fold(value) {
  return cleanText(value)?.toLocaleLowerCase("tr-TR")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/ı/g, "i")
    .replace(/[^a-z0-9]+/g, " ")
    .trim() ?? "";
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function csvCell(value) {
  if (value === null || value === undefined) return "";
  const text = typeof value === "string" ? value : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function csvText(headers, rows) {
  // UTF-8 BOM keeps Turkish characters intact when the CSV is opened directly in Excel.
  return `\uFEFF${[
    headers.map(csvCell).join(","),
    ...rows.map((row) => headers.map((header) => csvCell(row[header])).join(",")),
  ].join("\n")}\n`;
}

async function fetchSameHost(urlValue, options, maxRedirects = 3) {
  let current = allowedUrl(urlValue);
  for (let count = 0; count <= maxRedirects; count += 1) {
    const response = await fetch(current, { ...options, redirect: "manual" });
    if (response.status < 300 || response.status >= 400) return response;
    const location = response.headers.get("location");
    if (!location) throw new Error(`HTTP ${response.status}: yonlendirme konumu eksik`);
    current = allowedUrl(new URL(location, current).href);
  }
  throw new Error(`Ayni host yonlendirme limiti asildi (${maxRedirects})`);
}

async function fetchHtml(urlValue, timeoutMs, attempts = 4) {
  const url = allowedUrl(urlValue);
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetchSameHost(url, {
        signal: AbortSignal.timeout(timeoutMs),
        headers: {
          Accept: "text/html,application/xhtml+xml",
          "Accept-Language": "tr-TR,tr;q=0.9",
          "User-Agent": "GRun-Catalog-Research/0.1",
        },
      });
      const contentLength = Number(response.headers.get("content-length") ?? 0);
      if (contentLength > MAX_RESPONSE_BYTES) throw new Error("Kaynak cevap boyutu guvenlik limitini asti");
      const body = await response.text();
      if (Buffer.byteLength(body, "utf8") > MAX_RESPONSE_BYTES) {
        throw new Error("Kaynak cevap boyutu guvenlik limitini asti");
      }
      if (response.ok) return body;
      const failure = new Error(`HTTP ${response.status}: ${body.slice(0, 160)}`);
      if (response.status !== 429 && response.status < 500) throw failure;
      lastError = failure;
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(1_000 * (2 ** (attempt - 1)));
  }
  throw lastError;
}

async function fetchSearchPage(term, page, timeoutMs, attempts = 4) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetchSameHost(SEARCH_API_URL, {
        method: "POST",
        signal: AbortSignal.timeout(timeoutMs),
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json; charset=utf-8",
          "Accept-Language": "tr-TR,tr;q=0.9",
          "User-Agent": "GRun-Catalog-Research/0.1",
        },
        body: JSON.stringify({ term, page }),
      });
      if (!response.ok) {
        const body = await response.text();
        try {
          const payload = JSON.parse(body);
          if (payload.is_error && !Array.isArray(payload.data)) return { totalPage: 0, items: [] };
        } catch {
          // Non-JSON failures continue through the retry/error path below.
        }
        const failure = new Error(`Arama HTTP ${response.status}: ${body.slice(0, 160)}`);
        if (response.status !== 429 && response.status < 500) throw failure;
        lastError = failure;
      } else {
        const payload = await response.json();
        if (payload.is_error && !Array.isArray(payload.data)) return { totalPage: 0, items: [] };
        return {
          totalPage: Number(payload.total_page ?? 0),
          items: Array.isArray(payload.data) ? payload.data : [],
        };
      }
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(1_000 * (2 ** (attempt - 1)));
  }
  throw lastError;
}

function searchTerms() {
  const terms = [];
  for (const first of SEARCH_ALPHABET) {
    for (const second of SEARCH_ALPHABET) terms.push(`${first}${second}`);
  }
  return terms;
}

async function discoverFromSearch(args, manifestPath) {
  const saved = await readPartial(manifestPath);
  const candidates = new Map(saved
    .filter((entry) => entry?.slug)
    .map((entry) => [entry.slug, entry]));
  const completedTerms = new Set(saved.flatMap((entry) => entry?.completedTerm ? [entry.completedTerm] : []));
  const allTerms = searchTerms();
  const terms = args.maxSearchTerms === 0 ? allTerms : allTerms.slice(0, args.maxSearchTerms);
  let requestCount = 0;
  let truncatedTerms = 0;
  const failedTerms = [];

  for (let index = 0; index < terms.length; index += 1) {
    const term = terms[index];
    if (completedTerms.has(term)) continue;
    try {
      await sleep(args.delayMs);
      const first = await fetchSearchPage(term, 1, args.timeoutMs);
      const pageLimit = args.maxSearchPages === 0
        ? first.totalPage
        : Math.min(first.totalPage, args.maxSearchPages);
      const termComplete = pageLimit >= first.totalPage;
      if (!termComplete) truncatedTerms += 1;
      const pages = [first.items];
      requestCount += 1;
      for (let page = 2; page <= pageLimit; page += 1) {
        await sleep(args.delayMs);
        pages.push((await fetchSearchPage(term, page, args.timeoutMs)).items);
        requestCount += 1;
      }
      for (const items of pages) {
        for (const raw of items) {
          const slug = cleanText(raw.slug);
          if (!slug) continue;
          const candidate = {
            slug,
            url: allowedUrl(`/kac-kalori/${slug}`).href,
            sourceProductId: raw.nutrition_id ?? null,
            discoveredBy: "SEARCH_API",
          };
          if (!candidates.has(slug)) await fs.appendFile(manifestPath, `${JSON.stringify(candidate)}\n`, "utf8");
          candidates.set(slug, candidate);
        }
      }
      if (termComplete) {
        await fs.appendFile(manifestPath, `${JSON.stringify({ completedTerm: term })}\n`, "utf8");
        completedTerms.add(term);
      }
    } catch (error) {
      failedTerms.push({ term, error: error.message });
      console.warn(`Arama terimi gecici olarak atlandi (${term}): ${error.message}`);
    }
    if ((index + 1) % 25 === 0 || index + 1 === terms.length) {
      console.log(`Arama kesfi ${index + 1}/${terms.length}: ${candidates.size} benzersiz urun`);
    }
  }
  return {
    candidates,
    completedTerms: completedTerms.size,
    requestCount,
    totalTerms: terms.length,
    exhaustive: args.maxSearchTerms === 0
      && args.maxSearchPages === 0
      && completedTerms.size === allTerms.length
      && truncatedTerms === 0
      && failedTerms.length === 0,
    truncatedTerms,
    failedTerms,
  };
}

function rscText(html) {
  const chunks = [];
  const pattern = /self\.__next_f\.push\(\[1,("(?:\\.|[^"\\])*")\]\)<\/script>/g;
  for (const match of html.matchAll(pattern)) {
    try {
      chunks.push(JSON.parse(match[1]));
    } catch {
      // A malformed framework chunk is ignored; required data is validated later.
    }
  }
  return chunks.join("");
}

function extractJsonValue(text, key) {
  const marker = `"${key}":`;
  const markerIndex = text.lastIndexOf(marker);
  if (markerIndex < 0) return null;
  let index = markerIndex + marker.length;
  while (/\s/.test(text[index] ?? "")) index += 1;
  const start = index;
  if (text[index] !== "[" && text[index] !== "{") return null;
  const stack = [];
  let quoted = false;
  let escaped = false;
  for (; index < text.length; index += 1) {
    const char = text[index];
    if (quoted) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') quoted = false;
      continue;
    }
    if (char === '"') quoted = true;
    else if (char === "[" || char === "{") stack.push(char);
    else if (char === "]" || char === "}") {
      stack.pop();
      if (stack.length === 0) return JSON.parse(text.slice(start, index + 1));
    }
  }
  return null;
}

function extractJsonScalar(text, key) {
  const marker = `"${key}":`;
  const markerIndex = text.lastIndexOf(marker);
  if (markerIndex < 0) return null;
  const tail = text.slice(markerIndex + marker.length);
  try {
    return JSON.parse(`[${tail}]`)[0];
  } catch {
    const match = tail.match(/^\s*(null|true|false|-?\d+(?:\.\d+)?|"(?:\\.|[^"\\])*")/);
    return match ? JSON.parse(match[1]) : null;
  }
}

function pagination(html) {
  const text = rscText(html);
  const match = text.match(/"currentPage":(\d+),"totalPage":(\d+)/);
  if (!match) return { currentPage: 1, totalPage: 1 };
  return { currentPage: Number(match[1]), totalPage: Number(match[2]) };
}

function productLinks(html) {
  const found = new Map();
  const pattern = /href=["'](\/kac-kalori\/([^"'/?#]+))["']/gi;
  for (const match of html.matchAll(pattern)) {
    const slug = cleanText(match[2]);
    if (!slug || ["kategori", "arama"].includes(slug)) continue;
    const url = allowedUrl(match[1]);
    found.set(slug, { slug, url: url.href });
  }
  return [...found.values()];
}

function metaContent(html, property) {
  const escaped = property.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const first = new RegExp(`<meta[^>]+(?:property|name)=["']${escaped}["'][^>]+content=["']([^"']*)["']`, "i");
  const second = new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']${escaped}["']`, "i");
  return cleanText(decodeHtml(html.match(first)?.[1] ?? html.match(second)?.[1]));
}

function productName(html) {
  const heading = html.match(/<h1[^>]*itemProp=["']name["'][^>]*>([\s\S]*?)<\/h1>/i)?.[1];
  const raw = heading ? heading.replace(/<[^>]+>/g, " ") : metaContent(html, "og:title");
  return cleanText(decodeHtml(raw))?.replace(/\s+Kaç Kalori\??.*$/i, "") ?? null;
}

function categorySlug(url) {
  return decodeURIComponent(url.pathname.split("/").filter(Boolean).at(-1));
}

function categoryName(slug) {
  return slug
    .split("-")
    .filter(Boolean)
    .map((word) => `${word.charAt(0).toLocaleUpperCase("tr-TR")}${word.slice(1).toLocaleLowerCase("tr-TR")}`)
    .join(" ");
}

function nutritionBasis(options) {
  const names = options.map((option) => fold(option.name));
  return names.some((name) => /(^| )(mililitre|ml)( |$)/.test(name)) ? "ML" : "GRAM";
}

function canonicalNutrients(options) {
  const source = options.find((option) => Array.isArray(option.ingredient) && option.ingredient.length > 0);
  const values = {};
  const evidence = [];
  const unverified = [];
  for (const nutrient of source?.ingredient ?? []) {
    const title = cleanText(nutrient.title);
    const sourceUnit = cleanText(nutrient.unit)?.toLowerCase() ?? null;
    const sourceValue = number(nutrient.gr100);
    const field = NUTRIENT_FIELDS.get(fold(title));
    evidence.push({ id: nutrient.id ?? null, title, sourceUnit, per100: sourceValue });
    if (field) values[field] = sourceValue;
    else if (sourceValue !== null && sourceValue !== 0) {
      unverified.push({ title, sourceUnit, per100: sourceValue });
    }
  }
  return { values, evidence, unverified };
}

function caloriesPer100(options) {
  const base = options.find((option) => {
    const name = fold(option.name);
    return name === "gram" || name === "mililitre" || name === "ml";
  }) ?? options.find((option) => number(option.current_amount) > 0 && number(option.calorie) !== null);
  const amount = number(base?.current_amount);
  const calories = number(base?.calorie);
  return amount && calories !== null ? round((calories / amount) * 100, 3) : null;
}

function servingUnit(label) {
  const value = fold(label);
  if (value.includes("paket")) return "PACKAGE";
  if (value.includes("dilim")) return "SLICE";
  if (value.includes("yemek kasigi") || value.includes("corba kasigi")) return "TABLESPOON";
  if (value.includes("cay kasigi") || value.includes("tatli kasigi")) return "TEASPOON";
  if (value.includes("kase")) return "BOWL";
  if (value.includes("bardak")) return "GLASS";
  if (value.includes("kupa")) return "MUG";
  if (value.includes("sise")) return "BOTTLE";
  if (value.includes("kutu")) return "CAN";
  if (value.includes("bar")) return "BAR";
  if (value.includes("adet") || value.includes("tane")) return "PIECE";
  if (value.includes("porsiyon")) return "PORTION";
  return "SERVING";
}

function servingOptions(options, basis) {
  return options
    .filter((option) => !["gram", "mililitre", "ml"].includes(fold(option.name)))
    .map((option, index) => {
      const amount = number(option.current_amount);
      const label = cleanText(option.name) ?? "Porsiyon";
      return {
        sourceUnitId: option.unit_id ?? null,
        sourceSequence: option.seq_id ?? null,
        label,
        unitType: servingUnit(label),
        quantity: 1,
        gramWeight: basis === "GRAM" ? amount : null,
        mlVolume: basis === "ML" ? amount : null,
        calories: number(option.calorie),
        defaultOption: index === 0,
        labels: { TR: `1 ${label}` },
      };
    });
}

function inferCatalogType(category) {
  const genericCategories = new Set(["meyve-sebzeler", "et-tavuk-balik", "sut-urunleri"]);
  return genericCategories.has(fold(category).replaceAll(" ", "-")) ? "GENERIC_INGREDIENT" : "STANDARD_PREPARED_ITEM";
}

function validateProduct(product) {
  const issues = [...product.warnings];
  if (!product.name || !product.sourceKey) issues.push({ code: "MISSING_IDENTITY" });
  for (const field of ["calories", "protein", "fat", "carbohydrates"]) {
    if (product.nutrition[field] === null || product.nutrition[field] === undefined) {
      issues.push({ code: "MISSING_CORE_NUTRIENT", field });
    }
  }
  for (const field of ["protein", "fat", "carbohydrates", "fiber"]) {
    const value = product.nutrition[field];
    if (value !== null && value !== undefined && (value < 0 || value > 100)) {
      issues.push({ code: "IMPOSSIBLE_PER_100", field, value });
    }
  }
  if (product.nutrition.calories !== null
      && (product.nutrition.calories < 0 || product.nutrition.calories > 1_000)) {
    issues.push({ code: "IMPLAUSIBLE_CALORIES", value: product.nutrition.calories });
  }
  const rejected = issues.some((issue) => ["MISSING_IDENTITY", "IMPOSSIBLE_PER_100", "IMPLAUSIBLE_CALORIES"].includes(issue.code));
  return { issues, importDecision: rejected ? "REJECTED" : "REVIEW_REQUIRED" };
}

function normalizeDetail(html, item, category, fetchedAt) {
  const frameworkText = rscText(html);
  const sourceOptions = extractJsonValue(frameworkText, "nutritional_values");
  if (!Array.isArray(sourceOptions) || sourceOptions.length === 0) {
    throw new Error("Detay sayfasinda nutritional_values bulunamadi");
  }
  const name = productName(html);
  const sourceCategoryId = number(extractJsonScalar(frameworkText, "category_id"));
  const sourceCategoryName = cleanText(extractJsonScalar(frameworkText, "category_name"));
  const sourceSubcategoryId = number(extractJsonScalar(frameworkText, "sub_category_id"));
  const sourceSubcategoryName = cleanText(extractJsonScalar(frameworkText, "sub_category_name"));
  const basis = nutritionBasis(sourceOptions);
  const nutrients = canonicalNutrients(sourceOptions);
  const options = servingOptions(sourceOptions, basis);
  const warnings = [
    { code: "SOURCE_LICENSE_REVIEW_REQUIRED" },
    { code: "BARCODE_NOT_PROVIDED_BY_SOURCE" },
  ];
  if (nutrients.unverified.length > 0) {
    warnings.push({ code: "UNVERIFIED_NUTRIENT_UNITS", values: nutrients.unverified });
  }
  if (options.some((option) => option.unitType === "SERVING")) {
    warnings.push({ code: "GENERIC_SERVING_UNIT_REVIEW_REQUIRED" });
  }
  const product = {
    source: {
      provider: "DIYETKOLIK",
      sourceUrl: item.url,
      fetchedAt,
      payloadSha256: sha256(JSON.stringify(sourceOptions)),
      retrievedFrom: "server_rendered_html",
      sourceLicenseStatus: "REVIEW_REQUIRED",
    },
    sourceKey: `DIYETKOLIK:${item.slug}`,
    sourceProductId: item.slug,
    name,
    displayNames: { tr: name },
    brand: null,
    barcode: null,
    marketRegion: "TR",
    catalogType: inferCatalogType(category.slug),
    preparationState: "UNSPECIFIED",
    categoryPath: [sourceCategoryName, sourceSubcategoryName].filter(Boolean),
    sourceCategory: {
      ...category,
      id: sourceCategoryId,
      name: sourceCategoryName ?? category.name,
      subcategoryId: sourceSubcategoryId,
      subcategoryName: sourceSubcategoryName,
    },
    nutritionReferenceUnit: basis === "ML" ? "PER_100ML" : "PER_100G",
    nutrition: {
      calories: caloriesPer100(sourceOptions),
      carbohydrates: nutrients.values.carbohydrates ?? null,
      protein: nutrients.values.protein ?? null,
      fat: nutrients.values.fat ?? null,
      fiber: nutrients.values.fiber ?? null,
      sodium: nutrients.values.sodium ?? null,
      potassium: nutrients.values.potassium ?? null,
      cholesterol: nutrients.values.cholesterol ?? null,
      calcium: nutrients.values.calcium ?? null,
      iron: nutrients.values.iron ?? null,
      magnesium: nutrients.values.magnesium ?? null,
      phosphorus: nutrients.values.phosphorus ?? null,
      zinc: nutrients.values.zinc ?? null,
      vitaminC: nutrients.values.vitaminC ?? null,
      vitaminE: nutrients.values.vitaminE ?? null,
      vitaminB6: nutrients.values.vitaminB6 ?? null,
      vitaminB12: nutrients.values.vitaminB12 ?? null,
      thiamine: nutrients.values.thiamine ?? null,
      riboflavin: nutrients.values.riboflavin ?? null,
    },
    servingOptions: options,
    sourceServingEvidence: sourceOptions.map((option) => ({
      sourceUnitId: option.unit_id ?? null,
      sourceSequence: option.seq_id ?? null,
      name: cleanText(option.name),
      amount: number(option.current_amount),
      calories: number(option.calorie),
    })),
    nutrientEvidence: nutrients.evidence,
    imageUrl: metaContent(html, "og:image"),
    warnings,
  };
  Object.assign(product, validateProduct(product));
  return product;
}

function importRow(product) {
  const defaultServing = product.servingOptions.find((option) => option.defaultOption) ?? null;
  return {
    catalog_type: product.catalogType,
    data_source: "ADMIN_IMPORT",
    nutrition_basis: "SOURCE_REPORTED",
    nutrition_reference_unit: product.nutritionReferenceUnit,
    barcode: "",
    source_key: product.sourceKey,
    name: product.name,
    display_name_tr: product.displayNames.tr,
    short_display_name_tr: product.displayNames.tr,
    source_language: "TR",
    brand: "",
    calories: product.nutrition.calories,
    protein: product.nutrition.protein,
    fat: product.nutrition.fat,
    carbs: product.nutrition.carbohydrates,
    fiber: product.nutrition.fiber,
    sugar: "",
    sodium: product.nutrition.sodium,
    potassium: product.nutrition.potassium,
    cholesterol: product.nutrition.cholesterol,
    calcium: product.nutrition.calcium,
    iron: product.nutrition.iron,
    magnesium: product.nutrition.magnesium,
    zinc: product.nutrition.zinc,
    vitamin_c: product.nutrition.vitaminC,
    vitamin_e: product.nutrition.vitaminE,
    vitamin_b12: product.nutrition.vitaminB12,
    saturated_fat: "",
    trans_fat: "",
    serving_size_grams: defaultServing?.gramWeight ?? "",
    serving_quantity: defaultServing?.mlVolume ?? "",
    serving_unit: defaultServing?.gramWeight != null ? "GRAM" : defaultServing?.mlVolume != null ? "MILLILITER" : "",
    serving_options_json: JSON.stringify(product.servingOptions.map((option) => ({
      label: option.label,
      unitType: option.unitType,
      quantity: option.quantity,
      gramWeight: option.gramWeight,
      mlVolume: option.mlVolume,
      defaultOption: option.defaultOption,
      labels: option.labels,
    }))),
    preparation_state: product.preparationState,
    market_region: product.marketRegion,
    market_regions: product.marketRegion,
    source_categories: product.categoryPath.join(";"),
    source_category_slug: product.sourceCategory.slug,
    source_category_name: product.sourceCategory.name,
    source_category_url: product.sourceCategory.url,
    source_catalog_id: product.sourceProductId,
    source_providers: product.source.provider,
    source_urls_json: JSON.stringify([product.source.sourceUrl]),
  };
}

async function readPartial(file) {
  try {
    const lines = (await fs.readFile(file, "utf8")).split(/\r?\n/).filter(Boolean);
    return lines.map((line) => JSON.parse(line));
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return printHelp();

  const startUrl = categoryUrl(args.url);
  const category = {
    provider: "DIYETKOLIK",
    slug: categorySlug(startUrl),
    name: categoryName(categorySlug(startUrl)),
    url: startUrl.href,
  };
  const outputPath = path.resolve(args.output);
  const csvOutputPath = path.resolve(args.csvOutput);
  const partialPath = path.resolve(`${args.output}.partial.jsonl`);
  const progressPath = path.resolve(`${args.output}.progress.json`);
  const searchManifestPath = path.resolve(`${args.output}.search-manifest.jsonl`);
  const excludedPath = path.resolve(`${args.output}.excluded.jsonl`);
  for (const file of [outputPath, csvOutputPath, partialPath, progressPath, searchManifestPath, excludedPath]) {
    await fs.mkdir(path.dirname(file), { recursive: true });
  }
  if (!args.resume) {
    await Promise.all([partialPath, searchManifestPath, excludedPath].map((file) => fs.rm(file, { force: true })));
  }

  console.log(`Kategori ilk sayfasi aliniyor: ${startUrl.href}`);
  const firstHtml = await fetchHtml(startUrl, args.timeoutMs);
  const pageInfo = pagination(firstHtml);
  const pageLimit = args.maxPages === 0 ? pageInfo.totalPage : Math.min(args.maxPages, pageInfo.totalPage);
  if (pageLimit < 1 || pageLimit > MAX_PAGES) throw new Error(`Gecersiz toplam sayfa: ${pageLimit}`);

  const discovered = new Map(productLinks(firstHtml).map((item) => [item.slug, item]));
  const firstPageItems = productLinks(firstHtml);
  const pageFingerprints = new Map();
  const pageDiagnostics = [];

  function recordPage(page, items) {
    const fingerprint = sha256(items.map((item) => item.slug).sort().join("\n"));
    const repeatedPage = pageFingerprints.get(fingerprint) ?? null;
    if (repeatedPage === null) pageFingerprints.set(fingerprint, page);
    const diagnostic = {
      page,
      products: items.length,
      fingerprint,
      repeatedPage,
    };
    pageDiagnostics.push(diagnostic);
    return diagnostic;
  }

  recordPage(1, firstPageItems);
  for (let page = 2; page <= pageLimit; page += 1) {
    await sleep(args.delayMs);
    const url = new URL(startUrl);
    url.searchParams.set("p", String(page));
    const html = await fetchHtml(url, args.timeoutMs);
    const items = productLinks(html);
    const diagnostic = recordPage(page, items);
    for (const item of items) discovered.set(item.slug, item);
    console.log(`Liste sayfasi ${page}/${pageLimit}: ${discovered.size} benzersiz urun`);
    if (args.discoveryMode === "auto" && diagnostic.repeatedPage !== null) {
      console.warn(`Sayfa ${page}, sayfa ${diagnostic.repeatedPage} ile ayni; pagination taramasi erken kesiliyor.`);
      break;
    }
  }

  const repeatedPages = pageDiagnostics.filter((entry) => entry.repeatedPage !== null);
  const sourcePaginationComplete = repeatedPages.length === 0;
  const effectiveDiscoveryMode = args.discoveryMode === "auto"
    ? (sourcePaginationComplete ? "pagination" : "hybrid")
    : args.discoveryMode;
  if (args.discoveryMode === "auto" && effectiveDiscoveryMode === "hybrid") {
    console.warn(
      `Kaynak pagination guvenilmez: ${repeatedPages.length}/${pageDiagnostics.length} sayfa tekrar ediyor. `
      + "Alfabetik arama API kesfine otomatik geciliyor.",
    );
  }

  const categoryPageSlugs = new Set(discovered.keys());
  const categoryProbeItem = firstPageItems[0];
  let targetCategoryId = null;
  if (categoryProbeItem) {
    await sleep(args.delayMs);
    const probeHtml = await fetchHtml(categoryProbeItem.url, args.timeoutMs);
    targetCategoryId = normalizeDetail(probeHtml, categoryProbeItem, category, new Date().toISOString())
      .sourceCategory.id;
  }
  let searchDiscovery = null;
  if (effectiveDiscoveryMode === "hybrid") {
    console.log("Kategori sayfalamasini tamamlamak icin alfabetik arama kesfi basladi.");
    searchDiscovery = await discoverFromSearch(args, searchManifestPath);
    for (const item of searchDiscovery.candidates.values()) discovered.set(item.slug, item);
  }

  const allItems = [...discovered.values()];
  const selectedItems = args.maxProducts === 0 ? allItems : allItems.slice(0, args.maxProducts);
  if (selectedItems.length === 0) throw new Error("Kategori sayfalarinda urun baglantisi bulunamadi");

  const resumed = args.resume ? await readPartial(partialPath) : [];
  const productsByKey = new Map(resumed.map((product) => [product.sourceKey, product]));
  const excluded = args.resume ? await readPartial(excludedPath) : [];
  const excludedKeys = new Set(excluded.map((entry) => entry.sourceKey));
  const failures = [];
  let cursor = 0;
  let processed = 0;
  const startedAt = Date.now();

  async function writeProgress(status = "RUNNING") {
    const elapsedSeconds = Math.max(1, Math.round((Date.now() - startedAt) / 1_000));
    const progress = {
      status,
      updatedAt: new Date().toISOString(),
      category: category.slug,
      sourcePages: pageInfo.totalPage,
      scannedPages: pageDiagnostics.length,
      discoveredProducts: allItems.length,
      selectedProducts: selectedItems.length,
      resumedProducts: resumed.length,
      processedThisRun: processed,
      successfulProducts: productsByKey.size,
      failedProducts: failures.length,
      sourcePaginationComplete,
      repeatedSourcePages: repeatedPages.length,
      requestedDiscoveryMode: args.discoveryMode,
      discoveryMode: effectiveDiscoveryMode,
      completedSearchTerms: searchDiscovery?.completedTerms ?? 0,
      totalSearchTerms: searchDiscovery?.totalTerms ?? 0,
      searchDiscoveryExhaustive: searchDiscovery?.exhaustive ?? false,
      failedSearchTerms: searchDiscovery?.failedTerms.length ?? 0,
      excludedDifferentCategory: excludedKeys.size,
      ratePerMinute: round((processed / elapsedSeconds) * 60, 1),
    };
    await fs.writeFile(progressPath, `${JSON.stringify(progress, null, 2)}\n`, "utf8");
    return progress;
  }

  await writeProgress();
  console.log(`Detay taramasi basladi: ${selectedItems.length} urun (${resumed.length} resume kaydi)`);

  async function worker() {
    while (true) {
      const index = cursor;
      cursor += 1;
      if (index >= selectedItems.length) return;
      const item = selectedItems[index];
      const sourceKey = `DIYETKOLIK:${item.slug}`;
      if (productsByKey.has(sourceKey) || excludedKeys.has(sourceKey)) {
        processed += 1;
        continue;
      }
      try {
        const html = await fetchHtml(item.url, args.timeoutMs);
        const product = normalizeDetail(html, item, category, new Date().toISOString());
        const belongsToCategory = categoryPageSlugs.has(item.slug)
          || (targetCategoryId != null && targetCategoryId === product.sourceCategory.id);
        if (!belongsToCategory) {
          excludedKeys.add(sourceKey);
          await fs.appendFile(excludedPath, `${JSON.stringify({
            sourceKey,
            sourceUrl: item.url,
            categoryId: product.sourceCategory.id,
            categoryName: product.sourceCategory.name,
          })}\n`, "utf8");
          processed += 1;
          await sleep(args.delayMs);
          continue;
        }
        productsByKey.set(product.sourceKey, product);
        await fs.appendFile(partialPath, `${JSON.stringify(product)}\n`, "utf8");
      } catch (error) {
        failures.push({ sourceKey, sourceUrl: item.url, error: error.message });
      }
      processed += 1;
      if (processed % 10 === 0 || processed === selectedItems.length) {
        await writeProgress();
        console.log(`Detay: ${processed}/${selectedItems.length}, basarili ${productsByKey.size}, hata ${failures.length}`);
      }
      await sleep(args.delayMs);
    }
  }

  await Promise.all(Array.from({ length: args.concurrency }, () => worker()));
  const selectedKeys = new Set(selectedItems.map((item) => `DIYETKOLIK:${item.slug}`));
  const products = [...productsByKey.values()]
    .filter((product) => selectedKeys.has(product.sourceKey) && !excludedKeys.has(product.sourceKey))
    .sort((left, right) => left.name.localeCompare(right.name, "tr"));
  const report = {
    generatedAt: new Date().toISOString(),
    source: "DIYETKOLIK",
    sourceLicenseStatus: "REVIEW_REQUIRED",
    category,
    sourceCategoryUrl: startUrl.href,
    sourcePages: pageInfo.totalPage,
    scannedPages: pageDiagnostics.length,
    sourcePaginationComplete,
    requestedDiscoveryMode: args.discoveryMode,
    discoveryMode: effectiveDiscoveryMode,
    searchDiscovery: searchDiscovery ? {
      completedTerms: searchDiscovery.completedTerms,
      totalTerms: searchDiscovery.totalTerms,
      requestCount: searchDiscovery.requestCount,
      candidateProducts: searchDiscovery.candidates.size,
      exhaustive: searchDiscovery.exhaustive,
      truncatedTerms: searchDiscovery.truncatedTerms,
      failedTerms: searchDiscovery.failedTerms,
    } : null,
    pageDiagnostics,
    discoveredProducts: allItems.length,
    exportedProducts: products.length,
    importReady: products.filter((product) => product.importDecision === "IMPORT_READY").length,
    reviewRequired: products.filter((product) => product.importDecision === "REVIEW_REQUIRED").length,
    rejected: products.filter((product) => product.importDecision === "REJECTED").length,
    warnings: sourcePaginationComplete ? [] : [{
      code: "SOURCE_PAGINATION_REPEATED_CONTENT",
      message: "Diyetkolik returned identical product lists for multiple page numbers; export may be incomplete.",
      repeatedPages: repeatedPages.map((entry) => ({ page: entry.page, repeatedPage: entry.repeatedPage })),
    }],
    failures,
    excludedDifferentCategory: excludedKeys.size,
    products,
  };
  await fs.writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await fs.writeFile(csvOutputPath, csvText(IMPORT_HEADERS, products.map(importRow)), "utf8");
  const completedStatus = effectiveDiscoveryMode === "hybrid"
    ? (searchDiscovery.exhaustive ? "COMPLETED_HYBRID_DISCOVERY" : "INCOMPLETE_HYBRID_DISCOVERY")
    : (sourcePaginationComplete ? "COMPLETED" : "INCOMPLETE_SOURCE_PAGINATION");
  await writeProgress(completedStatus);

  console.log(JSON.stringify({
    output: outputPath,
    csvOutput: csvOutputPath,
    progressOutput: progressPath,
    category,
    scannedPages: pageDiagnostics.length,
    discoveredProducts: allItems.length,
    exportedProducts: products.length,
    sourcePaginationComplete,
    requestedDiscoveryMode: args.discoveryMode,
    discoveryMode: effectiveDiscoveryMode,
    completedSearchTerms: searchDiscovery?.completedTerms ?? 0,
    searchDiscoveryExhaustive: searchDiscovery?.exhaustive ?? false,
    failedSearchTerms: searchDiscovery?.failedTerms.length ?? 0,
    searchCandidates: searchDiscovery?.candidates.size ?? 0,
    excludedDifferentCategory: excludedKeys.size,
    repeatedSourcePages: repeatedPages.length,
    failures: failures.length,
  }, null, 2));
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
