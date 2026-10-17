import assert from 'node:assert/strict';
import test from 'node:test';
import { validateReport } from './validate-product-catalog-readiness-report.mjs';

function fixture() {
  return [
    {section:'identity',transactionReadOnly:'on',measuredAt:'2026-10-11T18:24:20Z',schemaVersion:'305'},
    {section:'inventory',rows:3,nonCustom:3,publishedNonCustom:2,per100g:2,per100ml:1,missingReferenceUnit:0,publishedIncompleteCore:1},
    {section:'catalogTypes',values:[
      {catalog_type:'BRANDED_PRODUCT',publication_status:'PUBLISHED',rows:1},
      {catalog_type:'GENERIC_INGREDIENT',publication_status:'PUBLISHED',rows:1},
      {catalog_type:'BRANDED_PRODUCT',publication_status:'INTERNAL_REVIEW',rows:1}]},
    {section:'gtin',brandedRows:2,missing:1,invalidFormat:0,validChecksum:1,invalidChecksum:0},
    {section:'identityGroups',values:[]},
    {section:'servings',publishedRows:2,withoutVerifiedVisibleOption:1,oneVerifiedVisibleOption:1,multipleVerifiedVisibleOptions:0},
    {section:'localization',values:[{published:2,with_tr:1,with_en:2,with_alias:1,with_numeric_source_evidence:2}]},
    {section:'categoryCoverage'}, {section:'qualitySuggestions'}, {section:'qualityRuns'},
    {section:'publishedCoreFailureSamples'},
    {section:'servingsByType',values:[{published:2,with_structured_option:1}]},
    {section:'volumeEvidence',volumeRows:1,withVolumeCoreEvidence:1,withMassCoreEvidence:1},
    {section:'canonicalCategoryCoverage',publishedRows:2,withPrimaryCanonicalCategory:1,withAnyCanonicalCategory:1},
    {section:'micronutrients',rows:2}, {section:'brandedCollapse'}, {section:'misclassificationCandidates'},
    {section:'auditSelfTest',cases:9,passed:9,allPassed:true},
  ];
}

test('valid report does not certify data quality or reject unresolved issues', () => {
  assert.equal(validateReport(fixture()).certifiesDataQuality, false);
});
test('missing and duplicate sections fail', () => {
  assert.throws(() => validateReport(fixture().slice(1)), /Missing section/);
  assert.throws(() => validateReport([...fixture(), fixture()[0]]), /Duplicate section/);
});
test('write-enabled audit fails', () => {
  const data=fixture(); data[0].transactionReadOnly='off';
  assert.throws(() => validateReport(data), /not read-only/);
});
test('mixed local and AWS denominators fail', () => {
  const data=fixture(); data[1].nonCustom=200000;
  assert.throws(() => validateReport(data), /scope mismatch/);
});
test('GTIN count and non-integer corruption fail', () => {
  const data=fixture(); data[3].validChecksum=0;
  assert.throws(() => validateReport(data), /GTIN totals mismatch/);
  data[3].validChecksum=0.5;
  assert.throws(() => validateReport(data), /Invalid count/);
});
test('serving count corruption fails', () => {
  const data=fixture(); data[5].oneVerifiedVisibleOption=2;
  assert.throws(() => validateReport(data), /Serving totals mismatch/);
});
test('coverage outside its scope fails', () => {
  const data=fixture(); data[6].values[0].with_tr=3;
  assert.throws(() => validateReport(data), /exceeds scope/);
});
test('failed checksum fixtures fail the report gate', () => {
  const data=fixture(); data.at(-1).passed=8;
  assert.throws(() => validateReport(data), /fixtures failed/);
});
