#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const DEFAULT_ROOT = "outputs/TR_Products";
const DEFAULT_OUTPUT = "outputs/TR_Products/merged-a101-migros/products.json";
const DEFAULT_REVIEW_OUTPUT = "outputs/TR_Products/merged-a101-migros/review-candidates.json";

function printHelp() {
  console.log(`
A101 ve Migros kategori JSON dosyalarını güvenli biçimde birleştirir.

Kullanım:
  node scripts/merge-a101-migros-products.mjs [seçenekler]

Seçenekler:
  --root <klasör>          Kaynak kategori klasörlerinin kökü
  --output <dosya>         Birleşik ürün JSON çıktısı
  --review-output <dosya>  Olası eşleşmelerin inceleme çıktısı
  --review-threshold <0-1> İnceleme adayı benzerlik alt sınırı (varsayılan 0.72)
  --help                   Bu yardımı göster

Yalnızca benzersiz marka + paket + ürün adı eşleşmeleri otomatik birleştirilir.
Benzer fakat kesin olmayan ürünler review-candidates.json dosyasına yazılır.
`.trim());
}

function parseArgs(argv) {
  const args = {
    root: DEFAULT_ROOT,
    output: DEFAULT_OUTPUT,
    reviewOutput: DEFAULT_REVIEW_OUTPUT,
    reviewThreshold: 0.72,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--help") args.help = true;
    else if (key === "--root" && value) args.root = value, index += 1;
    else if (key === "--output" && value) args.output = value, index += 1;
    else if (key === "--review-output" && value) args.reviewOutput = value, index += 1;
    else if (key === "--review-threshold" && value) {
      args.reviewThreshold = Number(value);
      index += 1;
    } else throw new Error(`Bilinmeyen veya eksik parametre: ${key}`);
  }
  if (!Number.isFinite(args.reviewThreshold)
      || args.reviewThreshold < 0
      || args.reviewThreshold > 1) {
    throw new Error("--review-threshold 0 ile 1 arasında olmalıdır");
  }
  return args;
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

function normalizeText(value) {
  return fold(value)
    .replace(/&/g, " ve ")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

function normalizeBrand(value) {
  return normalizeText(value).replace(/\b(?:marka|brand)\b/g, "").trim();
}

function normalizeProductName(value) {
  return normalizeText(value)
    .replace(/\b(\d+)\s*[xX]\s*(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|lt|l|adet)\b/g, " ")
    .replace(/\b\d+(?:[.,]\d+)?\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l|adet)\b/g, " ")
    .replace(/\b\d+\s*(li|lu)\b/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokenKey(value) {
  return [...new Set(normalizeProductName(value).split(" ").filter(Boolean))]
    .sort()
    .join(" ");
}

function packageKey(product) {
  const namedMultiPack = multiPackKeyFromName(product.name);
  if (namedMultiPack) return namedMultiPack;
  const amount = Number(product.packageAmount);
  const rawUnit = normalizeText(product.packageUnit).toUpperCase();
  if (!Number.isFinite(amount) || amount <= 0 || !rawUnit) return null;
  if (rawUnit === "G") return `G:${roundAmount(amount)}`;
  if (rawUnit === "KG") return `G:${roundAmount(amount * 1_000)}`;
  if (rawUnit === "ML") return `ML:${roundAmount(amount)}`;
  if (rawUnit === "L" || rawUnit === "LT") return `ML:${roundAmount(amount * 1_000)}`;
  if (rawUnit === "ADET") return `ADET:${roundAmount(amount)}`;
  return null;
}

function multiPackKeyFromName(name) {
  const normalized = fold(name).replace(/(\d)[,.](\d)/g, "$1.$2");
  const match = normalized.match(
    /\b(\d+)\s*[x×]\s*(\d+(?:\.\d+)?)\s*(kg|kilogram|gr|gram|g|ml|mililitre|lt|litre|l)\b/,
  );
  if (!match) return null;
  const count = Number(match[1]);
  const amount = Number(match[2]);
  if (!Number.isFinite(count) || !Number.isFinite(amount) || count <= 0 || amount <= 0) return null;
  const unit = match[3];
  if (["kg", "kilogram"].includes(unit)) return `G:${roundAmount(count * amount * 1_000)}`;
  if (["gr", "gram", "g"].includes(unit)) return `G:${roundAmount(count * amount)}`;
  if (["lt", "litre", "l"].includes(unit)) return `ML:${roundAmount(count * amount * 1_000)}`;
  return `ML:${roundAmount(count * amount)}`;
}

function roundAmount(value) {
  return Math.round(value * 1_000) / 1_000;
}

function uniq(values) {
  return [...new Set(values.filter((value) => value !== null && value !== undefined && value !== ""))];
}

function resolvedPrimaryBarcode(product) {
  if (product.primaryBarcode) return product.primaryBarcode;
  return product.barcodeStatus === "MEVCUT" ? (product.barcode ?? null) : null;
}

function richness(product) {
  return Object.values(product.nutrition ?? {}).filter((value) => value !== null && value !== "").length * 10
    + (product.packageAmount != null ? 3 : 0)
    + (product.packageUnit ? 3 : 0)
    + (product.primaryBarcode || product.barcode ? 5 : 0)
    + (product.imageSourceUrl ? 1 : 0);
}

function mergeDuplicate(existing, incoming, inputFile) {
  const best = richness(incoming) > richness(existing) ? incoming : existing;
  const other = best === incoming ? existing : incoming;
  return {
    ...other,
    ...best,
    categoryPaths: uniquePaths([
      ...(existing.categoryPaths ?? [existing.categoryPath]),
      ...(incoming.categoryPaths ?? [incoming.categoryPath]),
    ]),
    inputFiles: uniq([...(existing.inputFiles ?? []), ...(incoming.inputFiles ?? []), inputFile]),
    reviewNotes: uniq([...(existing.reviewNotes ?? []), ...(incoming.reviewNotes ?? [])]),
  };
}

function uniquePaths(paths) {
  const result = new Map();
  for (const value of paths) {
    if (!Array.isArray(value) || value.length === 0) continue;
    const clean = value.map(String).filter(Boolean);
    if (clean.length) result.set(clean.join(" > "), clean);
  }
  return [...result.values()];
}

async function discoverFiles(root, source) {
  const entries = await fs.readdir(root, { withFileTypes: true });
  const prefix = `${source.toLowerCase()}-`;
  const candidates = entries
    .filter((entry) => entry.isDirectory()
      && entry.name.startsWith(prefix)
      && entry.name.endsWith("-full"))
    .map((entry) => path.join(root, entry.name, "products.json"))
    .sort();
  const files = [];
  for (const candidate of candidates) {
    try {
      await fs.access(candidate);
      files.push(candidate);
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }
  return files;
}

async function loadSource(files, source) {
  const productsById = new Map();
  let rawRows = 0;
  for (const file of files) {
    const document = JSON.parse(await fs.readFile(file, "utf8"));
    if (!Array.isArray(document.products)) throw new Error(`${file}: products dizisi bulunamadı`);
    for (const product of document.products) {
      rawRows += 1;
      const sourceProductId = String(product.sourceProductId ?? "").trim();
      if (!sourceProductId) throw new Error(`${file}: sourceProductId eksik ürün bulundu`);
      const prepared = {
        ...product,
        source,
        sourceProductId,
        categoryPaths: uniquePaths([product.categoryPath]),
        inputFiles: [path.relative(process.cwd(), file)],
      };
      const existing = productsById.get(sourceProductId);
      productsById.set(
        sourceProductId,
        existing ? mergeDuplicate(existing, prepared, path.relative(process.cwd(), file)) : prepared,
      );
    }
  }
  return { products: [...productsById.values()], rawRows };
}

function buildUniqueIndex(products, keyBuilder) {
  const grouped = new Map();
  for (const product of products) {
    const key = keyBuilder(product);
    if (!key) continue;
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key).push(product);
  }
  return new Map([...grouped].filter(([, rows]) => rows.length === 1).map(([key, rows]) => [key, rows[0]]));
}

function strictKey(product) {
  const brand = normalizeBrand(product.brand);
  const pack = packageKey(product);
  const name = normalizeProductName(product.name);
  return brand && pack && name ? `${brand}|${pack}|${name}` : null;
}

function sortedTokenKey(product) {
  const brand = normalizeBrand(product.brand);
  const pack = packageKey(product);
  const name = tokenKey(product.name);
  return brand && pack && name ? `${brand}|${pack}|${name}` : null;
}

function fullNameKey(product) {
  const pack = packageKey(product);
  const name = normalizeText(product.name);
  return pack && name ? `${pack}|${name}` : null;
}

function pairProducts(migrosProducts, a101Products) {
  const matchedMigros = new Set();
  const matchedA101 = new Set();
  const pairs = [];
  const passes = [
    ["STRICT", strictKey],
    ["TOKEN_EXACT", sortedTokenKey],
    ["FULL_NAME", fullNameKey],
  ];

  for (const [method, keyBuilder] of passes) {
    const remainingMigros = migrosProducts.filter((row) => !matchedMigros.has(row.sourceProductId));
    const remainingA101 = a101Products.filter((row) => !matchedA101.has(row.sourceProductId));
    const migrosIndex = buildUniqueIndex(remainingMigros, keyBuilder);
    const a101Index = buildUniqueIndex(remainingA101, keyBuilder);
    for (const [key, migros] of migrosIndex) {
      const a101 = a101Index.get(key);
      if (!a101) continue;
      matchedMigros.add(migros.sourceProductId);
      matchedA101.add(a101.sourceProductId);
      pairs.push({ migros, a101, method, score: 1 });
    }
  }
  return { pairs, matchedMigros, matchedA101 };
}

function levenshteinSimilarity(left, right) {
  if (left === right) return 1;
  if (!left || !right) return 0;
  const previous = Array.from({ length: right.length + 1 }, (_, index) => index);
  for (let i = 1; i <= left.length; i += 1) {
    let diagonal = previous[0];
    previous[0] = i;
    for (let j = 1; j <= right.length; j += 1) {
      const above = previous[j];
      previous[j] = Math.min(
        previous[j] + 1,
        previous[j - 1] + 1,
        diagonal + (left[i - 1] === right[j - 1] ? 0 : 1),
      );
      diagonal = above;
    }
  }
  return 1 - previous[right.length] / Math.max(left.length, right.length);
}

function tokenJaccard(left, right) {
  const a = new Set(left.split(" ").filter(Boolean));
  const b = new Set(right.split(" ").filter(Boolean));
  const intersection = [...a].filter((token) => b.has(token)).length;
  const union = new Set([...a, ...b]).size;
  return union ? intersection / union : 0;
}

function nameSimilarity(left, right) {
  const a = normalizeProductName(left);
  const b = normalizeProductName(right);
  return roundAmount(levenshteinSimilarity(a, b) * 0.55 + tokenJaccard(a, b) * 0.45);
}

function buildReviewCandidates(migrosProducts, a101Products, matchedMigros, matchedA101, threshold) {
  const candidateGroups = new Map();
  for (const product of a101Products) {
    if (matchedA101.has(product.sourceProductId)) continue;
    const key = `${normalizeBrand(product.brand)}|${packageKey(product) ?? ""}`;
    if (key === "|") continue;
    if (!candidateGroups.has(key)) candidateGroups.set(key, []);
    candidateGroups.get(key).push(product);
  }
  const review = [];
  for (const migros of migrosProducts) {
    if (matchedMigros.has(migros.sourceProductId)) continue;
    const key = `${normalizeBrand(migros.brand)}|${packageKey(migros) ?? ""}`;
    if (key === "|") continue;
    const ranked = (candidateGroups.get(key) ?? [])
      .map((a101) => ({ a101, score: nameSimilarity(migros.name, a101.name) }))
      .filter((candidate) => candidate.score >= threshold)
      .sort((left, right) => right.score - left.score)
      .slice(0, 3);
    if (!ranked.length) continue;
    review.push({
      migros: productReference(migros),
      candidates: ranked.map(({ a101, score }) => ({ ...productReference(a101), score })),
    });
  }
  return review.sort((left, right) => right.candidates[0].score - left.candidates[0].score);
}

function productReference(product) {
  return {
    sourceProductId: product.sourceProductId,
    name: product.name,
    brand: product.brand,
    packageAmount: product.packageAmount,
    packageUnit: product.packageUnit,
    primaryBarcode: resolvedPrimaryBarcode(product),
    sourceUrl: product.sourceUrl ?? null,
  };
}

function mergedProduct(pair, order) {
  const { migros, a101, method, score } = pair;
  const barcodes = uniq([...(a101.barcodes ?? []), a101.primaryBarcode, a101.barcode]);
  const primaryBarcode = resolvedPrimaryBarcode(a101);
  const imageSourceUrls = uniq([
    ...(a101.imageSourceUrls ?? []),
    a101.imageSourceUrl,
    migros.imageSourceUrl,
  ]);
  return {
    order,
    catalogId: primaryBarcode
      ? `GTIN:${primaryBarcode}`
      : `MERGED:${migros.sourceProductId}:${a101.sourceProductId}`,
    source: "MIGROS+A101",
    sourceProductIds: { migros: migros.sourceProductId, a101: a101.sourceProductId },
    name: migros.name || a101.name,
    brand: migros.brand || a101.brand,
    mainCategory: migros.mainCategory || a101.mainCategory,
    subcategory: migros.subcategory || a101.subcategory,
    categoryPaths: uniquePaths([...(migros.categoryPaths ?? []), ...(a101.categoryPaths ?? [])]),
    packageAmount: a101.packageAmount ?? migros.packageAmount ?? null,
    packageUnit: a101.packageUnit ?? migros.packageUnit ?? null,
    netWeight: a101.netWeight ?? migros.packageAmount ?? null,
    pieceCount: a101.pieceCount ?? null,
    amountPerPiece: a101.amountPerPiece ?? null,
    amountPerPieceUnit: a101.amountPerPieceUnit ?? null,
    nutritionBasis: migros.nutritionBasis ?? a101.nutritionBasis ?? null,
    nutrition: Object.keys(migros.nutrition ?? {}).length ? migros.nutrition : (a101.nutrition ?? {}),
    nutritionRaw: (migros.nutritionRaw ?? []).length ? migros.nutritionRaw : (a101.nutritionRaw ?? []),
    nutritionStatus: (migros.nutritionRaw ?? []).length ? migros.nutritionStatus : a101.nutritionStatus,
    barcode: primaryBarcode,
    primaryBarcode,
    barcodes,
    barcodeStatus: a101.barcodeStatus,
    imageSourceUrl: a101.imageSourceUrl ?? migros.imageSourceUrl ?? null,
    imageSourceUrls,
    sourceUrls: { migros: migros.sourceUrl ?? null, a101: a101.sourceUrl ?? null },
    inputFiles: uniq([...(migros.inputFiles ?? []), ...(a101.inputFiles ?? [])]),
    matchStatus: "MATCHED",
    matchMethod: method,
    matchScore: score,
    reviewNotes: uniq([...(migros.reviewNotes ?? []), ...(a101.reviewNotes ?? [])]),
  };
}

function sourceOnlyProduct(product, order) {
  const isA101 = product.source === "A101";
  const barcodes = uniq([...(product.barcodes ?? []), product.primaryBarcode, product.barcode]);
  const primaryBarcode = resolvedPrimaryBarcode(product);
  return {
    order,
    catalogId: primaryBarcode
      ? `GTIN:${primaryBarcode}`
      : `${product.source}:${product.sourceProductId}`,
    source: product.source,
    sourceProductIds: {
      migros: isA101 ? null : product.sourceProductId,
      a101: isA101 ? product.sourceProductId : null,
    },
    name: product.name,
    brand: product.brand,
    mainCategory: product.mainCategory,
    subcategory: product.subcategory,
    categoryPaths: product.categoryPaths ?? uniquePaths([product.categoryPath]),
    packageAmount: product.packageAmount ?? null,
    packageUnit: product.packageUnit ?? null,
    netWeight: product.netWeight ?? product.packageAmount ?? null,
    pieceCount: product.pieceCount ?? null,
    amountPerPiece: product.amountPerPiece ?? null,
    amountPerPieceUnit: product.amountPerPieceUnit ?? null,
    nutritionBasis: product.nutritionBasis ?? null,
    nutrition: product.nutrition ?? {},
    nutritionRaw: product.nutritionRaw ?? [],
    nutritionStatus: product.nutritionStatus,
    barcode: primaryBarcode,
    primaryBarcode,
    barcodes,
    barcodeStatus: product.barcodeStatus,
    imageSourceUrl: product.imageSourceUrl ?? null,
    imageSourceUrls: uniq([...(product.imageSourceUrls ?? []), product.imageSourceUrl]),
    sourceUrls: {
      migros: isA101 ? null : product.sourceUrl ?? null,
      a101: isA101 ? product.sourceUrl ?? null : null,
    },
    inputFiles: product.inputFiles ?? [],
    matchStatus: `ONLY_${product.source}`,
    matchMethod: null,
    matchScore: null,
    reviewNotes: product.reviewNotes ?? [],
  };
}

function countBy(values, selector) {
  const counts = {};
  for (const value of values) {
    const key = selector(value);
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return counts;
}

async function writeJson(file, value) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return printHelp();

  const [migrosFiles, a101Files] = await Promise.all([
    discoverFiles(args.root, "migros"),
    discoverFiles(args.root, "a101"),
  ]);
  if (!migrosFiles.length) throw new Error(`${args.root}: Migros *-full/products.json bulunamadı`);
  if (!a101Files.length) throw new Error(`${args.root}: A101 *-full/products.json bulunamadı`);

  const [migrosData, a101Data] = await Promise.all([
    loadSource(migrosFiles, "MIGROS"),
    loadSource(a101Files, "A101"),
  ]);
  const matching = pairProducts(migrosData.products, a101Data.products);
  const reviewCandidates = buildReviewCandidates(
    migrosData.products,
    a101Data.products,
    matching.matchedMigros,
    matching.matchedA101,
    args.reviewThreshold,
  );

  const products = [];
  for (const pair of matching.pairs) products.push(mergedProduct(pair, products.length + 1));
  for (const product of migrosData.products) {
    if (!matching.matchedMigros.has(product.sourceProductId)) {
      products.push(sourceOnlyProduct(product, products.length + 1));
    }
  }
  for (const product of a101Data.products) {
    if (!matching.matchedA101.has(product.sourceProductId)) {
      products.push(sourceOnlyProduct(product, products.length + 1));
    }
  }

  const output = {
    schemaVersion: "1.0",
    generatedAt: new Date().toISOString(),
    matchingPolicy: {
      automatic: "Benzersiz marka + kanonik paket + normalize ürün adı/token eşleşmesi",
      fuzzyMatchesApplied: false,
      reviewThreshold: args.reviewThreshold,
      sourcePriority: {
        nutrition: "MIGROS",
        barcode: "A101",
        package: "A101, yoksa MIGROS",
        image: "A101, yoksa MIGROS",
      },
    },
    inputs: {
      migrosFiles: migrosFiles.map((file) => path.relative(process.cwd(), file)),
      a101Files: a101Files.map((file) => path.relative(process.cwd(), file)),
    },
    summary: {
      migrosRawRows: migrosData.rawRows,
      migrosUniqueProducts: migrosData.products.length,
      a101RawRows: a101Data.rawRows,
      a101UniqueProducts: a101Data.products.length,
      matchedProducts: matching.pairs.length,
      matchesByMethod: countBy(matching.pairs, (pair) => pair.method),
      onlyMigros: migrosData.products.length - matching.pairs.length,
      onlyA101: a101Data.products.length - matching.pairs.length,
      combinedProducts: products.length,
      productsWithBarcode: products.filter((product) => product.primaryBarcode).length,
      productsWithNutrition: products.filter((product) => Object.keys(product.nutrition ?? {}).length).length,
      productsWithPackageAmount: products.filter((product) => product.packageAmount != null && product.packageUnit).length,
      reviewCandidateGroups: reviewCandidates.length,
    },
    products,
  };
  const reviewOutput = {
    schemaVersion: "1.0",
    generatedAt: output.generatedAt,
    threshold: args.reviewThreshold,
    note: "Bu kayıtlar otomatik birleştirilmedi; insan kontrolü gerekir.",
    count: reviewCandidates.length,
    candidates: reviewCandidates,
  };
  await Promise.all([
    writeJson(args.output, output),
    writeJson(args.reviewOutput, reviewOutput),
  ]);
  console.log(JSON.stringify(output.summary));
  console.log(path.resolve(args.output));
  console.log(path.resolve(args.reviewOutput));
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
