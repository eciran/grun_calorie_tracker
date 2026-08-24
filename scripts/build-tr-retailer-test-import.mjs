import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";

const COLUMNS = [
  "catalog_type", "data_source", "nutrition_basis", "barcode", "source_key", "name", "brand",
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "serving_size_grams",
  "serving_unit", "market_region", "market_regions", "display_name_tr", "short_display_name_tr",
  "aliases_tr", "image_url", "external_image_url", "display_image_url", "allergens", "nutri_score",
  "source_catalog_id", "source_providers", "source_urls_json", "source_salt_per_100g", "serving_options_json",
];

const DEFAULTS = {
  input: "outputs/TR_Products/merged-a101-migros-iyas-deduplicated/products.json",
  excluded: "outputs/TR_Products/barcode-enrichment/manual-excluded-products.json",
  outputDir: "outputs/product-catalog-aug08/tr-retailer-test-v1",
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
  args.input = path.resolve(args.root, args.input);
  args.excluded = path.resolve(args.root, args.excluded);
  args.outputDir = path.resolve(args.root, args.outputDir);
  return args;
}

function finite(value) {
  if (value === null || value === undefined || String(value).trim() === "") return null;
  const parsed = Number(String(value).replace(",", "."));
  return Number.isFinite(parsed) ? parsed : null;
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

function csv(value) {
  if (value === null || value === undefined) return "";
  const text = String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

function nutrition(product) {
  const values = product.nutrition ?? {};
  return {
    calories: finite(values.energyKcal ?? values.calories),
    protein: finite(values.protein),
    fat: finite(values.fat),
    carbs: finite(values.carbohydrate ?? values.carbs),
    fiber: finite(values.fiber ?? values["Lif (g)"]),
    sugar: finite(values.sugar ?? values.glucose),
    salt: finite(values.salt),
  };
}

function plausibleCore(values) {
  const { calories, protein, fat, carbs } = values;
  return [calories, protein, fat, carbs].every((value) => value !== null)
    && calories >= 0 && calories <= 1000
    && [protein, fat, carbs].every((value) => value >= 0 && value <= 100)
    && protein + fat + carbs <= 110;
}

function sourceUrls(product) {
  return Object.fromEntries(Object.entries(product.sourceUrls ?? {}).filter(([, value]) => Boolean(value)));
}

function aliases(product) {
  const values = new Set();
  for (const alias of product.productAliases ?? []) {
    const name = String(alias.name ?? "").trim();
    if (name && name.toLocaleLowerCase("tr-TR") !== String(product.name ?? "").trim().toLocaleLowerCase("tr-TR")) values.add(name);
  }
  return [...values].slice(0, 20).join(";");
}

function packageServing(product) {
  const amount = finite(product.packageAmount);
  const unit = String(product.packageUnit ?? "").toUpperCase();
  if (amount === null || amount <= 0 || amount > 10000 || !["G", "ML"].includes(unit)) return { size: null, unit: null, options: "" };
  const option = {
    label: "1 package",
    unitType: "SERVING",
    quantity: 1,
    gramWeight: unit === "G" ? amount : null,
    mlVolume: unit === "ML" ? amount : null,
    defaultOption: true,
    labels: { EN: "1 package", TR: "1 paket" },
  };
  return { size: amount, unit, options: JSON.stringify([option]) };
}

function score(product) {
  const values = nutrition(product);
  return (plausibleCore(values) ? 100 : 0)
    + (product.imageSourceUrl ? 20 : 0)
    + (finite(product.packageAmount) !== null ? 10 : 0)
    + Object.keys(sourceUrls(product)).length * 5
    + (product.isMultipack ? -15 : 0);
}

function rowFor(product) {
  const values = nutrition(product);
  const serving = packageServing(product);
  const urls = sourceUrls(product);
  const image = product.imageSourceUrl ?? (product.imageSourceUrls ?? []).find(Boolean) ?? "";
  const sourceProviders = Object.keys(urls).map((name) => name.toUpperCase()).join("|") || String(product.source ?? "");
  const row = {
    catalog_type: "BRANDED_PRODUCT",
    data_source: "ADMIN_IMPORT",
    nutrition_basis: "SOURCE_REPORTED",
    barcode: String(product.primaryBarcode ?? product.barcode ?? "").trim(),
    source_key: `barcode:${String(product.primaryBarcode ?? product.barcode ?? "").trim()}`,
    name: product.name ?? "",
    brand: product.brand ?? "",
    calories: values.calories,
    protein: values.protein,
    fat: values.fat,
    carbs: values.carbs,
    fiber: values.fiber,
    sugar: values.sugar,
    sodium: null,
    serving_size_grams: serving.size,
    serving_unit: serving.unit,
    market_region: "TR",
    market_regions: "TR",
    display_name_tr: product.name ?? "",
    short_display_name_tr: product.name ?? "",
    aliases_tr: aliases(product),
    image_url: image,
    external_image_url: image,
    display_image_url: "",
    allergens: "",
    nutri_score: "unknown",
    source_catalog_id: product.catalogId ?? "",
    source_providers: sourceProviders,
    source_urls_json: JSON.stringify(urls),
    source_salt_per_100g: values.salt,
    serving_options_json: serving.options,
  };
  return COLUMNS.map((column) => csv(row[column])).join(",");
}

async function hash(filePath) {
  const digest = crypto.createHash("sha256").update(await fs.readFile(filePath)).digest("hex").toUpperCase();
  return digest;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const catalog = JSON.parse(await fs.readFile(args.input, "utf8"));
  const excluded = JSON.parse(await fs.readFile(args.excluded, "utf8"));
  const excludedIds = new Set((excluded.products ?? []).map((product) => product.catalogId));
  const candidates = (catalog.products ?? []).filter((product) => {
    const barcode = product.primaryBarcode ?? product.barcode;
    return !excludedIds.has(product.catalogId)
      && !product.isMultipack
      && validGtin(barcode)
      && String(product.name ?? "").trim()
      && String(product.brand ?? "").trim();
  });

  const selectedByBarcode = new Map();
  for (const product of candidates) {
    const barcode = String(product.primaryBarcode ?? product.barcode).trim();
    const existing = selectedByBarcode.get(barcode);
    if (!existing || score(product) > score(existing)) selectedByBarcode.set(barcode, product);
  }
  const maximum = [...selectedByBarcode.values()].sort((left, right) => String(left.primaryBarcode).localeCompare(String(right.primaryBarcode)));
  const rich = maximum.filter((product) => plausibleCore(nutrition(product)));

  await fs.mkdir(args.outputDir, { recursive: true });
  const header = COLUMNS.map(csv).join(",");
  const maxPath = path.join(args.outputDir, "tr-retailer-test-max-import.csv");
  const richPath = path.join(args.outputDir, "tr-retailer-test-rich-import.csv");
  await fs.writeFile(maxPath, `${header}\n${maximum.map(rowFor).join("\n")}\n`, "utf8");
  await fs.writeFile(richPath, `${header}\n${rich.map(rowFor).join("\n")}\n`, "utf8");

  const manifest = {
    schemaVersion: 1,
    manifestId: "tr-retailer-test-v1",
    generatedAt: new Date().toISOString(),
    marketRegion: "TR",
    releaseClassification: "PRIVATE_TEST_ONLY_BLOCKED_FOR_PRODUCTION_SOURCE_RIGHTS",
    requiredImportMode: "RAW_EXTERNAL",
    requiredImportFormat: "GRUN_STANDARD",
    source: {
      catalog: path.relative(process.cwd(), args.input),
      catalogSha256: await hash(args.input),
      excludedCatalogIds: path.relative(process.cwd(), args.excluded),
      excludedSha256: await hash(args.excluded),
    },
    selectionPolicy: {
      common: ["valid GTIN-8/12/13/14 check digit", "name", "brand", "manual exclusions removed", "multipacks removed", "one best row per primary GTIN"],
      rich: ["complete plausible per-100 calories/protein/fat/carbs"],
      maximum: ["nutrition gaps retained for admin/review testing"],
      serving: "Package amount is emitted as a NEEDS_REVIEW package serving option; it is not asserted as a dietary portion.",
      sodium: "Source salt is preserved in source_salt_per_100g; sodium is intentionally not inferred.",
    },
    artifacts: [
      { role: "TEST_DEFAULT_RICH", file: path.basename(richPath), rows: rich.length, sha256: await hash(richPath) },
      { role: "TEST_MAX_REVIEW", file: path.basename(maxPath), rows: maximum.length, sha256: await hash(maxPath) },
    ],
    blockers: [
      "Retailer/manufacturer page commercial-use and persistent-storage rights are not recorded.",
      "ADMIN_IMPORT is a transport-compatible placeholder; production provenance needs an explicit retailer/manufacturer provider model.",
      "No production database import is authorized by this manifest.",
    ],
  };
  const manifestPath = path.join(args.outputDir, "manifest.json");
  await fs.writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  process.stdout.write(`${JSON.stringify(manifest, null, 2)}\n`);
}

await main();
