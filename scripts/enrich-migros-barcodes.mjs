#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const DEFAULT_ROOT = "outputs/TR_Products";
const DEFAULT_DECISIONS = "outputs/TR_Products/barcode-enrichment/review-decisions.json";
const DEFAULT_EXTERNAL_VERIFIED =
  "outputs/TR_Products/barcode-enrichment/external-verified-barcodes.json";
const DEFAULT_OUTPUT_DIR = "outputs/TR_Products/barcode-enrichment";
const DEFAULT_OFF_FILES = [
  "outputs/product-data-readiness/s9-tr-internet/tr-off-candidates.tsv",
  "outputs/product-data-readiness/open-food-facts-tr-branded-v1-gate-raw.tsv",
  "outputs/product-data-readiness/open-food-facts-eu-branded-v1-gate-raw.tsv",
];

function parseArgs(argv) {
  const result = {
    root: DEFAULT_ROOT,
    decisions: DEFAULT_DECISIONS,
    externalVerified: DEFAULT_EXTERNAL_VERIFIED,
    outputDir: DEFAULT_OUTPUT_DIR,
    offFiles: DEFAULT_OFF_FILES,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--root" && value) result.root = value, index += 1;
    else if (key === "--decisions" && value) result.decisions = value, index += 1;
    else if (key === "--external-verified" && value) result.externalVerified = value, index += 1;
    else if (key === "--output-dir" && value) result.outputDir = value, index += 1;
    else if (key === "--off-file" && value) {
      if (result.offFiles === DEFAULT_OFF_FILES) result.offFiles = [];
      result.offFiles.push(value);
      index += 1;
    } else if (key === "--help") {
      console.log(`
Migros ürün barkodlarını A101 eşleşmeleri ve yerel Open Food Facts verisiyle zenginleştirir.

Kullanım:
  node scripts/enrich-migros-barcodes.mjs [seçenekler]

Seçenekler:
  --root <klasör>
  --decisions <json>
  --external-verified <json>
  --output-dir <klasör>
  --off-file <tsv>       Birden fazla kez verilebilir
  --help
`.trim());
      process.exit(0);
    } else throw new Error(`Bilinmeyen veya eksik parametre: ${key}`);
  }
  return result;
}

function fold(value) {
  return String(value ?? "")
    .toLocaleLowerCase("tr-TR")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replaceAll("ı", "i")
    .replaceAll("æ", "ae")
    .replaceAll("œ", "oe");
}

function prepared(value) {
  return fold(value)
    .replace(/(\d)[,.](\d)/g, "$1.$2")
    .replace(/&/g, " ve ");
}

function normalizeText(value) {
  return prepared(value)
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function normalizeBrand(value) {
  return normalizeText(value).replace(/\b(?:marka|brand)\b/g, "").trim();
}

function normalizeName(value) {
  return prepared(value)
    .replace(/\b\d+\s*[x×]\s*\d+(?:\.\d+)?\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l|adet)\b/g, " ")
    .replace(/\b\d+(?:\.\d+)?\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l|adet)\b/g, " ")
    .replace(/\b\d+\s*['’]?(li|lu)\b/g, " ")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function tokenName(value) {
  return [...new Set(normalizeName(value).split(" ").filter(Boolean))].sort().join(" ");
}

function descriptiveTokens(name, brand) {
  const brandTokens = new Set(normalizeBrand(brand).split(" ").filter(Boolean));
  return new Set(
    normalizeName(name).split(" ").filter((token) => token && !brandTokens.has(token)),
  );
}

function tokenSimilarity(left, right) {
  if (!left.size || !right.size) return 0;
  let intersection = 0;
  for (const token of left) if (right.has(token)) intersection += 1;
  return intersection / new Set([...left, ...right]).size;
}

function amountKey(amount, unit) {
  if (!Number.isFinite(amount) || amount <= 0) return "";
  if (["kg", "kilogram"].includes(unit)) return `g:${round(amount * 1000)}`;
  if (["gr", "gram", "g"].includes(unit)) return `g:${round(amount)}`;
  if (["lt", "litre", "l"].includes(unit)) return `ml:${round(amount * 1000)}`;
  if (["ml", "mililitre"].includes(unit)) return `ml:${round(amount)}`;
  return `adet:${round(amount)}`;
}

function round(value) {
  return Math.round(value * 1000) / 1000;
}

function amountText(product) {
  if (product.packageDisplayText) return String(product.packageDisplayText);
  if (product.packageAmount == null || !product.packageUnit) return "";
  return `${product.packageAmount} ${product.packageUnit}`;
}

function packageKey(name, fallbackAmount = "") {
  const source = prepared(name);
  const multi = source.match(
    /\b(\d+)\s*[x×]\s*(\d+(?:\.\d+)?)\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l)\b/,
  );
  if (multi) return amountKey(Number(multi[1]) * Number(multi[2]), multi[3]);
  const fromName = [...source.matchAll(
    /\b(\d+(?:\.\d+)?)\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l|adet)\b/g,
  )].at(-1);
  if (fromName) return amountKey(Number(fromName[1]), fromName[2]);
  const fallback = prepared(fallbackAmount).match(
    /(\d+(?:\.\d+)?)\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l|adet)\b/,
  );
  return fallback ? amountKey(Number(fallback[1]), fallback[2]) : "";
}

function identityKey(product) {
  return `${tokenName(product.name)}|${packageKey(product.name, amountText(product))}`;
}

function exactKey(product) {
  return `${normalizeName(product.name)}|${packageKey(product.name, amountText(product))}`;
}

function gtinCheckDigit(digitsWithoutCheck) {
  let sum = 0;
  for (let index = digitsWithoutCheck.length - 1, position = 0; index >= 0; index -= 1, position += 1) {
    sum += Number(digitsWithoutCheck[index]) * (position % 2 === 0 ? 3 : 1);
  }
  return String((10 - (sum % 10)) % 10);
}

function validGtin(value) {
  const digits = String(value ?? "").replace(/\D/g, "");
  if (![8, 12, 13, 14].includes(digits.length)) return null;
  return gtinCheckDigit(digits.slice(0, -1)) === digits.at(-1) ? digits : null;
}

async function loadProducts(root, prefix) {
  const entries = await fs.readdir(root, { withFileTypes: true });
  const files = entries
    .filter((entry) => entry.isDirectory()
      && entry.name.startsWith(`${prefix}-`)
      && entry.name.endsWith("-full"))
    .map((entry) => path.join(root, entry.name, "products.json"))
    .sort();
  const products = new Map();
  for (const file of files) {
    try {
      const document = JSON.parse(await fs.readFile(file, "utf8"));
      for (const product of document.products ?? []) {
        const id = String(product.sourceProductId ?? "").trim();
        if (!id) continue;
        const existing = products.get(id);
        if (!existing || Object.keys(product.nutrition ?? {}).length > Object.keys(existing.nutrition ?? {}).length) {
          products.set(id, { ...product, source: prefix === "a101" ? "A101" : "Migros" });
        }
      }
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }
  return [...products.values()];
}

function addCandidate(map, migros, candidate) {
  if (!map.has(migros.sourceProductId)) map.set(migros.sourceProductId, { migros, candidates: [] });
  map.get(migros.sourceProductId).candidates.push(candidate);
}

function uniqueBarcodes(candidates) {
  return [...new Set(candidates.map((row) => validGtin(row.barcode)).filter(Boolean))];
}

function parseTsv(text) {
  const lines = text.replace(/^\uFEFF/, "").split(/\r?\n/).filter(Boolean);
  if (!lines.length) return [];
  const headers = lines[0].split("\t");
  return lines.slice(1).map((line) => Object.fromEntries(
    line.split("\t").map((value, index) => [headers[index], value]),
  ));
}

async function loadOffProducts(files) {
  const byCode = new Map();
  for (const file of files) {
    try {
      const rows = parseTsv(await fs.readFile(file, "utf8"));
      for (const row of rows) {
        const code = validGtin(row.code || row.barcode);
        const name = row.product_name_tr || row.product_name || row.product_name_en || row.name;
        const brand = row.brands || row.brand;
        if (!code || !name || !brand) continue;
        const candidate = {
          barcode: code,
          name,
          brand,
          packageAmountText: row.serving_size || "",
          source: "Open Food Facts",
          sourceUrl: `https://world.openfoodfacts.org/product/${code}`,
          marketTier: row.tier || null,
          marketScore: Number(row.marketScore) || null,
          marketSignals: row.signals || null,
          countryTags: row.countries_tags || row.countries_tags_en || row.countryTags || null,
        };
        const existing = byCode.get(code);
        const candidateRank = candidate.marketTier === "STRICT" ? 3 : candidate.marketTier === "REVIEW" ? 2 : 1;
        const existingRank = existing?.marketTier === "STRICT" ? 3 : existing?.marketTier === "REVIEW" ? 2 : 1;
        if (!existing || candidateRank > existingRank) byCode.set(code, candidate);
      }
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }
  return [...byCode.values()];
}

function sourceCandidate(sourceProduct, method, confidence) {
  return {
    barcode: sourceProduct.primaryBarcode ?? sourceProduct.barcode,
    source: sourceProduct.source ?? "A101",
    sourceProductId: sourceProduct.sourceProductId ?? null,
    sourceName: sourceProduct.name,
    sourceBrand: sourceProduct.brand,
    sourceUrl: sourceProduct.sourceUrl,
    method,
    confidence,
    matchScore: sourceProduct.matchScore ?? null,
    marketTier: sourceProduct.marketTier ?? null,
    marketScore: sourceProduct.marketScore ?? null,
    marketSignals: sourceProduct.marketSignals ?? null,
    countryTags: sourceProduct.countryTags ?? null,
  };
}

function buildResult(row) {
  const barcodes = uniqueBarcodes(row.candidates);
  const validCandidates = row.candidates.filter((candidate) => validGtin(candidate.barcode));
  const methods = [...new Set(validCandidates.map((candidate) => candidate.method))];
  const sources = [...new Set(validCandidates.map((candidate) => candidate.source))];
  const hasHighConfidenceEvidence = validCandidates.some((candidate) => candidate.confidence === "high");
  const status = barcodes.length > 1
    ? "AMBIGUOUS"
    : barcodes.length === 1 && hasHighConfidenceEvidence
      ? "AUTO_CONFIRMED"
      : barcodes.length === 1
        ? "REVIEW_CANDIDATE"
        : "NOT_FOUND";
  return {
    migrosSourceProductId: row.migros.sourceProductId,
    migrosName: row.migros.name,
    migrosBrand: row.migros.brand,
    packageAmount: row.migros.packageAmount,
    packageUnit: row.migros.packageUnit,
    migrosUrl: row.migros.sourceUrl,
    barcode: ["AUTO_CONFIRMED", "REVIEW_CANDIDATE"].includes(status) ? barcodes[0] : null,
    candidateBarcodes: barcodes,
    status,
    methods,
    sources,
    evidence: validCandidates,
  };
}

const args = parseArgs(process.argv.slice(2));
const [migrosProducts, a101Products] = await Promise.all([
  loadProducts(args.root, "migros"),
  loadProducts(args.root, "a101"),
]);
const migrosById = new Map(migrosProducts.map((product) => [String(product.sourceProductId), product]));
const migrosByUrl = new Map(migrosProducts.map((product) => [String(product.sourceUrl), product]));
const a101ById = new Map(a101Products.map((product) => [String(product.sourceProductId), product]));

const candidatesByMigros = new Map();
const a101ByIdentity = new Map();
for (const product of a101Products) {
  const key = identityKey(product);
  if (!a101ByIdentity.has(key)) a101ByIdentity.set(key, []);
  a101ByIdentity.get(key).push(product);
}

for (const migros of migrosProducts) {
  for (const a101 of a101ByIdentity.get(identityKey(migros)) ?? []) {
    addCandidate(candidatesByMigros, migros, sourceCandidate(a101, "A101_EXACT_IDENTITY", "high"));
  }
}

try {
  const decisionDocument = JSON.parse(await fs.readFile(args.decisions, "utf8"));
  for (const decision of decisionDocument.decisions ?? []) {
    if (fold(decision.status) !== "ayni urun") continue;
    const migros = migrosByUrl.get(String(decision.migrosUrl));
    const a101 = a101ById.get(String(decision.a101SourceProductId));
    if (!migros || !a101) continue;
    addCandidate(candidatesByMigros, migros, sourceCandidate(a101, "A101_HUMAN_REVIEWED", "high"));
  }
} catch (error) {
  if (error.code !== "ENOENT") throw error;
}

try {
  const externalDocument = JSON.parse(await fs.readFile(args.externalVerified, "utf8"));
  for (const verified of externalDocument.products ?? []) {
    const migros = migrosById.get(String(verified.migrosSourceProductId));
    const barcode = validGtin(verified.barcode);
    if (!migros || !barcode || verified.confidence !== "high") continue;
    const evidenceSources = verified.evidenceSources ?? [];
    const hasOfficialManufacturerEvidence = evidenceSources.some(
      (source) => source.officialManufacturer === true,
    );
    if (evidenceSources.length < 2 && !hasOfficialManufacturerEvidence) continue;
    addCandidate(candidatesByMigros, migros, {
      barcode,
      source: "Verified Web",
      sourceProductId: null,
      sourceName: verified.migrosName,
      sourceBrand: migros.brand,
      sourceUrl: evidenceSources[0].url,
      evidenceSources,
      method: hasOfficialManufacturerEvidence && evidenceSources.length < 2
        ? "WEB_OFFICIAL_MANUFACTURER_VERIFIED"
        : "WEB_MULTI_SOURCE_VERIFIED",
      confidence: "high",
    });
  }
} catch (error) {
  if (error.code !== "ENOENT") throw error;
}

const a101Results = [...candidatesByMigros.values()].map(buildResult);
const confirmedMigrosIds = new Set(
  a101Results.filter((row) => row.status === "AUTO_CONFIRMED").map((row) => row.migrosSourceProductId),
);

const offProducts = await loadOffProducts(args.offFiles);
const offExact = new Map();
const offToken = new Map();
const offByBrandPackage = new Map();
const offByBrand = new Map();
for (const product of offProducts) {
  const pack = packageKey(product.name, product.packageAmountText);
  if (!pack) continue;
  const brand = normalizeBrand(product.brand);
  const exact = `${brand}|${normalizeName(product.name)}|${pack}`;
  const token = `${brand}|${tokenName(product.name)}|${pack}`;
  if (!offExact.has(exact)) offExact.set(exact, []);
  if (!offToken.has(token)) offToken.set(token, []);
  const brandPackage = `${brand}|${pack}`;
  if (!offByBrandPackage.has(brandPackage)) offByBrandPackage.set(brandPackage, []);
  if (!offByBrand.has(brand)) offByBrand.set(brand, []);
  offExact.get(exact).push(product);
  offToken.get(token).push(product);
  offByBrandPackage.get(brandPackage).push(product);
  offByBrand.get(brand).push(product);
}

for (const migros of migrosProducts) {
  if (confirmedMigrosIds.has(migros.sourceProductId)) continue;
  const brand = normalizeBrand(migros.brand);
  const pack = packageKey(migros.name, amountText(migros));
  if (!brand || !pack) continue;
  const exactMatches = offExact.get(`${brand}|${normalizeName(migros.name)}|${pack}`) ?? [];
  const tokenMatches = exactMatches.length
    ? []
    : offToken.get(`${brand}|${tokenName(migros.name)}|${pack}`) ?? [];
  for (const product of exactMatches) {
    addCandidate(candidatesByMigros, migros, sourceCandidate(product, "OFF_BRAND_EXACT_NAME_PACKAGE", "medium"));
  }
  for (const product of tokenMatches) {
    addCandidate(candidatesByMigros, migros, sourceCandidate(product, "OFF_BRAND_TOKEN_NAME_PACKAGE", "medium"));
  }
  if (!exactMatches.length && !tokenMatches.length) {
    const migrosTokens = descriptiveTokens(migros.name, migros.brand);
    const scored = (offByBrandPackage.get(`${brand}|${pack}`) ?? [])
      .map((product) => ({
        product,
        score: tokenSimilarity(migrosTokens, descriptiveTokens(product.name, product.brand)),
      }))
      .filter((row) => row.score >= 0.72)
      .sort((left, right) => right.score - left.score);
    const bestScore = scored[0]?.score ?? 0;
    for (const row of scored.filter((item) => item.score >= bestScore - 0.03).slice(0, 3)) {
      addCandidate(candidatesByMigros, migros, sourceCandidate(
        { ...row.product, matchScore: Math.round(row.score * 1000) / 1000 },
        "OFF_BRAND_PACKAGE_FUZZY",
        "medium",
      ));
    }
    if (!scored.length) {
      const nameOnlyScored = (offByBrand.get(brand) ?? [])
        .filter((product) => !packageKey(product.name, product.packageAmountText))
        .map((product) => ({
          product,
          score: tokenSimilarity(migrosTokens, descriptiveTokens(product.name, product.brand)),
        }))
        .filter((row) => row.score >= 0.82)
        .sort((left, right) => right.score - left.score);
      const bestNameScore = nameOnlyScored[0]?.score ?? 0;
      for (const row of nameOnlyScored.filter((item) => item.score >= bestNameScore - 0.03).slice(0, 3)) {
        addCandidate(candidatesByMigros, migros, sourceCandidate(
          { ...row.product, matchScore: Math.round(row.score * 1000) / 1000 },
          "OFF_BRAND_NAME_FUZZY_NO_PACKAGE",
          "medium",
        ));
      }
    }
  }
}

const results = migrosProducts
  .map((migros) => buildResult(candidatesByMigros.get(migros.sourceProductId) ?? { migros, candidates: [] }))
  .sort((left, right) => left.migrosName.localeCompare(right.migrosName, "tr-TR"));
const confirmed = results.filter((row) => row.status === "AUTO_CONFIRMED");
const ambiguous = results.filter((row) => row.status === "AMBIGUOUS");
const reviewCandidates = results.filter((row) => row.status === "REVIEW_CANDIDATE");
const notFound = results.filter((row) => row.status === "NOT_FOUND");

const output = {
  schemaVersion: "1.0",
  generatedAt: new Date().toISOString(),
  policy: {
    autoConfirmed: "Tek bir geçerli GTIN ve yüksek güvenli A101 eşleşme kanıtı",
    reviewCandidate: "Tek bir geçerli GTIN var ancak kaynak/kanıt ayrıca doğrulanmalı",
    ambiguous: "Aynı Migros ürünü için birden fazla geçerli GTIN adayı",
    notFound: "Yerel kaynaklarda güvenli barkod adayı bulunamadı",
  },
  summary: {
    migrosProducts: migrosProducts.length,
    a101Products: a101Products.length,
    openFoodFactsProducts: offProducts.length,
    autoConfirmed: confirmed.length,
    reviewCandidate: reviewCandidates.length,
    ambiguous: ambiguous.length,
    notFound: notFound.length,
    fromA101: confirmed.filter((row) => row.sources.includes("A101")).length,
    fromVerifiedWeb: confirmed.filter((row) => row.sources.includes("Verified Web")).length,
    fromOpenFoodFactsCandidates: results.filter((row) =>
      row.sources.includes("Open Food Facts") && !row.sources.includes("A101")).length,
  },
  products: results,
};

await fs.mkdir(args.outputDir, { recursive: true });
await Promise.all([
  fs.writeFile(path.join(args.outputDir, "migros-barcode-enrichment.json"), `${JSON.stringify(output, null, 2)}\n`),
  fs.writeFile(path.join(args.outputDir, "migros-barcode-confirmed.json"), `${JSON.stringify(confirmed, null, 2)}\n`),
  fs.writeFile(
    path.join(args.outputDir, "migros-barcode-review-queue.json"),
    `${JSON.stringify([...reviewCandidates, ...ambiguous], null, 2)}\n`,
  ),
  fs.writeFile(path.join(args.outputDir, "migros-barcode-not-found.json"), `${JSON.stringify(notFound, null, 2)}\n`),
]);

console.log(JSON.stringify(output.summary, null, 2));

