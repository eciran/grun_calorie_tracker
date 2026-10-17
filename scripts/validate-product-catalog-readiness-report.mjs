import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const REQUIRED = [
  'identity', 'inventory', 'catalogTypes', 'gtin', 'identityGroups', 'servings',
  'localization', 'categoryCoverage', 'qualitySuggestions', 'qualityRuns',
  'publishedCoreFailureSamples', 'servingsByType', 'volumeEvidence',
  'canonicalCategoryCoverage', 'micronutrients', 'brandedCollapse',
  'misclassificationCandidates', 'auditSelfTest',
];

// This checks report integrity, not the truth of nutrients or readiness to publish.
export function validateReport(records) {
  assert(Array.isArray(records), 'Expected an array of audit sections');
  const sections = new Map();
  for (const record of records) {
    assert(record && typeof record.section === 'string', 'Invalid section');
    assert(!sections.has(record.section), `Duplicate section: ${record.section}`);
    sections.set(record.section, record);
  }
  for (const name of REQUIRED) assert(sections.has(name), `Missing section: ${name}`);
  const get = (name) => sections.get(name);
  const count = (value) => {
    assert(Number.isSafeInteger(value) && value >= 0, `Invalid count: ${value}`);
    return value;
  };
  const sum = (rows, field, predicate = () => true) => {
    assert(Array.isArray(rows), `Missing grouped rows for ${field}`);
    return rows.filter(predicate).reduce((total, row) => total + count(row[field]), 0);
  };
  const bounded = (value, maximum) => assert(count(value) <= count(maximum), 'Count exceeds scope');
  const identity = get('identity');
  assert.equal(identity.transactionReadOnly, 'on', 'Audit was not read-only');
  assert(Number.isFinite(Date.parse(identity.measuredAt)), 'Missing measurement time');
  assert(identity.schemaVersion, 'Missing schema version');
  const inventory = get('inventory');
  assert.equal(count(inventory.rows), count(inventory.nonCustom), 'Inventory scope mismatch');
  assert.equal(count(inventory.per100g) + count(inventory.per100ml) + count(inventory.missingReferenceUnit), inventory.rows);
  const types = get('catalogTypes').values;
  assert.equal(sum(types, 'rows'), inventory.rows, 'Type totals mismatch');
  const published = count(inventory.publishedNonCustom);
  assert.equal(sum(types, 'rows', (row) => row.publication_status === 'PUBLISHED'), published);
  const gtin = get('gtin');
  assert.equal(sum(types, 'rows', (row) => row.catalog_type === 'BRANDED_PRODUCT'), count(gtin.brandedRows));
  assert.equal(['missing', 'invalidFormat', 'validChecksum', 'invalidChecksum']
    .reduce((total, key) => total + count(gtin[key]), 0), gtin.brandedRows, 'GTIN totals mismatch');
  const servings = get('servings');
  assert.equal(count(servings.publishedRows), published);
  assert.equal(['withoutVerifiedVisibleOption', 'oneVerifiedVisibleOption', 'multipleVerifiedVisibleOptions']
    .reduce((total, key) => total + count(servings[key]), 0), published, 'Serving totals mismatch');
  assert.equal(sum(get('servingsByType').values, 'published'), published);
  assert.equal(sum(get('servingsByType').values, 'with_structured_option'),
    servings.oneVerifiedVisibleOption + servings.multipleVerifiedVisibleOptions);
  assert.equal(sum(get('localization').values, 'published'), published);
  for (const row of get('localization').values) {
    for (const key of ['with_tr', 'with_en', 'with_alias', 'with_numeric_source_evidence']) bounded(row[key], row.published);
  }
  const categories = get('canonicalCategoryCoverage');
  assert.equal(count(categories.publishedRows), published);
  bounded(categories.withPrimaryCanonicalCategory, categories.withAnyCanonicalCategory);
  bounded(categories.withAnyCanonicalCategory, published);
  const volume = get('volumeEvidence');
  assert.equal(count(volume.volumeRows), inventory.per100ml);
  bounded(volume.withVolumeCoreEvidence, volume.volumeRows);
  bounded(volume.withMassCoreEvidence, volume.volumeRows);
  bounded(inventory.publishedIncompleteCore, published);
  assert.equal(count(get('micronutrients').rows), published);
  const selfTest = get('auditSelfTest');
  assert(count(selfTest.cases) > 0, 'No checksum fixtures executed');
  assert.equal(count(selfTest.passed), selfTest.cases, 'Checksum fixtures failed');
  assert.equal(selfTest.allPassed, true);
  return {
    integrity: 'PASS', certifiesDataQuality: false, measuredAt: identity.measuredAt,
    sections: sections.size, nonCustomRows: inventory.rows, publishedRows: published,
    checksumFixtures: selfTest.passed,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    const input = process.argv[2];
    assert(input, 'Usage: node scripts/validate-product-catalog-readiness-report.mjs <audit.json>');
    const records = JSON.parse((await fs.readFile(input, 'utf8')).replace(/^\uFEFF/, ''));
    console.log(JSON.stringify(validateReport(records), null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
