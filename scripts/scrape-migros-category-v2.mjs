#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const BASE_URL = "https://www.migros.com.tr";
const ALLOWED_HOST = "www.migros.com.tr";
const DEFAULT_CATEGORY = "dondurma-c-41b";
const DEFAULT_OUTPUT = "outputs/TR_Products/migros-v2/products.json";
const MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

const LIQUID_CATEGORY_TERMS = [
  "icecek", "su", "sut", "ayran", "kefir", "meyve suyu", "gazli icecek",
  "enerji icecegi", "soguk cay", "bitkisel icecek", "sivi yag", "sirke",
];

const NUTRIENT_DEFINITIONS = [
  { field: "calories", ids: ["energyKcal"], labels: ["enerji kcal"], unit: "KCAL" },
  { field: "energyKj", ids: ["energyKj"], labels: ["enerji kj"], unit: "KJ" },
  { field: "fat", ids: ["fat"], labels: ["yag"], unit: "G" },
  { field: "saturatedFat", ids: ["saturatedFat"], labels: ["doymus yag"], unit: "G" },
  { field: "transFat", labels: ["trans yag"], unit: "G" },
  { field: "carbohydrates", ids: ["carbohydrate"], labels: ["karbonhidrat"], unit: "G" },
  { field: "sugar", ids: ["glucose"], labels: ["seker"], unit: "G" },
  { field: "fiber", labels: ["lif"], unit: "G" },
  { field: "protein", ids: ["protein"], labels: ["protein"], unit: "G" },
  { field: "cholesterol", labels: ["kolesterol"], unit: "MG" },
  { field: "calcium", labels: ["kalsiyum"], unit: "MG" },
  { field: "iron", labels: ["demir"], unit: "MG" },
  { field: "potassium", labels: ["potasyum"], unit: "MG" },
  { field: "vitaminA", labels: ["vitamin a"], unit: "MCG" },
  { field: "vitaminC", labels: ["vitamin c"], unit: "MG" },
];

function printHelp() {
  console.log(`
Migros kategori urunlerini raw evidence ve normalize import adaylari olarak indirir.

Kullanim:
  node scripts/scrape-migros-category-v2.mjs [secenekler]

Secenekler:
  --category <url|slug>       Migros kategori URL'si veya slug'i
  --output <dosya>            Normalize sonuc JSON yolu
  --raw-output <dosya>        Degistirilmeyen kaynak snapshot JSONL yolu
  --concurrency <1-8>         Eszamanli detay istegi (varsayilan 4)
  --delay-ms <sayi>           Her istek sonrasi gecikme (varsayilan 250)
  --max-products <sayi>       Ilk N urunle sinirla
  --timeout-ms <sayi>         Istek zaman asimi (varsayilan 30000)
  --help                      Yardimi goster
`.trim());
}

function parseArgs(argv) {
  const args = {
    category: DEFAULT_CATEGORY,
    output: DEFAULT_OUTPUT,
    rawOutput: null,
    concurrency: 4,
    delayMs: 250,
    maxProducts: null,
    timeoutMs: 30_000,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--help") args.help = true;
    else if (key === "--category" && value) args.category = value, index += 1;
    else if (key === "--output" && value) args.output = value, index += 1;
    else if (key === "--raw-output" && value) args.rawOutput = value, index += 1;
    else if (key === "--concurrency" && value) args.concurrency = Number(value), index += 1;
    else if (key === "--delay-ms" && value) args.delayMs = Number(value), index += 1;
    else if (key === "--max-products" && value) args.maxProducts = Number(value), index += 1;
    else if (key === "--timeout-ms" && value) args.timeoutMs = Number(value), index += 1;
    else throw new Error(`Bilinmeyen veya eksik parametre: ${key}`);
  }
  if (!Number.isInteger(args.concurrency) || args.concurrency < 1 || args.concurrency > 8) {
    throw new Error("--concurrency 1 ile 8 arasinda tam sayi olmalidir");
  }
  if (!Number.isFinite(args.delayMs) || args.delayMs < 100) {
    throw new Error("--delay-ms en az 100 olmalidir");
  }
  if (!Number.isInteger(args.timeoutMs) || args.timeoutMs < 5_000 || args.timeoutMs > 120_000) {
    throw new Error("--timeout-ms 5000 ile 120000 arasinda olmalidir");
  }
  if (args.maxProducts !== null
      && (!Number.isInteger(args.maxProducts) || args.maxProducts < 1)) {
    throw new Error("--max-products pozitif tam sayi olmalidir");
  }
  return args;
}

function categorySlug(value) {
  try {
    const url = new URL(value);
    if (url.hostname !== ALLOWED_HOST) throw new Error(`Yalnizca ${ALLOWED_HOST} kabul edilir`);
    return url.pathname.replace(/^\/+|\/+$/g, "");
  } catch (error) {
    if (/^https?:/i.test(value)) throw error;
    if (!/^[a-z0-9-]+$/i.test(value)) throw new Error("Gecersiz Migros kategori slug'i");
    return value;
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function safeUrl(value) {
  const url = new URL(value, BASE_URL);
  if (url.protocol !== "https:" || url.hostname !== ALLOWED_HOST) {
    throw new Error(`Izin verilmeyen kaynak URL'si: ${url.href}`);
  }
  return url;
}

async function fetchJson(urlValue, timeoutMs, attempts = 4) {
  const url = safeUrl(urlValue);
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        redirect: "error",
        signal: AbortSignal.timeout(timeoutMs),
        headers: {
          Accept: "application/json",
          Referer: `${BASE_URL}/`,
          "User-Agent": "GRun-Nutrition-Catalog/0.2 (controlled research import)",
        },
      });
      const contentLength = Number(response.headers.get("content-length") ?? 0);
      if (contentLength > MAX_RESPONSE_BYTES) throw new Error("Kaynak cevap boyutu guvenlik limitini asti");
      const body = await response.text();
      if (Buffer.byteLength(body, "utf8") > MAX_RESPONSE_BYTES) {
        throw new Error("Kaynak cevap boyutu guvenlik limitini asti");
      }
      if (response.ok) return JSON.parse(body);
      const retryable = response.status === 429 || response.status >= 500;
      const failure = new Error(`HTTP ${response.status}: ${body.slice(0, 300)}`);
      if (!retryable) throw failure;
      lastError = failure;
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(750 * 2 ** (attempt - 1));
  }
  throw lastError;
}

function text(value) {
  if (value === null || value === undefined) return null;
  const normalized = String(value).replace(/[\u0000-\u001F\u007F]/g, " ").replace(/\s+/g, " ").trim();
  return normalized || null;
}

function fold(value) {
  return text(value)?.toLocaleLowerCase("tr-TR")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/ı/g, "i")
    .replace(/[^a-z0-9]+/g, " ")
    .trim() ?? "";
}

function number(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(String(value).trim().replace(",", "."));
  return Number.isFinite(parsed) ? parsed : null;
}

function round(value, scale = 3) {
  if (value === null) return null;
  const factor = 10 ** scale;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function getProperty(detail, type, customId) {
  return detail.propertyInfosMap?.[type]?.find((item) => item.customId === customId)?.value ?? null;
}

function inferPackage(name, detail) {
  const normalizedName = text(name)?.toLocaleUpperCase("tr-TR") ?? "";
  const match = normalizedName.match(/(?:^|\s)(?:(\d+)\s*[Xx]\s*)?(\d+(?:[.,]\d+)?)\s*(KG|GR|G|ML|LT|L|ADET)\s*$/u);
  if (match) {
    const multiplier = match[1] ? Number(match[1]) : 1;
    const amount = Number(match[2].replace(",", "."));
    const unit = ({ GR: "G", LT: "L" })[match[3]] ?? match[3];
    return { amount, unit, multiplier, totalAmount: round(amount * multiplier) };
  }
  const netAmount = number(getProperty(detail, "MAIN", "netKg"));
  return netAmount === null
    ? { amount: null, unit: null, multiplier: 1, totalAmount: null }
    : { amount: netAmount, unit: null, multiplier: 1, totalAmount: netAmount };
}

function stripPackageSuffix(name) {
  return text(name)?.replace(/\s+(?:\d+\s*[Xx]\s*)?\d+(?:[.,]\d+)?\s*(?:KG|GR|G|ML|LT|L|ADET)\s*$/iu, "").trim() ?? null;
}

function normalizedBrand(detail, listItem) {
  return text(detail.brand?.name ?? listItem.brand?.name);
}

function rawNutrition(detail) {
  return (detail.propertyInfosMap?.NUTRITIONAL ?? []).map((item) => ({
    label: text(item.name),
    customId: text(item.customId),
    value: text(item.value),
  }));
}

function parseLabelUnit(label) {
  const match = text(label)?.match(/\((kcal|kj|g|mg|µg|ug|mcg)\)/iu);
  if (!match) return null;
  const unit = match[1].toLocaleUpperCase("en-US");
  return unit === "UG" || unit === "µG" ? "MCG" : unit;
}

function findNutrient(entries, definition) {
  const byId = entries.find((entry) => definition.ids?.includes(entry.customId));
  if (byId) return byId;
  return entries.find((entry) => definition.labels.some((label) => fold(entry.label) === fold(label)));
}

function convertUnit(value, sourceUnit, targetUnit) {
  if (value === null || !sourceUnit || sourceUnit === targetUnit) return value;
  if (sourceUnit === "G" && targetUnit === "MG") return value * 1000;
  if (sourceUnit === "MG" && targetUnit === "G") return value / 1000;
  if (sourceUnit === "MG" && targetUnit === "MCG") return value * 1000;
  if (sourceUnit === "MCG" && targetUnit === "MG") return value / 1000;
  return null;
}

function normalizeNutrition(entries) {
  const values = {};
  const evidence = {};
  const issues = [];
  for (const definition of NUTRIENT_DEFINITIONS) {
    const entry = findNutrient(entries, definition);
    if (!entry) {
      values[definition.field] = null;
      continue;
    }
    const parsed = number(entry.value);
    const sourceUnit = parseLabelUnit(entry.label) ?? definition.unit;
    const converted = convertUnit(parsed, sourceUnit, definition.unit);
    values[definition.field] = round(converted);
    evidence[definition.field] = {
      rawLabel: entry.label,
      rawValue: entry.value,
      rawUnit: sourceUnit,
      normalizedUnit: definition.unit,
    };
    if (parsed === null || converted === null || converted < 0) {
      issues.push({ code: "INVALID_NUTRIENT", field: definition.field, rawValue: entry.value });
    }
  }

  if (values.calories !== null && values.energyKj !== null) {
    const normalRatio = values.energyKj / Math.max(values.calories, 0.001);
    const swappedRatio = values.calories / Math.max(values.energyKj, 0.001);
    if ((normalRatio < 3.5 || normalRatio > 5) && swappedRatio >= 3.5 && swappedRatio <= 5) {
      const sourceCalories = values.calories;
      values.calories = values.energyKj;
      values.energyKj = sourceCalories;
      evidence.calories.correction = "Source kcal/kJ fields were swapped after energy-ratio validation";
      evidence.energyKj.correction = "Source kcal/kJ fields were swapped after energy-ratio validation";
      issues.push({
        code: "SOURCE_ENERGY_FIELDS_SWAPPED",
        originalCalories: sourceCalories,
        originalEnergyKj: values.calories,
      });
    } else if (normalRatio < 3.5 || normalRatio > 5) {
      issues.push({
        code: "ENERGY_VALUES_INCONSISTENT",
        calories: values.calories,
        energyKj: values.energyKj,
      });
    }
  }

  const sodiumEntry = entries.find((entry) => fold(entry.label) === "sodyum");
  const saltEntry = entries.find((entry) => entry.customId === "salt" || fold(entry.label) === "tuz");
  if (sodiumEntry) {
    const sourceUnit = parseLabelUnit(sodiumEntry.label) ?? "MG";
    values.sodium = round(convertUnit(number(sodiumEntry.value), sourceUnit, "MG"));
    evidence.sodium = { rawLabel: sodiumEntry.label, rawValue: sodiumEntry.value, rawUnit: sourceUnit, normalizedUnit: "MG" };
  } else if (saltEntry) {
    const sourceUnit = parseLabelUnit(saltEntry.label) ?? "G";
    const saltGrams = convertUnit(number(saltEntry.value), sourceUnit, "G");
    values.sodium = saltGrams === null ? null : round(saltGrams * 400);
    evidence.sodium = {
      rawLabel: saltEntry.label,
      rawValue: saltEntry.value,
      rawUnit: sourceUnit,
      normalizedUnit: "MG",
      derivation: "salt_g * 400",
    };
  } else {
    values.sodium = null;
  }

  for (const field of ["fat", "saturatedFat", "transFat", "carbohydrates", "sugar", "fiber", "protein"]) {
    if (values[field] !== null && values[field] > 100) issues.push({ code: "IMPOSSIBLE_PER_100", field, value: values[field] });
  }
  if (values.calories !== null && values.calories > 1000) {
    issues.push({ code: "IMPLAUSIBLE_CALORIES", field: "calories", value: values.calories });
  }
  return { values, evidence, issues };
}

function classifyBasis(packageInfo, categoryPath) {
  if (["ML", "L"].includes(packageInfo.unit)) return { unit: "ML", confidence: "SOURCE_PACKAGE", issues: [] };
  if (["G", "KG"].includes(packageInfo.unit)) return { unit: "GRAM", confidence: "SOURCE_PACKAGE", issues: [] };
  const foldedPath = fold(categoryPath.join(" "));
  const categoryLiquid = LIQUID_CATEGORY_TERMS.some((term) => foldedPath.includes(fold(term)));
  if (categoryLiquid) {
    return {
      unit: "ML",
      confidence: "CATEGORY_INFERENCE",
      issues: [{ code: "BASIS_INFERRED_FROM_CATEGORY", detail: categoryPath.join(" > ") }],
    };
  }
  return { unit: null, confidence: "UNKNOWN", issues: [{ code: "UNKNOWN_NUTRITION_BASIS" }] };
}

function toBasePackageAmount(packageInfo, basisUnit) {
  if (packageInfo.totalAmount === null) return null;
  if (basisUnit === "ML" && packageInfo.unit === "L") return round(packageInfo.totalAmount * 1000);
  if (basisUnit === "ML" && packageInfo.unit === "ML") return packageInfo.totalAmount;
  if (basisUnit === "GRAM" && packageInfo.unit === "KG") return round(packageInfo.totalAmount * 1000);
  if (basisUnit === "GRAM" && packageInfo.unit === "G") return packageInfo.totalAmount;
  return null;
}

function servingOptions(packageInfo, basisUnit) {
  if (!basisUnit) return [];
  const options = [{ code: basisUnit === "ML" ? "ML_100" : "GRAM_100", labelTr: basisUnit === "ML" ? "100 ml" : "100 g", amount: 100, unit: basisUnit }];
  const packageAmount = toBasePackageAmount(packageInfo, basisUnit);
  if (packageAmount !== null) {
    options.push({ code: "PACKAGE", labelTr: "1 paket", amount: packageAmount, unit: basisUnit });
  }
  return options;
}

function imageUrl(detail) {
  const urls = detail.images?.[0]?.urls ?? {};
  const candidate = urls.x2 || urls.x1 || urls.original || Object.values(urls).find(Boolean);
  if (!candidate) return null;
  try {
    const url = new URL(candidate);
    return url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

function decisionFor(issues, nutrition) {
  const rejectionCodes = new Set(["INVALID_NUTRIENT", "IMPOSSIBLE_PER_100", "IMPLAUSIBLE_CALORIES", "MISSING_IDENTITY"]);
  if (issues.some((issue) => rejectionCodes.has(issue.code))) return "REJECTED";
  if (nutrition.calories === null || nutrition.protein === null
      || nutrition.carbohydrates === null || nutrition.fat === null) return "REVIEW_REQUIRED";
  return issues.length === 0 ? "IMPORT_READY" : "REVIEW_REQUIRED";
}

function normalizeProduct(listItem, detail, categoryPageUrl, fetchedAt, payloadHash) {
  const sourceProductId = text(detail.id ?? listItem.id);
  const sourceName = text(detail.name ?? listItem.name);
  const brand = normalizedBrand(detail, listItem);
  const packageInfo = inferPackage(sourceName, detail);
  const ascendingPath = [...(detail.categoryAscendants ?? [])].reverse().map((item) => text(item.name)).filter(Boolean);
  const categoryPath = [...ascendingPath, text(detail.category?.name)].filter(Boolean);
  const basis = classifyBasis(packageInfo, categoryPath);
  const rawEntries = rawNutrition(detail);
  const normalized = normalizeNutrition(rawEntries);
  const displayNameTr = stripPackageSuffix(sourceName);
  const sourceUrl = `${BASE_URL}/${detail.prettyName || listItem.prettyName}`;
  const issues = [...basis.issues, ...normalized.issues];
  if (!sourceProductId || !displayNameTr) issues.push({ code: "MISSING_IDENTITY" });
  if (!brand) issues.push({ code: "MISSING_BRAND" });
  if (rawEntries.length === 0) issues.push({ code: "MISSING_NUTRITION" });
  for (const field of ["calories", "protein", "carbohydrates", "fat"]) {
    if (normalized.values[field] === null) issues.push({ code: "MISSING_CORE_NUTRIENT", field });
  }
  if (!packageInfo.unit) issues.push({ code: "PACKAGE_UNIT_UNVERIFIED", rawAmount: packageInfo.totalAmount });

  const duplicateKey = [brand, displayNameTr, packageInfo.totalAmount, packageInfo.unit].map(fold).join("|");
  const product = {
    source: "MIGROS",
    sourceKey: sourceProductId ? `MIGROS:${sourceProductId}` : null,
    sourceProductId,
    sourceUrl,
    sourceFetchedAt: fetchedAt,
    sourcePayloadSha256: payloadHash,
    sourceLanguage: "tr",
    catalogType: "BRANDED_PRODUCT",
    marketRegions: ["TR"],
    canonicalName: null,
    displayNames: { tr: displayNameTr, en: null },
    sourceName,
    brand,
    brandNormalized: fold(brand),
    mainCategory: categoryPath[0] ?? null,
    subcategory: categoryPath.at(-1) ?? null,
    categoryPath,
    package: {
      amount: packageInfo.amount,
      unit: packageInfo.unit,
      multiplier: packageInfo.multiplier,
      totalAmount: packageInfo.totalAmount,
    },
    nutritionBasis: basis.unit ? { amount: 100, unit: basis.unit, confidence: basis.confidence } : null,
    nutrition: normalized.values,
    nutritionEvidence: normalized.evidence,
    preparationState: "UNSPECIFIED",
    servingOptions: servingOptions(packageInfo, basis.unit),
    barcode: null,
    barcodeStatus: "NOT_PROVIDED_BY_SOURCE",
    imageSourceUrl: imageUrl(detail),
    categoryPageUrl,
    duplicateKey,
    issues,
  };
  product.importDecision = decisionFor(issues, normalized.values);
  return product;
}

async function loadCheckpoint(checkpointPath) {
  try {
    const rows = new Map();
    for (const line of (await fs.readFile(checkpointPath, "utf8")).split(/\r?\n/)) {
      if (!line.trim()) continue;
      const row = JSON.parse(line);
      if (row?.sourceProductId) rows.set(row.sourceProductId, row);
    }
    return rows;
  } catch (error) {
    if (error.code === "ENOENT") return new Map();
    throw error;
  }
}

async function collectListing(slug, timeoutMs) {
  const firstUrl = `${BASE_URL}/rest/search/screens/${encodeURIComponent(slug)}`;
  const first = await fetchJson(firstUrl, timeoutMs);
  if (!first.successful || !first.data?.searchInfo) throw new Error("Migros kategori cevabinda searchInfo bulunamadi");
  const pageCount = first.data.searchInfo.pageCount;
  const hitCount = first.data.searchInfo.hitCount;
  const products = [...first.data.searchInfo.storeProductInfos];
  for (let page = 2; page <= pageCount; page += 1) {
    const response = await fetchJson(`${firstUrl}?page=${page}`, timeoutMs);
    products.push(...(response.data?.searchInfo?.storeProductInfos ?? []));
    await sleep(150);
  }
  const categoryId = new URLSearchParams(first.data.searchQuery ?? "").get("category-id");
  if (!categoryId) throw new Error("Migros kategori cevabinda category-id bulunamadi");
  for (const sort of ["once-en-dusuk-fiyat", "once-en-yuksek-fiyat", "cok-satanlar"]) {
    for (let page = 1; page <= pageCount; page += 1) {
      const params = new URLSearchParams({ "category-id": categoryId, sort, page: String(page) });
      const response = await fetchJson(`${BASE_URL}/rest/products/search?${params}`, timeoutMs);
      products.push(...(response.data?.storeProductInfos ?? []));
      await sleep(150);
    }
  }
  return {
    hitCount,
    pageCount,
    products: [...new Map(products.map((item) => [String(item.id), item])).values()],
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return printHelp();
  const slug = categorySlug(args.category);
  const categoryPageUrl = `${BASE_URL}/${slug}`;
  const outputPath = path.resolve(args.output);
  const checkpointPath = `${outputPath}.checkpoint.jsonl`;
  const rawOutputPath = path.resolve(args.rawOutput ?? `${args.output}.raw.jsonl`);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.mkdir(path.dirname(rawOutputPath), { recursive: true });

  console.log(`Kategori listesi aliniyor: ${categoryPageUrl}`);
  const listing = await collectListing(slug, args.timeoutMs);
  const listProducts = args.maxProducts ? listing.products.slice(0, args.maxProducts) : listing.products;
  const completed = await loadCheckpoint(checkpointPath);
  const pending = listProducts.filter((item) => !completed.has(String(item.id)));
  const checkpointHandle = await fs.open(checkpointPath, "a");
  const rawHandle = await fs.open(rawOutputPath, "a");
  const failures = [];
  let cursor = 0;
  let processed = 0;

  async function worker() {
    while (true) {
      const index = cursor;
      cursor += 1;
      if (index >= pending.length) return;
      const listItem = pending[index];
      try {
        const response = await fetchJson(`${BASE_URL}/rest/products/${listItem.id}`, args.timeoutMs);
        if (!response.successful || !response.data) throw new Error("Urun detay cevabi basarisiz");
        const fetchedAt = new Date().toISOString();
        const serializedPayload = JSON.stringify(response.data);
        const payloadHash = sha256(serializedPayload);
        await rawHandle.appendFile(`${JSON.stringify({
          source: "MIGROS",
          sourceProductId: String(listItem.id),
          sourceUrl: `${BASE_URL}/${response.data.prettyName || listItem.prettyName}`,
          fetchedAt,
          payloadSha256: payloadHash,
          payload: response.data,
        })}\n`, "utf8");
        const product = normalizeProduct(listItem, response.data, categoryPageUrl, fetchedAt, payloadHash);
        completed.set(product.sourceProductId, product);
        await checkpointHandle.appendFile(`${JSON.stringify(product)}\n`, "utf8");
      } catch (error) {
        failures.push({ sourceProductId: String(listItem.id), name: text(listItem.name), error: error.message });
      }
      processed += 1;
      if (processed % 25 === 0 || processed === pending.length) {
        process.stdout.write(`\rUrun detaylari: ${processed}/${pending.length}; hata: ${failures.length}`);
      }
      await sleep(args.delayMs);
    }
  }

  await Promise.all(Array.from({ length: args.concurrency }, () => worker()));
  await checkpointHandle.close();
  await rawHandle.close();
  process.stdout.write("\n");

  const products = listProducts.map((item) => completed.get(String(item.id))).filter(Boolean);
  const duplicateCounts = new Map();
  for (const product of products) duplicateCounts.set(product.duplicateKey, (duplicateCounts.get(product.duplicateKey) ?? 0) + 1);
  for (const product of products) {
    if (duplicateCounts.get(product.duplicateKey) > 1) {
      product.issues.push({ code: "DUPLICATE_CANDIDATE", duplicateKey: product.duplicateKey });
      if (product.importDecision === "IMPORT_READY") product.importDecision = "REVIEW_REQUIRED";
    }
  }
  const counts = Object.fromEntries(["IMPORT_READY", "REVIEW_REQUIRED", "REJECTED"].map((decision) => [
    decision,
    products.filter((product) => product.importDecision === decision).length,
  ]));

  const artifact = {
    schemaVersion: 2,
    generatedAt: new Date().toISOString(),
    source: "MIGROS",
    importPolicy: "Only IMPORT_READY records may proceed; review and rejected records are never auto-imported.",
    category: { slug, url: categoryPageUrl, reportedHitCount: listing.hitCount, pageCount: listing.pageCount },
    summary: {
      listedProducts: listProducts.length,
      completedProducts: products.length,
      failedProducts: failures.length,
      ...counts,
      duplicateCandidates: products.filter((product) => product.issues.some((issue) => issue.code === "DUPLICATE_CANDIDATE")).length,
    },
    products,
    failures,
  };
  await fs.writeFile(outputPath, `${JSON.stringify(artifact, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(artifact.summary, null, 2));
  console.log(`Normalize cikti: ${outputPath}`);
  console.log(`Raw evidence: ${rawOutputPath}`);
}

await main();
