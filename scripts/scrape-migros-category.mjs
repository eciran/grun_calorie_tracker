#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const BASE_URL = "https://www.migros.com.tr";
const DEFAULT_CATEGORY = "dondurma-c-41b";
const DEFAULT_OUTPUT = "outputs/migros-dondurma-full/products.json";

function parseArgs(argv) {
  const args = {
    category: DEFAULT_CATEGORY,
    output: DEFAULT_OUTPUT,
    concurrency: 5,
    delayMs: 150,
    maxProducts: null,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    const value = argv[i + 1];
    if (key === "--category" && value) args.category = value, i += 1;
    else if (key === "--output" && value) args.output = value, i += 1;
    else if (key === "--concurrency" && value) args.concurrency = Number(value), i += 1;
    else if (key === "--delay-ms" && value) args.delayMs = Number(value), i += 1;
    else if (key === "--max-products" && value) args.maxProducts = Number(value), i += 1;
  }
  if (!Number.isInteger(args.concurrency) || args.concurrency < 1 || args.concurrency > 10) {
    throw new Error("--concurrency must be an integer between 1 and 10");
  }
  if (!Number.isFinite(args.delayMs) || args.delayMs < 0) {
    throw new Error("--delay-ms must be zero or greater");
  }
  return args;
}

function categorySlug(value) {
  try {
    return new URL(value).pathname.replace(/^\/+|\/+$/g, "");
  } catch {
    return value.replace(/^\/+|\/+$/g, "");
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchJson(url, attempts = 4) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: {
          Accept: "application/json",
          Referer: `${BASE_URL}/`,
          "User-Agent": "GRun-Nutrition-Catalog/0.1",
        },
      });
      if (response.ok) return await response.json();
      const body = await response.text();
      const retryable = response.status === 429 || response.status >= 500;
      if (!retryable) {
        throw new Error(`HTTP ${response.status}: ${body.slice(0, 300)}`);
      }
      lastError = new Error(`HTTP ${response.status}: ${body.slice(0, 300)}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt < attempts) await sleep(500 * 2 ** (attempt - 1));
  }
  throw lastError;
}

async function loadCheckpoint(checkpointPath) {
  try {
    const text = await fs.readFile(checkpointPath, "utf8");
    const products = new Map();
    for (const line of text.split(/\r?\n/)) {
      if (!line.trim()) continue;
      const row = JSON.parse(line);
      if (row?.sourceProductId) products.set(String(row.sourceProductId), row);
    }
    return products;
  } catch (error) {
    if (error.code === "ENOENT") return new Map();
    throw error;
  }
}

function getProperty(detail, type, customId) {
  return (detail.propertyInfosMap?.[type] ?? []).find((item) => item.customId === customId)?.value ?? null;
}

function inferPackage(name, detail) {
  const netAmount = getProperty(detail, "MAIN", "netKg");
  const normalizedName = name.toLocaleUpperCase("tr-TR");
  const match = normalizedName.match(/(?:^|\s)(\d+(?:[.,]\d+)?)\s*(KG|G|ML|LT|L|ADET)\s*$/u);
  if (match) {
    return {
      amount: Number(match[1].replace(",", ".")),
      unit: match[2] === "LT" ? "L" : match[2],
    };
  }
  if (/\bKG\s*$/u.test(normalizedName)) return { amount: null, unit: "KG / TARTIMLI" };
  if (netAmount && Number.isFinite(Number(netAmount))) {
    return { amount: Number(netAmount), unit: "G/ML (KAYNAK BELİRSİZ)" };
  }
  return { amount: null, unit: null };
}

function nutritionMap(detail) {
  const entries = detail.propertyInfosMap?.NUTRITIONAL ?? [];
  const byCustomId = {};
  const raw = [];
  for (const item of entries) {
    const key = item.customId || item.name;
    byCustomId[key] = item.value;
    raw.push({
      label: item.name,
      customId: item.customId ?? null,
      value: item.value,
    });
  }
  return { byCustomId, raw };
}

function imageUrl(detail) {
  const urls = detail.images?.[0]?.urls ?? {};
  return urls.x2 || urls.x1 || urls.original || Object.values(urls).find(Boolean) || null;
}

function reviewNotes(nutrition) {
  const notes = [];
  if (nutrition.raw.length === 0) notes.push("Ürün detay sayfasında besin değerleri bulunmuyor.");
  for (const item of nutrition.raw) {
    const value = Number(String(item.value).replace(",", "."));
    if (!Number.isFinite(value) || value < 0) {
      notes.push(`${item.label}: geçersiz kaynak değeri (${item.value}).`);
    } else if (/\(g\)/i.test(item.label) && value > 100) {
      notes.push(`${item.label}: 100 g/ml bazında 100'ün üzerinde kaynak değeri (${item.value}).`);
    } else if (/kcal/i.test(item.label) && value > 1000) {
      notes.push(`${item.label}: 1000 kcal üzerinde kaynak değeri (${item.value}).`);
    }
  }
  return notes;
}

function normalizeProduct(listItem, detail, categoryPageUrl) {
  const nutrition = nutritionMap(detail);
  const ascendingPath = [...(detail.categoryAscendants ?? [])].reverse().map((item) => item.name);
  const categoryPath = [...ascendingPath, detail.category?.name].filter(Boolean);
  const packageInfo = inferPackage(detail.name || listItem.name, detail);
  const sourceUrl = `${BASE_URL}/${detail.prettyName || listItem.prettyName}`;
  return {
    sourceProductId: String(detail.id ?? listItem.id),
    name: detail.name ?? listItem.name,
    brand: detail.brand?.name ?? listItem.brand?.name ?? null,
    mainCategory: categoryPath[0] ?? null,
    subcategory: categoryPath.at(-1) ?? null,
    categoryPath,
    packageAmount: packageInfo.amount,
    packageUnit: packageInfo.unit,
    nutritionBasis: nutrition.raw.length ? "100 g / ml" : null,
    nutrition: nutrition.byCustomId,
    nutritionRaw: nutrition.raw,
    barcode: null,
    barcodeStatus: "ARAŞTIRILACAK",
    imageSourceUrl: imageUrl(detail),
    sourceUrl,
    categoryPageUrl,
    nutritionStatus: nutrition.raw.length ? "MEVCUT" : "EKSİK",
    reviewNotes: reviewNotes(nutrition),
  };
}

async function collectListing(slug) {
  const firstUrl = `${BASE_URL}/rest/search/screens/${encodeURIComponent(slug)}`;
  const first = await fetchJson(firstUrl);
  if (!first.successful || !first.data?.searchInfo) {
    throw new Error("Migros category response did not contain searchInfo");
  }
  const pageCount = first.data.searchInfo.pageCount;
  const hitCount = first.data.searchInfo.hitCount;
  const products = [...first.data.searchInfo.storeProductInfos];
  for (let page = 2; page <= pageCount; page += 1) {
    const response = await fetchJson(`${firstUrl}?page=${page}`);
    products.push(...(response.data?.searchInfo?.storeProductInfos ?? []));
    process.stdout.write(`\rKategori sayfaları: ${page}/${pageCount}`);
    await sleep(75);
  }
  const categoryId = new URLSearchParams(first.data.searchQuery ?? "").get("category-id");
  if (!categoryId) throw new Error("Migros category response did not contain category-id");
  const sorts = [
    "once-en-dusuk-fiyat",
    "once-en-yuksek-fiyat",
    "cok-satanlar",
  ];
  let completedSortedPages = 0;
  const totalSortedPages = pageCount * sorts.length;
  for (const sort of sorts) {
    for (let page = 1; page <= pageCount; page += 1) {
      const params = new URLSearchParams({
        "category-id": categoryId,
        sort,
        page: String(page),
      });
      const response = await fetchJson(`${BASE_URL}/rest/products/search?${params}`);
      products.push(...(response.data?.storeProductInfos ?? []));
      completedSortedPages += 1;
      process.stdout.write(
        `\rStable category listings: ${completedSortedPages}/${totalSortedPages}`,
      );
      await sleep(75);
    }
  }
  process.stdout.write("\n");
  const unique = [...new Map(products.map((item) => [String(item.id), item])).values()];
  return { hitCount, pageCount, products: unique };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const slug = categorySlug(args.category);
  const categoryPageUrl = `${BASE_URL}/${slug}`;
  const outputPath = path.resolve(args.output);
  const checkpointPath = `${outputPath}.jsonl`;
  await fs.mkdir(path.dirname(outputPath), { recursive: true });

  console.log(`Kategori listesi alınıyor: ${categoryPageUrl}`);
  const listing = await collectListing(slug);
  const listProducts = args.maxProducts
    ? listing.products.slice(0, args.maxProducts)
    : listing.products;
  console.log(`Liste: ${listProducts.length} benzersiz ürün; bildirilen toplam: ${listing.hitCount}`);

  const completed = await loadCheckpoint(checkpointPath);
  const pending = listProducts.filter((item) => !completed.has(String(item.id)));
  console.log(`Detay bekleyen: ${pending.length}; checkpoint içinde: ${completed.size}`);

  let cursor = 0;
  let processed = 0;
  let failures = 0;
  const failedProducts = [];
  const appendHandle = await fs.open(checkpointPath, "a");

  async function worker() {
    while (true) {
      const index = cursor;
      cursor += 1;
      if (index >= pending.length) return;
      const listItem = pending[index];
      try {
        const response = await fetchJson(`${BASE_URL}/rest/products/${listItem.id}`);
        if (!response.successful || !response.data) throw new Error("Product detail response was unsuccessful");
        const product = normalizeProduct(listItem, response.data, categoryPageUrl);
        completed.set(String(listItem.id), product);
        await appendHandle.appendFile(`${JSON.stringify(product)}\n`, "utf8");
      } catch (error) {
        failures += 1;
        failedProducts.push({
          sourceProductId: String(listItem.id),
          name: listItem.name,
          sourceUrl: `${BASE_URL}/${listItem.prettyName}`,
          error: error.message,
        });
      }
      processed += 1;
      if (processed % 25 === 0 || processed === pending.length) {
        process.stdout.write(`\rÜrün detayları: ${processed}/${pending.length}; hata: ${failures}`);
      }
      if (args.delayMs) await sleep(args.delayMs);
    }
  }

  await Promise.all(Array.from({ length: args.concurrency }, () => worker()));
  await appendHandle.close();
  process.stdout.write("\n");

  const ordered = listProducts
    .map((item) => completed.get(String(item.id)))
    .filter(Boolean)
    .map((item, index) => ({ order: index + 1, ...item }));
  const artifact = {
    schemaVersion: 1,
    category: {
      slug,
      url: categoryPageUrl,
      reportedHitCount: listing.hitCount,
      pageCount: listing.pageCount,
    },
    summary: {
      listedProducts: listProducts.length,
      completedProducts: ordered.length,
      failedProducts: failedProducts.length,
      nutritionAvailable: ordered.filter((item) => item.nutritionStatus === "MEVCUT").length,
      nutritionMissing: ordered.filter((item) => item.nutritionStatus === "EKSİK").length,
    },
    products: ordered,
    failures: failedProducts,
  };
  await fs.writeFile(outputPath, `${JSON.stringify(artifact, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(artifact.summary));
  console.log(outputPath);
}

await main();
