import fs from "node:fs/promises";
import path from "node:path";

const DEFAULTS = {
  catalog: "outputs/TR_Products/merged-a101-migros-iyas-deduplicated/products.json",
  excluded: "outputs/TR_Products/barcode-enrichment/manual-excluded-products.json",
  barcodeQueue: "outputs/TR_Products/barcode-enrichment/primary-catalog-barcode-queue.json",
  nutritionQueue: "outputs/TR_Products/nutrition-enrichment/queue.json",
  offCandidates: "outputs/product-data-readiness/s9-tr-internet/tr-off-candidates.tsv",
  discoveryManifest: "sample-data/manifests/open-food-facts-tr-internet-discovery-v1.json",
  supplementalSources: "outputs/TR_Products/sources",
  output: "outputs/product-catalog-aug08/tr-capacity-20260802/tr-capacity-report.json",
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

function nutrition(product) {
  const values = product.nutrition ?? {};
  return {
    calories: finite(values.energyKcal ?? values.calories),
    protein: finite(values.protein),
    fat: finite(values.fat),
    carbs: finite(values.carbohydrate ?? values.carbs),
  };
}

function plausibleCore(product) {
  const { calories, protein, fat, carbs } = nutrition(product);
  return [calories, protein, fat, carbs].every((value) => value !== null)
    && calories >= 0 && calories <= 1000
    && [protein, fat, carbs].every((value) => value >= 0 && value <= 100)
    && protein + fat + carbs <= 110;
}

function usableText(value) {
  return String(value ?? "").trim().length > 0;
}

function gtins(product) {
  const values = new Set([
    product.primaryBarcode,
    product.barcode,
    ...(product.barcodes ?? []),
  ].map((value) => String(value ?? "").trim()).filter(validGtin));
  return [...values];
}

function groupByCategory(products, catalogById) {
  const groups = new Map();
  for (const queued of products) {
    const product = catalogById.get(queued.catalogId) ?? queued;
    const category = String(product.mainCategory ?? "UNKNOWN").trim() || "UNKNOWN";
    groups.set(category, (groups.get(category) ?? 0) + 1);
  }
  return [...groups.entries()]
    .map(([category, rows]) => ({ category, rows }))
    .sort((left, right) => right.rows - left.rows || left.category.localeCompare(right.category));
}

function parseTsv(text) {
  const lines = text.replace(/^\uFEFF/, "").split(/\r?\n/).filter(Boolean);
  const headers = lines.shift().split("\t");
  return lines.map((line) => Object.fromEntries(line.split("\t").map((value, index) => [headers[index], value])));
}

function validPrimary(product) {
  return validGtin(product.primaryBarcode ?? product.barcode);
}

async function readJson(filePath) {
  return JSON.parse(await fs.readFile(filePath, "utf8"));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [catalog, excluded, barcodeQueue, nutritionQueue, offText, discovery] = await Promise.all([
    readJson(args.catalog),
    readJson(args.excluded),
    readJson(args.barcodeQueue),
    readJson(args.nutritionQueue),
    fs.readFile(args.offCandidates, "utf8"),
    readJson(args.discoveryManifest),
  ]);

  const products = catalog.products ?? [];
  const excludedIds = new Set((excluded.products ?? []).map((product) => product.catalogId));
  const catalogById = new Map(products.map((product) => [product.catalogId, product]));
  const nonExcludedSingles = products.filter((product) => !excludedIds.has(product.catalogId) && !product.isMultipack);
  const namedBrandedSingles = nonExcludedSingles.filter((product) => usableText(product.name) && usableText(product.brand));
  const identityReady = namedBrandedSingles.filter(validPrimary);
  const nutritionReady = namedBrandedSingles.filter(plausibleCore);
  const bothReady = namedBrandedSingles.filter((product) => validPrimary(product) && plausibleCore(product));
  const eitherReadyIds = new Set([...identityReady, ...nutritionReady].map((product) => product.catalogId));

  const queuedForBarcode = barcodeQueue.products ?? [];
  const barcodeQueueNutritionReady = queuedForBarcode.filter((queued) => {
    const product = catalogById.get(queued.catalogId);
    return product && usableText(product.name) && usableText(product.brand) && plausibleCore(product)
      && !excludedIds.has(product.catalogId) && !product.isMultipack;
  });

  const offRows = parseTsv(offText);
  const offValid = offRows.filter((row) => !String(row.issues ?? "").includes("INVALID_GTIN"));
  const offStrict = offRows.filter((row) => row.tier === "STRICT");
  const offQualityReady = offValid.filter((row) => !String(row.issues ?? "").trim());
  const hasStrongMarket = (row) => /OFF_COUNTRY_TR|TR_RETAILER_TAG/.test(row.signals ?? "");
  const offStrongMarketValid = offValid.filter(hasStrongMarket);
  const offStrongNeedsFieldRepair = offStrongMarketValid.filter((row) => row.tier !== "STRICT");
  const offQualityWeakMarket = offQualityReady.filter((row) => !hasStrongMarket(row));
  const offRecoverable = offValid.filter((row) => hasStrongMarket(row)
    || (!String(row.issues ?? "").trim() && String(row.signals ?? "").includes("GS1_TR_PREFIX")));
  const registryPlannedPotential = Number(discovery.capacity?.strict ?? 0)
    + Number(discovery.capacity?.qualityPassGtinPrefixWithoutCountryEvidence ?? 0);

  const retailerGtins = new Set(products.flatMap(gtins));
  const offGtins = new Set(offValid.map((row) => row.barcode).filter(validGtin));
  const knownGtins = new Set([...retailerGtins, ...offGtins]);
  const sourceRows = [];
  const supplementalUnion = new Set();
  for (const entry of await fs.readdir(args.supplementalSources, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const sourcePath = path.join(args.supplementalSources, entry.name, "products.json");
    try {
      const source = await readJson(sourcePath);
      const candidates = Array.isArray(source) ? source : (source.products ?? source.items ?? []);
      const unique = new Set(candidates.flatMap(gtins));
      for (const gtin of unique) supplementalUnion.add(gtin);
      const netNew = [...unique].filter((gtin) => !knownGtins.has(gtin));
      sourceRows.push({
        source: entry.name,
        rows: candidates.length,
        validUniqueGtins: unique.size,
        overlapRetailerCatalog: [...unique].filter((gtin) => retailerGtins.has(gtin)).length,
        overlapOffCandidates: [...unique].filter((gtin) => offGtins.has(gtin)).length,
        netNewIdentityCandidates: netNew.length,
        rowsWithBrand: candidates.filter((product) => usableText(product.brand)).length,
      });
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
    }
  }
  sourceRows.sort((left, right) => right.netNewIdentityCandidates - left.netNewIdentityCandidates);

  const supplementalNetNew = [...supplementalUnion].filter((gtin) => !knownGtins.has(gtin));
  const report = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    marketRegion: "TR",
    classifications: {
      off: "PRODUCTION_ALLOWED_SUBJECT_TO_ODBL_COMPLIANCE",
      retailerAndSupplementalPages: "EVIDENCE_ONLY_OR_PRIVATE_TEST_UNTIL_RIGHTS_ARE_RECORDED",
    },
    currentRetailerCatalog: {
      collectedRows: products.length,
      activeCatalogRowsFromQueuePolicy: barcodeQueue.activeCatalogProducts,
      manuallyOrPolicyExcludedRows: excludedIds.size,
      multipackRows: products.filter((product) => product.isMultipack).length,
      nonExcludedSingleRows: nonExcludedSingles.length,
      namedBrandedSingleRows: namedBrandedSingles.length,
      identityReadyRows: identityReady.length,
      nutritionReadyRows: nutritionReady.length,
      identityAndNutritionReadyRows: bothReady.length,
      identityOnlyRows: identityReady.length - bothReady.length,
      nutritionOnlyRows: nutritionReady.length - bothReady.length,
      identityOrNutritionReadyRows: eitherReadyIds.size,
      needsBothAfterNameBrandPolicy: namedBrandedSingles.length - eitherReadyIds.size,
      currentPrivateTestRichRows: 1605,
      currentPrivateTestIdentityCeilingRows: 5903,
    },
    repairQueues: {
      missingRequiredBarcodeRows: barcodeQueue.remaining,
      missingRequiredBarcodeBySource: barcodeQueue.remainingBySource,
      barcodeQueueAlreadyNutritionReadyRows: barcodeQueueNutritionReady.length,
      barcodeQueueByCategory: groupByCategory(queuedForBarcode, catalogById),
      barcodePresentNutritionMissingRows: nutritionQueue.summary?.pendingWithBarcodeWithoutNutrition
        ?? (nutritionQueue.products ?? []).length,
      nutritionQueueByCategory: groupByCategory(nutritionQueue.products ?? [], catalogById),
    },
    openFoodFacts: {
      candidates: offRows.length,
      validGtinCandidates: offValid.length,
      strictNow: offStrict.length,
      qualityReadyAllMarketTiers: offQualityReady.length,
      qualityReadyButWeakMarketEvidence: offQualityWeakMarket.length,
      strongMarketValidGtinCandidates: offStrongMarketValid.length,
      strongMarketCandidatesNeedingFieldRepair: offStrongNeedsFieldRepair.length,
      recoverableWithFieldAndOrAuthorizedMarketEvidence: offRecoverable.length,
      registryPlannedPotentialAfterAllEvidence: registryPlannedPotential,
      remainingValidLowEvidenceAndQualityGap: offValid.length - offRecoverable.length,
    },
    supplementalIdentityEvidence: {
      sources: sourceRows,
      validUniqueGtinsAcrossSources: supplementalUnion.size,
      netNewVersusRetailerAndOffCandidateUniverses: supplementalNetNew.length,
      limitation: "These sources do not provide a production-authorized complete nutrition record by default.",
    },
    planningCeilings: {
      rightsClearedStrictToday: offStrict.length,
      offQualityFirstTarget: registryPlannedPotential,
      offFieldEnrichmentTarget: offStrongMarketValid.length,
      offCombinedEvidenceAndFieldRepairUpperBound: offRecoverable.length,
      privateRetailerImmediateIdentityReviewTarget: 5903,
      privateRetailerAfterExistingQueueRepairTarget: barcodeQueue.activeCatalogProducts,
      supplementalNetNewIdentityResearchBacklog: supplementalNetNew.length,
    },
    interpretation: [
      "Rights-cleared growth should prioritize OFF records and authorized manufacturer or label evidence.",
      "Retailer and supplemental page data can drive private-test and evidence queues but not production import without recorded rights.",
      "The private retailer upper bound assumes every active record can obtain a valid GTIN, complete plausible nutrition, name and brand; it is not a guaranteed yield.",
      "GTIN prefix 868/869 does not prove Turkish market availability by itself.",
    ],
  };

  await fs.mkdir(path.dirname(args.output), { recursive: true });
  await fs.writeFile(args.output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

await main();
