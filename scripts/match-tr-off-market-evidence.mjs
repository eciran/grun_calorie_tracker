import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";

const DEFAULTS = {
  queue: "outputs/product-data-readiness/s9-tr-internet/tr-market-evidence-queue.tsv",
  catalog: "outputs/TR_Products/merged-a101-migros-iyas-deduplicated/products.json",
  supplementalSources: "outputs/TR_Products/sources",
  outputDir: "outputs/product-catalog-aug08/tr-off-market-evidence-match-20260802",
};

const AUTHORIZED_EVIDENCE_DIRECTORIES = new Set([
  "aykilic", "bakkalabla", "cagri", "gurmar", "marketkapinda",
  "ozdilekteyim", "sariyer", "toptantr", "trendyol",
]);

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

function parseTsv(text) {
  const lines = text.replace(/^\uFEFF/, "").split(/\r?\n/).filter(Boolean);
  const headers = lines.shift().split("\t");
  return lines.map((line) => Object.fromEntries(line.split("\t").map((value, index) => [headers[index], value])));
}

function productGtins(product) {
  return [...new Set([
    product.primaryBarcode,
    product.barcode,
    ...(product.barcodes ?? []),
  ].map((value) => String(value ?? "").trim()).filter(Boolean))];
}

function urlsFromCatalog(product) {
  const urls = [];
  for (const [provider, url] of Object.entries(product.sourceUrls ?? {})) {
    if (url) urls.push({ provider: provider.toUpperCase(), url, evidenceType: "TR_RETAILER_PRODUCT_PAGE" });
  }
  return urls;
}

async function sha256(filePath) {
  return crypto.createHash("sha256").update(await fs.readFile(filePath)).digest("hex").toUpperCase();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const queueRows = parseTsv(await fs.readFile(args.queue, "utf8"));
  const catalog = JSON.parse(await fs.readFile(args.catalog, "utf8"));
  const evidenceByGtin = new Map();
  const addEvidence = (gtin, evidence) => {
    const list = evidenceByGtin.get(gtin) ?? [];
    const key = `${evidence.provider}|${evidence.url}`;
    if (!list.some((item) => `${item.provider}|${item.url}` === key)) list.push(evidence);
    evidenceByGtin.set(gtin, list);
  };

  for (const product of catalog.products ?? []) {
    const evidence = urlsFromCatalog(product);
    if (!evidence.length) continue;
    for (const gtin of productGtins(product)) {
      for (const item of evidence) addEvidence(gtin, item);
    }
  }
  const directGtins = new Set(evidenceByGtin.keys());

  for (const entry of await fs.readdir(args.supplementalSources, { withFileTypes: true })) {
    if (!entry.isDirectory() || !AUTHORIZED_EVIDENCE_DIRECTORIES.has(entry.name)) continue;
    const filePath = path.join(args.supplementalSources, entry.name, "products.json");
    const source = JSON.parse(await fs.readFile(filePath, "utf8"));
    for (const product of source.products ?? []) {
      const url = String(product.sourceUrl ?? "").trim();
      if (!url) continue;
      for (const gtin of productGtins(product)) {
        addEvidence(gtin, {
          provider: String(product.source ?? entry.name).toUpperCase(),
          url,
          evidenceType: "TR_RETAILER_OR_DISTRIBUTOR_PRODUCT_PAGE",
        });
      }
    }
  }

  const matches = [];
  let directCatalogMatches = 0;
  let supplementalMatches = 0;
  for (const row of queueRows) {
    const evidence = evidenceByGtin.get(row.barcode) ?? [];
    if (!evidence.length) continue;
    const hasDirect = directGtins.has(row.barcode);
    if (hasDirect) directCatalogMatches += 1;
    if (evidence.some((item) => !["A101", "MIGROS", "IYAS"].includes(item.provider))) supplementalMatches += 1;
    matches.push({
      barcode: row.barcode,
      name: row.name,
      brand: row.brand,
      offMarketScore: Number(row.offMarketScore),
      offSignals: row.offSignals,
      status: "PENDING_AUTHORIZED_MARKET_EVIDENCE_REVIEW",
      exactGtinMatch: true,
      evidence,
    });
  }
  matches.sort((left, right) => left.barcode.localeCompare(right.barcode));

  await fs.mkdir(args.outputDir, { recursive: true });
  const candidatePath = path.join(args.outputDir, "tr-market-evidence-candidates.json");
  const reportPath = path.join(args.outputDir, "report.json");
  const candidateDocument = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    classification: "EVIDENCE_REFERENCES_NOT_AUTO_PROMOTED",
    matchingPolicy: "Exact GTIN only; URLs require page-specific rights and reviewer confirmation.",
    products: matches,
  };
  await fs.writeFile(candidatePath, `${JSON.stringify(candidateDocument, null, 2)}\n`, "utf8");
  const report = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    status: "PASS",
    queueRows: queueRows.length,
    exactGtinEvidenceCandidates: matches.length,
    currentA101MigrosIyasMatches: directCatalogMatches,
    matchesWithSupplementalRetailerEvidence: supplementalMatches,
    remainingExternalEvidenceRequired: queueRows.length - matches.length,
    currentStrict: 1595,
    projectedStrictAfterAllCandidateReviews: 1595 + matches.length,
    artifacts: {
      candidates: candidatePath,
      candidateSha256: await sha256(candidatePath),
    },
  };
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

await main();
