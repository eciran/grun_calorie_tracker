import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";

const DEFAULTS = {
  ukImport: "outputs/product-data-readiness/open-food-facts-uk-ie-branded-v1-gate-import.csv",
  trOffImport: "outputs/product-data-readiness/open-food-facts-tr-branded-v1-gate-import.csv",
  trRetailCatalog: "outputs/TR_Products/merged-a101-migros-iyas-deduplicated/products.json",
  trPolicyQueue: "outputs/TR_Products/barcode-enrichment/primary-catalog-barcode-queue.json",
  trExcluded: "outputs/TR_Products/barcode-enrichment/manual-excluded-products.json",
  output: "outputs/product-catalog-aug08/catalog-capacity-audit.json",
};

function parseArgs(argv) {
  const args = { root: process.cwd(), ...DEFAULTS };
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith("--")) throw new Error(`Unexpected argument: ${token}`);
    const key = token.slice(2).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    if (!(key in args)) throw new Error(`Unknown option: ${token}`);
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) throw new Error(`${token} requires a value`);
    args[key] = value;
    index += 1;
  }
  args.root = path.resolve(args.root);
  for (const key of Object.keys(DEFAULTS)) args[key] = path.resolve(args.root, args[key]);
  return args;
}

function csvLine(line) {
  const values = [];
  let value = "";
  let quoted = false;
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    if (char === '"') {
      if (quoted && line[index + 1] === '"') {
        value += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      values.push(value);
      value = "";
    } else {
      value += char;
    }
  }
  values.push(value);
  return values;
}

function finite(value) {
  if (value === null || value === undefined || String(value).trim() === "") return null;
  const parsed = Number(String(value).replace(",", "."));
  return Number.isFinite(parsed) ? parsed : null;
}

function plausibleCore(calories, protein, fat, carbs) {
  const values = [calories, protein, fat, carbs].map(finite);
  if (values.some((value) => value === null)) return false;
  const [energy, p, f, c] = values;
  return energy >= 0 && energy <= 1000
    && [p, f, c].every((value) => value >= 0 && value <= 100)
    && p + f + c <= 110;
}

function validGtin(value) {
  const gtin = String(value ?? "").trim();
  if (!/^(?:\d{8}|\d{12}|\d{13}|\d{14})$/.test(gtin)) return false;
  const digits = [...gtin].map(Number);
  const check = digits.pop();
  let sum = 0;
  for (let index = digits.length - 1, position = 0; index >= 0; index -= 1, position += 1) {
    sum += digits[index] * (position % 2 === 0 ? 3 : 1);
  }
  return (10 - (sum % 10)) % 10 === check;
}

async function sha256(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(await fs.readFile(filePath));
  return hash.digest("hex").toUpperCase();
}

async function analyzeImportCsv(filePath, marketRegion) {
  const text = await fs.readFile(filePath, "utf8");
  const lines = text.split(/\r?\n/).filter((line) => line.length > 0);
  const headers = csvLine(lines[0]).map((header) => header.trim());
  const index = Object.fromEntries(headers.map((header, position) => [header, position]));
  const value = (row, name) => row[index[name]] ?? "";
  const barcodes = new Map();
  let completeCoreNutrition = 0;
  let validGtinRows = 0;
  let invalidGtinRows = 0;
  let imageRows = 0;
  let servingRows = 0;
  let missingBrandRows = 0;
  let missingNameRows = 0;
  let marketMismatchRows = 0;
  for (const line of lines.slice(1)) {
    const row = csvLine(line);
    const barcode = value(row, "barcode").trim();
    if (barcode) barcodes.set(barcode, (barcodes.get(barcode) ?? 0) + 1);
    if (validGtin(barcode)) validGtinRows += 1;
    else invalidGtinRows += 1;
    if (plausibleCore(value(row, "calories"), value(row, "protein"), value(row, "fat"), value(row, "carbs"))) completeCoreNutrition += 1;
    if (["display_image_url", "external_image_url", "image_url"].some((name) => value(row, name).trim())) imageRows += 1;
    if (value(row, "serving_size_grams").trim()) servingRows += 1;
    if (!value(row, "brand").trim()) missingBrandRows += 1;
    if (!value(row, "name").trim()) missingNameRows += 1;
    if (value(row, "market_region").trim() !== marketRegion) marketMismatchRows += 1;
  }
  return {
    path: path.relative(process.cwd(), filePath),
    sha256: await sha256(filePath),
    rows: Math.max(0, lines.length - 1),
    uniqueBarcodes: barcodes.size,
    duplicateBarcodeGroups: [...barcodes.values()].filter((count) => count > 1).length,
    completePlausibleCoreNutritionRows: completeCoreNutrition,
    validGtinRows,
    invalidGtinRows,
    imageRows,
    servingRows,
    missingBrandRows,
    missingNameRows,
    marketMismatchRows,
  };
}

function productNutrition(product) {
  const nutrition = product.nutrition ?? {};
  return {
    calories: nutrition.energyKcal ?? nutrition.calories ?? null,
    protein: nutrition.protein ?? null,
    fat: nutrition.fat ?? null,
    carbs: nutrition.carbohydrate ?? nutrition.carbs ?? null,
  };
}

function sourceUrlCount(product) {
  return Object.values(product.sourceUrls ?? {}).filter(Boolean).length;
}

async function analyzeRetailCatalog(catalogPath, excludedPath, policyQueuePath) {
  const catalog = JSON.parse(await fs.readFile(catalogPath, "utf8"));
  const excluded = JSON.parse(await fs.readFile(excludedPath, "utf8"));
  const policyQueue = JSON.parse(await fs.readFile(policyQueuePath, "utf8"));
  const excludedIds = new Set((excluded.products ?? []).map((product) => product.catalogId));
  const primaryBarcodes = new Map();
  const metrics = {
    rows: 0,
    excludedRows: 0,
    multipackRows: 0,
    validPrimaryGtinRows: 0,
    completePlausibleCoreNutritionRows: 0,
    validGtinAndCompleteNutritionRows: 0,
    validGtinCompleteNutritionAndImageRows: 0,
    validGtinCompleteNutritionImagePackageAndSourceRows: 0,
    brandRows: 0,
    imageRows: 0,
    packageRows: 0,
    sourceUrlRows: 0,
  };

  for (const product of catalog.products ?? []) {
    metrics.rows += 1;
    if (excludedIds.has(product.catalogId)) metrics.excludedRows += 1;
    if (product.isMultipack) metrics.multipackRows += 1;
    const barcode = String(product.primaryBarcode ?? product.barcode ?? "").trim();
    const hasGtin = validGtin(barcode);
    const hasCore = plausibleCore(...Object.values(productNutrition(product)));
    const hasImage = Boolean(product.imageSourceUrl || (product.imageSourceUrls ?? []).some(Boolean));
    const hasPackage = finite(product.packageAmount) !== null && Boolean(product.packageUnit);
    const hasSourceUrl = sourceUrlCount(product) > 0;
    if (barcode) primaryBarcodes.set(barcode, (primaryBarcodes.get(barcode) ?? 0) + 1);
    if (hasGtin) metrics.validPrimaryGtinRows += 1;
    if (hasCore) metrics.completePlausibleCoreNutritionRows += 1;
    if (hasGtin && hasCore) metrics.validGtinAndCompleteNutritionRows += 1;
    if (hasGtin && hasCore && hasImage) metrics.validGtinCompleteNutritionAndImageRows += 1;
    if (hasGtin && hasCore && hasImage && hasPackage && hasSourceUrl) metrics.validGtinCompleteNutritionImagePackageAndSourceRows += 1;
    if (String(product.brand ?? "").trim()) metrics.brandRows += 1;
    if (hasImage) metrics.imageRows += 1;
    if (hasPackage) metrics.packageRows += 1;
    if (hasSourceUrl) metrics.sourceUrlRows += 1;
  }

  return {
    path: path.relative(process.cwd(), catalogPath),
    sha256: await sha256(catalogPath),
    catalogSummary: catalog.summary,
    calculated: {
      ...metrics,
      uniquePrimaryBarcodes: primaryBarcodes.size,
      duplicatePrimaryBarcodeGroups: [...primaryBarcodes.values()].filter((count) => count > 1).length,
    },
    activePolicySummary: {
      catalogProducts: policyQueue.catalogProducts,
      activeCatalogProducts: policyQueue.activeCatalogProducts,
      inactiveMultipackProducts: policyQueue.inactiveMultipackProducts,
      fullyExcludedProducts: policyQueue.fullyExcludedProducts,
      barcodeOptionalProducts: policyQueue.barcodeOptionalProducts,
      barcodeOptionalWithoutBarcode: policyQueue.barcodeOptionalWithoutBarcode,
      barcodeRequiredProducts: policyQueue.barcodeRequiredProducts,
      barcodeRequiredWithBarcode: policyQueue.barcodeRequiredWithBarcode,
      activeProductsWithBarcode: policyQueue.productsWithBarcode,
      remainingRequiredBarcodes: policyQueue.remaining,
    },
    releaseClassification: "TEST_ONLY_PENDING_SOURCE_RIGHTS",
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const report = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    definitions: {
      collectedCandidate: "Source records after deterministic identity merging; not necessarily importable or licensed.",
      strictImportRow: "Unique barcode, name, brand, complete plausible calories/protein/fat/carbs and matching market.",
      retailerTestOnly: "May be used only in a private test build until commercial-use and persistent-storage rights are documented.",
    },
    ukIeStrict: await analyzeImportCsv(args.ukImport, "UK_IE"),
    trOffStrict: await analyzeImportCsv(args.trOffImport, "TR"),
    trRetailCandidates: await analyzeRetailCatalog(args.trRetailCatalog, args.trExcluded, args.trPolicyQueue),
    databaseObservation: {
      status: "NOT_CHECKED_BY_SCRIPT",
      note: "This audit is file-based and never starts Docker or mutates a database.",
    },
  };
  await fs.mkdir(path.dirname(args.output), { recursive: true });
  await fs.writeFile(args.output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

await main();
