#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const BASE_URL = "https://www.calorieking.com";
const ALLOWED_HOST = "www.calorieking.com";
const DEFAULT_START_URL = `${BASE_URL}/gb/en/foods/b/calories-in-kelloggs/FgI-Ff92T_OwkKtxrPdTWg`;
const DEFAULT_OUTPUT = "outputs/calorieking-example/kelloggs-products.json";
const DEFAULT_CSV_OUTPUT = "outputs/calorieking-example/kelloggs-products.import.csv";
const MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

function printHelp() {
  console.log(`
CalorieKing marka sayfasindaki urunleri yapilandirilmis Next.js verisinden okur.

Kullanim:
  node scripts/scrape-calorieking-brand-v2.mjs [secenekler]

Secenekler:
  --url <marka-url>        CalorieKing marka liste URL'si
  --output <dosya>         Normalize JSON cikti yolu
  --csv-output <dosya>     GRUN_STANDARD aday CSV cikti yolu
  --raw-output <dosya>     Degistirilmeyen detail evidence JSONL yolu
  --max-products <sayi>    Islenecek urun sayisi (varsayilan 5, en fazla 100)
  --concurrency <1-4>      Eszamanli detay istegi (varsayilan 2)
  --delay-ms <sayi>        Istekler arasi gecikme (varsayilan 750, en az 500)
  --timeout-ms <sayi>      Istek zaman asimi (varsayilan 30000)
  --help                   Yardimi goster
`.trim());
}

function parseArgs(argv) {
  const args = {
    url: DEFAULT_START_URL,
    output: DEFAULT_OUTPUT,
    csvOutput: DEFAULT_CSV_OUTPUT,
    rawOutput: null,
    maxProducts: 5,
    concurrency: 2,
    delayMs: 750,
    timeoutMs: 30_000,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--help") args.help = true;
    else if (key === "--url" && value) args.url = value, index += 1;
    else if (key === "--output" && value) args.output = value, index += 1;
    else if (key === "--csv-output" && value) args.csvOutput = value, index += 1;
    else if (key === "--raw-output" && value) args.rawOutput = value, index += 1;
    else if (key === "--max-products" && value) args.maxProducts = Number(value), index += 1;
    else if (key === "--concurrency" && value) args.concurrency = Number(value), index += 1;
    else if (key === "--delay-ms" && value) args.delayMs = Number(value), index += 1;
    else if (key === "--timeout-ms" && value) args.timeoutMs = Number(value), index += 1;
    else throw new Error(`Bilinmeyen veya eksik parametre: ${key}`);
  }
  if (!Number.isInteger(args.maxProducts) || args.maxProducts < 1 || args.maxProducts > 300) {
    throw new Error("--max-products 1 ile 300 arasinda tam sayi olmalidir");
  }
  if (!Number.isInteger(args.concurrency) || args.concurrency < 1 || args.concurrency > 4) {
    throw new Error("--concurrency 1 ile 4 arasinda tam sayi olmalidir");
  }
  if (!Number.isFinite(args.delayMs) || args.delayMs < 500) {
    throw new Error("--delay-ms en az 500 olmalidir");
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function round(value, scale = 3) {
  if (value === null || value === undefined || !Number.isFinite(value)) return null;
  const factor = 10 ** scale;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

function cleanText(value) {
  if (value === null || value === undefined) return null;
  const result = String(value).replace(/[\u0000-\u001F\u007F]/g, " ").replace(/\s+/g, " ").trim();
  return result || null;
}

function fold(value) {
  return cleanText(value)?.toLocaleLowerCase("en-US")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim() ?? "";
}

function decodeHtml(value) {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#x27;|&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

function csvCell(value) {
  if (value === null || value === undefined) return "";
  const text = typeof value === "string" ? value : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function csvText(headers, rows) {
  return `${[
    headers.map(csvCell).join(","),
    ...rows.map((row) => headers.map((header) => csvCell(row[header])).join(",")),
  ].join("\n")}\n`;
}

async function fetchHtml(urlValue, timeoutMs, attempts = 4) {
  const url = allowedUrl(urlValue);
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        redirect: "error",
        signal: AbortSignal.timeout(timeoutMs),
        headers: {
          Accept: "text/html,application/xhtml+xml",
          "User-Agent": "GRun-Catalog-Example/0.1",
        },
      });
      const contentLength = Number(response.headers.get("content-length") ?? 0);
      if (contentLength > MAX_RESPONSE_BYTES) throw new Error("Kaynak cevap boyutu guvenlik limitini asti");
      const body = await response.text();
      if (Buffer.byteLength(body, "utf8") > MAX_RESPONSE_BYTES) {
        throw new Error("Kaynak cevap boyutu guvenlik limitini asti");
      }
      if (response.ok) return body;
      const retryable = response.status === 429 || response.status >= 500;
      const failure = new Error(`HTTP ${response.status}: ${body.slice(0, 200)}`);
      if (!retryable) throw failure;
      lastError = failure;
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(1000 * 2 ** (attempt - 1));
  }
  throw lastError;
}

function extractNextData(html) {
  const match = html.match(/<script[^>]*id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i);
  if (!match) throw new Error("Sayfada __NEXT_DATA__ bulunamadi");
  const parsed = JSON.parse(match[1]);
  const pageProps = parsed?.props?.pageProps;
  if (!pageProps) throw new Error("__NEXT_DATA__ icinde pageProps bulunamadi");
  return { buildId: parsed.buildId ?? null, page: parsed.page ?? null, pageProps };
}

function extractProductLinks(html) {
  const links = [];
  const pattern = /href=["']([^"']*\/foods\/f\/[^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  for (const match of html.matchAll(pattern)) {
    const label = cleanText(decodeHtml(match[2].replace(/<[^>]+>/g, " ")));
    if (!label) continue;
    const url = allowedUrl(match[1]).href;
    if (!links.some((item) => item.url === url)) links.push({ name: label, url });
  }
  return links;
}

function listingItems(html, pageProps) {
  const links = extractProductLinks(html);
  const linkByName = new Map(links.map((link) => [fold(link.name), link.url]));
  const products = [];
  for (const group of pageProps.groups ?? []) {
    for (const food of group.foods ?? []) {
      const detailUrl = linkByName.get(fold(food.name));
      if (!detailUrl) continue;
      products.push({
        foodId: cleanText(food.foodId),
        name: cleanText(food.name),
        brand: cleanText(food.brand?.name ?? pageProps.brand?.name),
        classification: cleanText(food.classification ?? group.name),
        detailUrl,
      });
    }
  }
  return products;
}

function basisOf(food) {
  if (Number.isFinite(food.volume) && food.volume > 0) return { amount: food.volume, unit: "ML" };
  if (Number.isFinite(food.mass) && food.mass > 0) return { amount: food.mass, unit: "GRAM" };
  return null;
}

function normalizedNutrition(food, basis) {
  const nutrients = food.nutrients ?? {};
  const factor = basis ? 100 / basis.amount : null;
  const perServing = {
    calories: round(Number(nutrients.energy) / 4.184, 1),
    energyKj: round(Number(nutrients.energy), 1),
    fat: round(Number(nutrients.fat)),
    saturatedFat: round(Number(nutrients.satFat)),
    transFat: round(Number(nutrients.transFat)),
    // CalorieKing's structured payload currently exposes carbohydrate as
    // `netCarbs`; retain `totalCarbs` as a compatibility fallback for older
    // payload variants instead of silently dropping the macro value.
    carbohydrates: round(Number(nutrients.netCarbs ?? nutrients.totalCarbs)),
    sugar: round(Number(nutrients.sugar)),
    fiber: round(Number(nutrients.fiber)),
    protein: round(Number(nutrients.protein)),
    sodium: round(Number(nutrients.sodium)),
    cholesterol: round(Number(nutrients.cholesterol)),
    calcium: round(Number(nutrients.calcium)),
    iron: round(Number(nutrients.iron)),
    alcohol: round(Number(nutrients.alcohol)),
    // The embedded payload does not declare a Vitamin A unit. Preserve it only
    // in source evidence instead of guessing IU, mcg, or mg.
    vitaminA: null,
    vitaminC: round(Number(nutrients.vitaminC)),
  };
  for (const [key, value] of Object.entries(perServing)) {
    if (!Number.isFinite(value)) perServing[key] = null;
  }
  const per100 = Object.fromEntries(Object.entries(perServing).map(([key, value]) => [
    key,
    value === null || factor === null ? null : round(value * factor),
  ]));
  return { perServing, per100 };
}

function servingOptions(food, basis) {
  if (!basis) {
    return [{
      label: "1 serving",
      amount: null,
      unit: "SERVING",
      isDefault: true,
      sourceScale: 1,
    }];
  }
  const options = [];
  for (const serving of food.servings ?? []) {
    if (!Number.isFinite(serving.scale) || serving.scale <= 0) continue;
    options.push({
      label: cleanText(serving.name),
      amount: round(basis.amount * serving.scale, 1),
      unit: basis.unit,
      isDefault: Boolean(serving.isDefault),
      sourceScale: serving.scale,
    });
  }
  return options;
}

function validateProduct(product) {
  const issues = [];
  if (!product.sourceProductId || !product.displayNames.en) issues.push({ code: "MISSING_IDENTITY" });
  if (!product.brand) issues.push({ code: "MISSING_BRAND" });
  const canonicalNutrition = product.nutritionReferenceUnit === "PER_SERVING"
    ? product.sourceServingNutrition
    : product.nutrition;
  for (const field of ["calories", "protein", "carbohydrates", "fat"]) {
    if (canonicalNutrition[field] === null) issues.push({ code: "MISSING_CORE_NUTRIENT", field });
  }
  if (product.nutritionReferenceUnit !== "PER_SERVING") {
    for (const field of ["fat", "saturatedFat", "transFat", "carbohydrates", "sugar", "fiber", "protein"]) {
      const value = canonicalNutrition[field];
      if (value !== null && (value < 0 || value > 100)) issues.push({ code: "IMPOSSIBLE_PER_100", field, value });
    }
  }
  const calorieLimit = product.nutritionReferenceUnit === "PER_SERVING" ? 5000 : 1000;
  if (canonicalNutrition.calories !== null
      && (canonicalNutrition.calories < 0 || canonicalNutrition.calories > calorieLimit)) {
    issues.push({ code: "IMPLAUSIBLE_CALORIES", value: canonicalNutrition.calories });
  }
  const rejected = issues.some((issue) => ["MISSING_IDENTITY", "IMPOSSIBLE_PER_100", "IMPLAUSIBLE_CALORIES"].includes(issue.code));
  return {
    issues,
    importDecision: rejected ? "REJECTED" : issues.length ? "REVIEW_REQUIRED" : "IMPORT_READY",
  };
}

function normalizeDetail(food, detailUrl, fetchedAt, payloadHash) {
  const basis = basisOf(food);
  const nutrition = normalizedNutrition(food, basis);
  const brand = cleanText(food.brand?.name);
  const classification = cleanText(food.classification);
  const product = {
    source: "CALORIEKING_EXAMPLE",
    sourceKey: food.foodId ? `CALORIEKING:${food.region}:${food.foodId}` : null,
    sourceProductId: cleanText(food.foodId),
    sourceRevisionId: cleanText(food.revisionId),
    sourceUrl: detailUrl,
    sourceFetchedAt: fetchedAt,
    sourcePayloadSha256: payloadHash,
    sourceLanguage: cleanText(food.lang ?? "en"),
    sourceRegion: cleanText(food.region),
    catalogType: "STANDARD_PREPARED_ITEM",
    marketRegions: [String(food.region ?? "US").toLocaleUpperCase("en-US")],
    canonicalName: fold(food.name),
    displayNames: { en: cleanText(food.name), tr: null },
    brand,
    brandNormalized: fold(brand),
    mainCategory: classification,
    subcategory: classification,
    categoryPath: [classification].filter(Boolean),
    sourceNote: cleanText(food.note),
    sourceNutrientsWithUnverifiedUnit: {
      vitaminA: Number.isFinite(Number(food.nutrients?.vitaminA)) ? Number(food.nutrients.vitaminA) : null,
    },
    nutritionBasis: basis ? { amount: 100, unit: basis.unit, sourceServingAmount: basis.amount } : null,
    nutritionReferenceUnit: basis ? (basis.unit === "ML" ? "PER_100ML" : "PER_100G") : "PER_SERVING",
    sourceServingNutrition: nutrition.perServing,
    nutrition: nutrition.per100,
    preparationState: "PREPARED",
    servingOptions: servingOptions(food, basis),
    barcode: null,
    barcodeStatus: "NOT_PROVIDED_BY_SOURCE",
    duplicateKey: [brand, food.name, classification].map(fold).join("|"),
    warnings: Number.isFinite(Number(food.nutrients?.vitaminA))
      ? [{ code: "UNVERIFIED_NUTRIENT_UNIT", field: "vitaminA", rawValue: Number(food.nutrients.vitaminA) }]
      : [],
  };
  Object.assign(product, validateProduct(product));
  return product;
}

function servingOptionsForImport(product) {
  return product.servingOptions.map((option) => ({
    label: option.label,
    unitType: "SERVING",
    quantity: 1,
    gramWeight: option.unit === "GRAM" ? option.amount : null,
    mlVolume: option.unit === "ML" ? option.amount : null,
    defaultOption: option.isDefault,
    labels: { EN: option.label },
  }));
}

function importRow(product) {
  const defaultServing = product.servingOptions.find((option) => option.isDefault)
    ?? product.servingOptions[0]
    ?? null;
  const gramServing = defaultServing?.unit === "GRAM" ? defaultServing.amount : null;
  const mlServing = defaultServing?.unit === "ML" ? defaultServing.amount : null;
  const nutrition = product.nutritionReferenceUnit === "PER_SERVING"
    ? product.sourceServingNutrition
    : product.nutrition;
  return {
    catalog_type: product.catalogType,
    data_source: "ADMIN_IMPORT",
    nutrition_basis: "SOURCE_REPORTED",
    nutrition_reference_unit: product.nutritionReferenceUnit,
    barcode: "",
    source_key: product.sourceKey,
    name: product.displayNames.en,
    display_name_en: product.displayNames.en,
    short_display_name_en: product.displayNames.en,
    source_language: "EN",
    brand: product.brand,
    calories: nutrition.calories,
    protein: nutrition.protein,
    fat: nutrition.fat,
    carbs: nutrition.carbohydrates,
    fiber: nutrition.fiber,
    sugar: nutrition.sugar,
    sodium: nutrition.sodium,
    cholesterol: nutrition.cholesterol,
    calcium: nutrition.calcium,
    iron: nutrition.iron,
    vitamin_c: nutrition.vitaminC,
    saturated_fat: nutrition.saturatedFat,
    trans_fat: nutrition.transFat,
    serving_size_grams: gramServing,
    serving_quantity: mlServing,
    serving_unit: gramServing !== null ? "GRAM" : mlServing !== null ? "MILLILITER" : "",
    serving_options_json: JSON.stringify(servingOptionsForImport(product)),
    preparation_state: product.preparationState,
    market_region: "GLOBAL",
    market_regions: "GLOBAL",
    source_categories: product.categoryPath.join(";"),
    source_catalog_id: product.sourceProductId,
    source_providers: product.source,
    source_urls_json: JSON.stringify([product.sourceUrl]),
  };
}

const IMPORT_HEADERS = [
  "catalog_type", "data_source", "nutrition_basis", "nutrition_reference_unit",
  "barcode", "source_key", "name", "display_name_en", "short_display_name_en",
  "source_language", "brand", "calories", "protein", "fat", "carbs", "fiber",
  "sugar", "sodium", "cholesterol", "calcium", "iron", "vitamin_c",
  "saturated_fat", "trans_fat", "serving_size_grams", "serving_quantity",
  "serving_unit", "serving_options_json", "preparation_state", "market_region",
  "market_regions", "source_categories", "source_catalog_id", "source_providers",
  "source_urls_json",
];

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return printHelp();
  const startUrl = allowedUrl(args.url).href;
  if (!startUrl.includes("/foods/b/")) throw new Error("--url bir CalorieKing marka liste sayfasi olmalidir");

  const outputPath = path.resolve(args.output);
  const csvOutputPath = path.resolve(args.csvOutput);
  const rawOutputPath = path.resolve(args.rawOutput ?? `${args.output}.raw.jsonl`);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.mkdir(path.dirname(csvOutputPath), { recursive: true });
  await fs.mkdir(path.dirname(rawOutputPath), { recursive: true });

  console.log(`Marka listesi aliniyor: ${startUrl}`);
  const listingHtml = await fetchHtml(startUrl, args.timeoutMs);
  const listingNext = extractNextData(listingHtml);
  const availableItems = listingItems(listingHtml, listingNext.pageProps);
  const selectedItems = availableItems.slice(0, args.maxProducts);
  if (selectedItems.length === 0) throw new Error("Marka sayfasinda urun detay baglantisi bulunamadi");

  const rawHandle = await fs.open(rawOutputPath, "w");
  const products = [];
  const failures = [];
  let cursor = 0;

  async function worker() {
    while (true) {
      const index = cursor;
      cursor += 1;
      if (index >= selectedItems.length) return;
      const item = selectedItems[index];
      try {
        const html = await fetchHtml(item.detailUrl, args.timeoutMs);
        const next = extractNextData(html);
        const food = next.pageProps.foodRaw;
        if (!food?.foodId || !food?.nutrients) throw new Error("Detay sayfasinda foodRaw veya nutrients bulunamadi");
        const fetchedAt = new Date().toISOString();
        const serialized = JSON.stringify(food);
        const payloadHash = sha256(serialized);
        await rawHandle.appendFile(`${JSON.stringify({
          source: "CALORIEKING_EXAMPLE",
          sourceUrl: item.detailUrl,
          fetchedAt,
          payloadSha256: payloadHash,
          payload: food,
        })}\n`, "utf8");
        products[index] = normalizeDetail(food, item.detailUrl, fetchedAt, payloadHash);
      } catch (error) {
        failures.push({ name: item.name, sourceUrl: item.detailUrl, error: error.message });
      }
      await sleep(args.delayMs);
    }
  }

  await Promise.all(Array.from({ length: args.concurrency }, () => worker()));
  await rawHandle.close();
  const completed = products.filter(Boolean);

  const duplicateCounts = new Map();
  for (const product of completed) {
    duplicateCounts.set(product.duplicateKey, (duplicateCounts.get(product.duplicateKey) ?? 0) + 1);
  }
  for (const product of completed) {
    if (duplicateCounts.get(product.duplicateKey) > 1) {
      product.issues.push({ code: "DUPLICATE_CANDIDATE", duplicateKey: product.duplicateKey });
      if (product.importDecision === "IMPORT_READY") product.importDecision = "REVIEW_REQUIRED";
    }
  }

  const summary = {
    availableOnRenderedBrandPage: availableItems.length,
    requestedProducts: selectedItems.length,
    completedProducts: completed.length,
    failedProducts: failures.length,
    importReady: completed.filter((item) => item.importDecision === "IMPORT_READY").length,
    reviewRequired: completed.filter((item) => item.importDecision === "REVIEW_REQUIRED").length,
    rejected: completed.filter((item) => item.importDecision === "REJECTED").length,
  };
  const artifact = {
    schemaVersion: 2,
    generatedAt: new Date().toISOString(),
    source: "CALORIEKING_EXAMPLE",
    sourceMode: "STRUCTURED_NEXT_DATA",
    sourceBrand: cleanText(listingNext.pageProps.brand?.name),
    sourceUrl: startUrl,
    importPolicy: "Example research output. Only IMPORT_READY records may proceed after source-use approval.",
    summary,
    products: completed,
    failures,
  };
  await fs.writeFile(outputPath, `${JSON.stringify(artifact, null, 2)}\n`, "utf8");
  const importCandidates = completed.filter((product) => product.importDecision === "IMPORT_READY");
  await fs.writeFile(
    csvOutputPath,
    csvText(IMPORT_HEADERS, importCandidates.map(importRow)),
    "utf8",
  );
  console.log(JSON.stringify(summary, null, 2));
  console.log(`Normalize cikti: ${outputPath}`);
  console.log(`GRUN_STANDARD aday CSV: ${csvOutputPath} (${importCandidates.length} kayit)`);
  console.log(`Raw evidence: ${rawOutputPath}`);
}

await main();
