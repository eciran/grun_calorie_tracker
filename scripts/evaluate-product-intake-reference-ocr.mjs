import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import zlib from 'node:zlib';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const frontend = process.env.GRUN_FRONTEND_PATH || 'C:/Users/emrah/IdeaProjects/Claude_Grun_frontend';
const ts = require(path.join(frontend, 'node_modules/typescript'));
const sourcePath = path.join(frontend, 'src/services/productOcr.ts');
const source = fs.readFileSync(sourcePath, 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const sandbox = { module: { exports: {} }, exports: {}, require: (name) => name === 'react-native' ? { NativeModules: {}, Platform: { OS: 'android' } } : name === 'expo-modules-core' ? { requireOptionalNativeModule: () => null } : require(name) };
sandbox.exports = sandbox.module.exports;
vm.runInNewContext(`(function(require,module,exports){${compiled}\n})(require,module,exports)`, sandbox, { filename: sourcePath });
const parseNutritionObservations = sandbox.module.exports.parseNutritionObservations;
if (typeof parseNutritionObservations !== 'function') throw new Error('Mobile parser could not be loaded.');

const market = (process.argv[2] || 'tr').toLowerCase();
const target = Number(process.argv[3] || 50);
const api = `https://${market}.openfoodfacts.org/api/v2/search`;
const headers = { 'User-Agent': 'GrunProductIntakePilot/1.0 (reference-corpus)' };
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function fetchWithRetry(url) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const response = await fetch(url, { headers });
    if (response.ok || (response.status !== 429 && response.status !== 503)) return response;
    await sleep(2000 * (2 ** attempt));
  }
  return fetch(url, { headers });
}
const candidates = [];
for (let page = 1; page <= 12 && candidates.length < target * 2; page++) {
  const params = new URLSearchParams({ states_tags: 'nutrition-facts-completed', page: String(page), page_size: '50', fields: 'code,product_name,brands,lang,images,nutriments' });
  const response = await fetchWithRetry(`${api}?${params}`);
  if (!response.ok) throw new Error(`Open Food Facts search failed: ${response.status}`);
  const body = await response.json();
  for (const product of body.products || []) {
    const keys = Object.keys(product.images || {}).filter((key) => key.startsWith('nutrition_'));
    const preferred = keys.find((key) => key === `nutrition_${market}`) || keys.find((key) => key === 'nutrition_en') || keys[0];
    if (!preferred) continue;
    const imgid = String(product.images[preferred]?.imgid || '');
    const n = product.nutriments || {};
    if (!imgid || !n['energy-kcal_100g'] || !n.proteins_100g || !n.carbohydrates_100g || !n.fat_100g) continue;
    candidates.push({ product, imageKey: preferred, imgid });
    if (candidates.length >= target * 2) break;
  }
}
const folder = (code) => code.length > 8 ? code.replace(/(...)(...)(...)(.*)/, '$1/$2/$3/$4') : code;
const tolerance = (actual, expected) => Number.isFinite(actual) && Math.abs(actual - expected) <= Math.max(0.5, Math.abs(expected) * 0.08);
const rows = [];
for (const candidate of candidates) {
  if (rows.length >= target) break;
  const { product, imageKey, imgid } = candidate;
  const ocrUrl = `https://openfoodfacts-images.s3.eu-west-3.amazonaws.com/data/${folder(product.code)}/${imgid}.json.gz`;
  try {
    const response = await fetchWithRetry(ocrUrl);
    if (!response.ok) continue;
    const json = JSON.parse(zlib.gunzipSync(Buffer.from(await response.arrayBuffer())).toString('utf8'));
    const annotation = json.responses?.[0]?.textAnnotations?.[0];
    if (!annotation?.description) continue;
    const words = (json.responses?.[0]?.textAnnotations || []).slice(1);
    const observations = words.length ? words.map((item) => {
      const vertices = item.boundingPoly?.vertices || [];
      const xs = vertices.map((point) => Number(point.x || 0));
      const ys = vertices.map((point) => Number(point.y || 0));
      const x = Math.min(...xs); const y = Math.min(...ys);
      return { text: item.description, confidence: 0.8, box: { x, y, width: Math.max(...xs) - x, height: Math.max(...ys) - y } };
    }) : annotation.description.split(/\r?\n/).filter(Boolean).map((text) => ({ text, confidence: 0.8 }));
    const parsed = parseNutritionObservations(observations);
    const n = product.nutriments;
    const expected = { calories: Number(n['energy-kcal_100g']), protein: Number(n.proteins_100g), carbs: Number(n.carbohydrates_100g), fat: Number(n.fat_100g), sugar: Number(n.sugars_100g), fiber: Number(n.fiber_100g), sodium: Number(n.sodium_100g) * 1000 };
    const critical = ['calories', 'protein', 'carbs', 'fat'];
    const extracted = critical.filter((field) => parsed.fields[field] !== undefined).length;
    const correct = critical.filter((field) => tolerance(Number(parsed.fields[field]), expected[field])).length;
    rows.push({ barcode: product.code, productName: product.product_name || '', labelLanguage: imageKey.slice(10), ocrLocale: annotation.locale || '', ocrUrl, expected, parsed: parsed.fields, warnings: parsed.warnings, criticalExtracted: extracted, criticalCorrect: correct });
  } catch { /* unavailable OCR artifacts are skipped */ }
}
const totalCritical = rows.length * 4;
const extracted = rows.reduce((sum, row) => sum + row.criticalExtracted, 0);
const correct = rows.reduce((sum, row) => sum + row.criticalCorrect, 0);
const report = { evidenceType: 'REFERENCE_OCR_NOT_DEVICE', marketEndpoint: market, requested: target, evaluated: rows.length, criticalFieldCoverage: totalCritical ? extracted / totalCritical : 0, criticalFieldPrecision: extracted ? correct / extracted : 0, parserSource: sourcePath, generatedAt: new Date().toISOString(), rows };
const output = path.resolve(`outputs/product-intake-${market}-reference-ocr-report.json`);
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, JSON.stringify(report, null, 2));
console.log(JSON.stringify({ output, requested: target, evaluated: rows.length, criticalFieldCoverage: report.criticalFieldCoverage, criticalFieldPrecision: report.criticalFieldPrecision }, null, 2));
if (rows.length < target) process.exitCode = 3;