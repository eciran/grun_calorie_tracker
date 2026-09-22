import fs from 'node:fs';
import readline from 'node:readline';
import zlib from 'node:zlib';

const LIQUID_CATEGORIES = new Set([
  'en:beverages', 'en:waters', 'en:juices-and-nectars', 'en:fruit-juices',
  'en:soft-drinks', 'en:carbonated-drinks', 'en:energy-drinks', 'en:sports-drinks',
  'en:milk-drinks', 'en:dairy-drinks', 'en:fermented-drinks', 'en:fermented-milk-drinks',
  'en:plant-based-beverages', 'en:coffee-drinks', 'en:tea-based-beverages', 'en:iced-teas',
  'en:syrups', 'en:simple-syrups',
  'tr:içecek', 'tr:gazlı-içecek'
]);
const CONFLICT_CATEGORIES = new Set([
  'en:powders', 'en:drink-powders', 'en:concentrates',
  'en:ice-creams', 'en:frozen-desserts', 'en:pickles'
]);

export function classifyOffLiquidUnit({categoriesTags, nutritionDataPer}) {
  const categories = String(categoriesTags ?? '').split(',').map(value => value.trim().toLowerCase()).filter(Boolean);
  if (categories.some(category => CONFLICT_CATEGORIES.has(category))) return 'FORM_CONFLICT_REVIEW';
  if (!categories.some(category => LIQUID_CATEGORIES.has(category))) return 'NOT_LIQUID_BY_CATEGORY';
  const basis = String(nutritionDataPer ?? '').trim().toLowerCase();
  if (basis === '100ml') return 'ML_SOURCE_SUPPORTED';
  if (basis === '100g') return 'MASS_VOLUME_BRIDGE_REQUIRED';
  return 'LIQUID_CATEGORY_SOURCE_BASIS_REQUIRED';
}

function parseArgs(argv) {
  const args = {};
  for (let index = 2; index < argv.length; index += 2) args[argv[index].replace(/^--/, '')] = argv[index + 1];
  if (!args.candidates || !args.snapshot || !args.output) {
    throw new Error('Usage: node scripts/analyze-off-liquid-units.mjs --candidates <json> --snapshot <csv.gz> --output <json>');
  }
  return args;
}

function openLines(path) {
  const input = fs.createReadStream(path);
  return readline.createInterface({input: path.endsWith('.gz') ? input.pipe(zlib.createGunzip()) : input, crlfDelay: Infinity});
}

export async function analyze({candidatesPath, snapshotPath}) {
  const candidates = JSON.parse(fs.readFileSync(candidatesPath, 'utf8').replace(/^\uFEFF/, ''));
  if (!Array.isArray(candidates)) throw new Error('Candidate JSON must be an array.');
  const byBarcode = new Map(candidates.filter(row => row.barcode).map(row => [String(row.barcode), row]));
  const matches = [];
  let headers;
  let indexes;
  let rowsRead = 0;
  for await (const line of openLines(snapshotPath)) {
    if (!headers) {
      headers = line.split('\t');
      indexes = Object.fromEntries(headers.map((header, index) => [header, index]));
      for (const required of ['code', 'categories_tags']) {
        if (indexes[required] === undefined) throw new Error(`OFF snapshot header is missing: ${required}`);
      }
      continue;
    }
    rowsRead++;
    const fields = line.split('\t');
    const barcode = fields[indexes.code];
    const candidate = byBarcode.get(barcode);
    if (!candidate) continue;
    const categoriesTags = fields[indexes.categories_tags] ?? '';
    const nutritionDataPer = indexes.nutrition_data_per === undefined ? '' : (fields[indexes.nutrition_data_per] ?? '');
    matches.push({
      id: candidate.id,
      barcode,
      name: candidate.name,
      categoriesTags: categoriesTags.split(',').filter(Boolean),
      nutritionDataPer: nutritionDataPer || null,
      decision: classifyOffLiquidUnit({categoriesTags, nutritionDataPer}),
      applyAllowed: false
    });
    byBarcode.delete(barcode);
    if (byBarcode.size === 0) break;
  }
  const counts = {};
  for (const match of matches) counts[match.decision] = (counts[match.decision] ?? 0) + 1;
  return {
    scope: 'LIQUID_UNIT_CATEGORY_TRIAGE',
    generatedAt: new Date().toISOString(),
    sourceSnapshot: snapshotPath,
    candidateCount: candidates.length,
    matchedCount: matches.length,
    unmatchedCount: candidates.length - matches.length,
    rowsRead,
    counts,
    safety: 'Category establishes physical form only. ML is supported only when the source also declares nutrition_data_per=100ml. A bulk snapshot without that field creates a source-basis review queue. No database writes were performed.',
    matches
  };
}

if (process.argv[1] && import.meta.url === new URL(`file:///${process.argv[1].replaceAll('\\', '/')}`).href) {
  const args = parseArgs(process.argv);
  const report = await analyze({candidatesPath: args.candidates, snapshotPath: args.snapshot});
  fs.writeFileSync(args.output, JSON.stringify(report, null, 2));
  console.log(JSON.stringify({candidateCount: report.candidateCount, matchedCount: report.matchedCount, counts: report.counts}));
}
