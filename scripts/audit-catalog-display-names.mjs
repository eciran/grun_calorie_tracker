import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const baseUrl = (process.env.GRUN_API_BASE_URL ?? "https://api-staging.gruncalorietracker.com").replace(/\/$/, "");
const token = process.env.GRUN_ADMIN_TOKEN;
const concurrency = Math.max(1, Math.min(Number(process.env.GRUN_AUDIT_CONCURRENCY ?? 12), 24));
const pageSize = 100;

if (!token) {
  throw new Error("GRUN_ADMIN_TOKEN is required");
}

const headers = { Authorization: `Bearer ${token}`, Accept: "application/json" };

async function getJson(relativeUrl, attempts = 4) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}${relativeUrl}`, { headers });
      if (response.ok) return await response.json();
      const body = await response.text();
      if (response.status !== 429 && response.status < 500) {
        throw new Error(`${response.status} ${relativeUrl}: ${body.slice(0, 300)}`);
      }
      lastError = new Error(`${response.status} ${relativeUrl}`);
    } catch (error) {
      lastError = error;
    }
    await new Promise(resolve => setTimeout(resolve, 300 * 2 ** (attempt - 1)));
  }
  throw lastError;
}

function normalized(value) {
  return String(value ?? "").trim().replace(/\s+/g, " ");
}

function count(map, key) {
  const safeKey = key || "UNKNOWN";
  map[safeKey] = (map[safeKey] ?? 0) + 1;
}

function baseIssues(product) {
  const sourceName = normalized(product.sourceName);
  const displayName = normalized(product.displayName);
  const shortName = normalized(product.shortDisplayName);
  const productName = normalized(product.productName);
  const issues = [];

  if (!displayName) issues.push("MISSING_DISPLAY_NAME");
  if (!shortName) issues.push("MISSING_SHORT_DISPLAY_NAME");
  const sourceBackedGeneric = product.catalogType === "GENERIC_INGREDIENT" || product.dataSource === "USDA_FOODDATA";
  if (sourceBackedGeneric && sourceName && displayName.toLocaleLowerCase("en") === sourceName.toLocaleLowerCase("en") && sourceName.includes(",")) {
    issues.push("RAW_SOURCE_NAME_EXPOSED");
  }
  if ((productName.match(/,/g) ?? []).length >= 2) issues.push("TECHNICAL_COMMA_CHAIN");
  if (/\s+-\s+.*\s+-\s+/u.test(productName)) issues.push("HYPHEN_DESCRIPTOR_CHAIN");
  if (/\b(nfs|ns as to|wo\/|w\/|incl\.|excluding|solids and liquids)\b/i.test(productName)) {
    issues.push("TECHNICAL_SOURCE_TOKEN");
  }
  if (/,[\s]*$/u.test(productName)) issues.push("TRAILING_COMMA");
  const openingParentheses = (productName.match(/\(/g) ?? []).length;
  const closingParentheses = (productName.match(/\)/g) ?? []).length;
  if (openingParentheses !== closingParentheses) issues.push("UNBALANCED_PARENTHESES");
  if (/\b(energy|nutrition|ingredients?)\b/i.test(productName) && (productName.match(/\d/g) ?? []).length >= 5) {
    issues.push("OCR_OR_LABEL_TEXT_AS_NAME");
  }
  return [...new Set(issues)];
}

function localizationIssues(product, language) {
  const issues = [];
  const displayName = normalized(product.displayName);
  const shortName = normalized(product.shortDisplayName);
  const sourceName = normalized(product.sourceName);
  if (product.language !== language) issues.push(`MISSING_${language}_LOCALIZATION`);
  if (!displayName || !shortName) issues.push(`INCOMPLETE_${language}_DISPLAY_NAME`);
  if (sourceName && displayName.toLocaleLowerCase("en") === sourceName.toLocaleLowerCase("en") && sourceName.includes(",")) {
    issues.push(`${language}_RAW_SOURCE_NAME_EXPOSED`);
  }
  if (language === "TR" && /\b(raw|fresh|cooked|boiled|fried|baked|roasted|grilled|yellow|white|whole)\b/i.test(`${displayName} ${shortName}`)) {
    issues.push("ENGLISH_TOKEN_IN_TR_NAME");
  }
  return [...new Set(issues)];
}

async function mapConcurrent(items, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  async function run() {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await worker(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, run));
  return results;
}

const firstPage = await getJson(`/api/v1/admin/products/review?page=0&size=${pageSize}`);
const pages = Array.from({ length: firstPage.totalPages }, (_, index) => index);
const summary = {
  scannedAt: new Date().toISOString(),
  baseUrl,
  totalElements: firstPage.totalElements,
  totalPages: firstPage.totalPages,
  byCatalogType: {},
  byDataSource: {},
  baseIssueCounts: {},
  localizationIssueCounts: {},
};
const suspicious = [];
const localizationCandidates = [];

await mapConcurrent(pages, async page => {
  const payload = page === 0 ? firstPage : await getJson(`/api/v1/admin/products/review?page=${page}&size=${pageSize}`);
  for (const product of payload.content ?? []) {
    count(summary.byCatalogType, product.catalogType);
    count(summary.byDataSource, product.dataSource);
    const issues = baseIssues(product);
    for (const issue of issues) count(summary.baseIssueCounts, issue);
    if (issues.length) suspicious.push({ ...product, issues });
    if (["GENERIC_INGREDIENT", "LOCAL_DISH"].includes(product.catalogType)) {
      localizationCandidates.push(product);
    }
  }
  if (page > 0 && page % 100 === 0) process.stderr.write(`scanned ${page}/${firstPage.totalPages} pages\n`);
});

const localizedSuspicious = [];
await mapConcurrent(localizationCandidates, async product => {
  for (const language of ["TR", "EN"]) {
    const localized = await getJson(`/api/v1/products/${product.id}?language=${language}`);
    const issues = localizationIssues(localized, language);
    for (const issue of issues) count(summary.localizationIssueCounts, issue);
    if (issues.length) {
      localizedSuspicious.push({
        id: product.id,
        catalogType: product.catalogType,
        dataSource: product.dataSource,
        language,
        sourceName: localized.sourceName,
        displayName: localized.displayName,
        shortDisplayName: localized.shortDisplayName,
        productName: localized.productName,
        issues,
      });
    }
  }
});

summary.baseSuspiciousProducts = suspicious.length;
summary.localizationCandidates = localizationCandidates.length;
summary.localizedSuspiciousRows = localizedSuspicious.length;

const outputDir = path.resolve("outputs", "catalog-display-name-audit");
await mkdir(outputDir, { recursive: true });
await writeFile(path.join(outputDir, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`, "utf8");
await writeFile(path.join(outputDir, "base-suspicious.json"), `${JSON.stringify(suspicious, null, 2)}\n`, "utf8");
await writeFile(path.join(outputDir, "localization-suspicious.json"), `${JSON.stringify(localizedSuspicious, null, 2)}\n`, "utf8");

console.log(JSON.stringify(summary, null, 2));
