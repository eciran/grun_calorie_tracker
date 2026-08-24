import fs from 'node:fs';
import path from 'node:path';

const args = parseArgs(process.argv.slice(2));
const source = args.source || 'dummyjson';
const input = args.input;
const outDir = args.out || 'data/recipe-import/generated';
const stages = (args.stages || '30,50,100,150').split(',').map((value) => Number(value.trim())).filter(Boolean);

if (!input) {
  throw new Error('Missing --input path');
}

const rawText = fs.readFileSync(input, 'utf8');
const sourceRecipes = source === 'dummyjson'
  ? readDummyJson(rawText)
  : readGenericCsv(rawText);

fs.mkdirSync(outDir, { recursive: true });

const normalized = sourceRecipes
  .map((recipe, index) => normalizeRecipe(recipe, source, index))
  .filter(Boolean);

const plan = [];
for (const stageSize of stages) {
  if (normalized.length < stageSize) {
    plan.push({ stageSize, status: 'skipped', reason: `Only ${normalized.length} source recipes available` });
    continue;
  }
  const batch = buildBatch(source, normalized.slice(0, stageSize), stageSize);
  const filename = `${source}-recipe-import-stage-${stageSize}.json`;
  fs.writeFileSync(path.join(outDir, filename), JSON.stringify(batch, null, 2), 'utf8');
  plan.push({ stageSize, status: 'created', file: path.join(outDir, filename), recipeCount: stageSize });
}

fs.writeFileSync(
  path.join(outDir, `${source}-recipe-import-stage-plan.json`),
  JSON.stringify({ source, input, availableRecipes: normalized.length, stages: plan }, null, 2),
  'utf8'
);

console.log(JSON.stringify({ source, availableRecipes: normalized.length, stages: plan }, null, 2));

function parseArgs(argv) {
  const result = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (!key.startsWith('--')) continue;
    result[key.slice(2)] = argv[i + 1];
    i += 1;
  }
  return result;
}

function readDummyJson(text) {
  const parsed = JSON.parse(text);
  return Array.isArray(parsed.recipes) ? parsed.recipes : [];
}

function readGenericCsv(text) {
  const rows = parseCsv(text);
  if (rows.length < 2) return [];
  const headers = rows[0].map((header) => header.trim());
  return rows.slice(1).map((row) => Object.fromEntries(headers.map((header, index) => [header, row[index] ?? ''])));
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    const next = text[i + 1];
    if (char === '"' && inQuotes && next === '"') {
      cell += '"';
      i += 1;
    } else if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === ',' && !inQuotes) {
      row.push(cell);
      cell = '';
    } else if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && next === '\n') i += 1;
      row.push(cell);
      if (row.some((value) => value.length > 0)) rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += char;
    }
  }
  row.push(cell);
  if (row.some((value) => value.length > 0)) rows.push(row);
  return rows;
}

function normalizeRecipe(recipe, sourceName, index) {
  const name = firstValue(recipe, ['name', 'Name', 'title', 'Title', 'recipe_name', 'RecipeName']);
  if (!name) return null;

  const ingredients = toArray(firstValue(recipe, ['ingredients', 'Ingredients', 'ingredient_list', 'IngredientList']));
  const instructions = toArray(firstValue(recipe, ['instructions', 'Instructions', 'directions', 'Directions', 'steps', 'Steps']));
  if (ingredients.length === 0 || instructions.length === 0) return null;

  const tags = toArray(firstValue(recipe, ['tags', 'Tags', 'category', 'Category', 'subcategory', 'Subcategory']));
  const cuisine = firstValue(recipe, ['cuisine', 'Cuisine', 'area', 'Area']) || '';
  const mealTypes = toArray(firstValue(recipe, ['mealType', 'meal_type', 'MealType']));
  const servings = toNumber(firstValue(recipe, ['servings', 'Servings', 'yield', 'Yield'])) || 4;
  const mealType = normalizeMealType(mealTypes[0] || tags[0] || 'DINNER');
  const totalYieldGrams = estimateTotalYieldGrams(servings, mealType, tags, name);

  return {
    sourceKey: `${sourceName}-${recipe.id ?? index + 1}`,
    sourceTitle: name,
    sourceUrl: sourceName === 'dummyjson' && recipe.id ? `https://dummyjson.com/recipes/${recipe.id}` : undefined,
    license: sourceName === 'dummyjson' ? 'DummyJSON public placeholder API terms - internal QA only' : 'CC0-1.0',
    recommendedImportStatus: sourceName === 'dummyjson' ? 'PILOT_REVIEW_ONLY' : 'READY_FOR_REVIEW',
    recipe: {
      name,
      description: buildDescription(recipe, cuisine, tags),
      mealType,
      marketRegion: inferMarketRegion(cuisine, tags),
      language: 'en',
      imageUrl: recipe.image || recipe.imageurl || recipe.imageUrl || undefined,
      totalYieldGrams,
      defaultServingGrams: round(totalYieldGrams / servings),
      servingCount: servings,
      categories: inferCategories({ name, cuisine, tags, mealType, ingredients }),
      allergens: inferAllergens(ingredients),
      ingredients: ingredients.map(normalizeIngredient),
      cookingSteps: instructions.map((instruction) => ({ instruction: String(instruction).trim() })).filter((step) => step.instruction)
    }
  };
}

function firstValue(object, keys) {
  for (const key of keys) {
    if (object[key] !== undefined && object[key] !== null && String(object[key]).trim() !== '') return object[key];
  }
  return undefined;
}

function toArray(value) {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean);
  if (value === undefined || value === null) return [];
  const trimmed = String(value).trim();
  if (!trimmed) return [];
  try {
    const parsed = JSON.parse(trimmed.replaceAll("'", '"'));
    if (Array.isArray(parsed)) return parsed.map((item) => String(item).trim()).filter(Boolean);
  } catch {}
  return trimmed.split(/\r?\n|\s*;\s*/).map((item) => item.trim()).filter(Boolean);
}

function toNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : undefined;
}

function buildDescription(recipe, cuisine, tags) {
  const parts = [];
  if (cuisine) parts.push(`${cuisine} style recipe.`);
  if (recipe.difficulty) parts.push(`Difficulty: ${recipe.difficulty}.`);
  if (recipe.prepTimeMinutes || recipe.cookTimeMinutes) {
    parts.push(`Prep ${recipe.prepTimeMinutes ?? 0} min, cook ${recipe.cookTimeMinutes ?? 0} min.`);
  }
  if (tags.length > 0) parts.push(`Tags: ${tags.join(', ')}.`);
  return parts.join(' ').slice(0, 1000);
}

function normalizeMealType(value) {
  const normalized = String(value || '').toUpperCase();
  if (normalized.includes('BREAKFAST')) return 'BREAKFAST';
  if (normalized.includes('LUNCH')) return 'LUNCH';
  if (normalized.includes('SNACK') || normalized.includes('DESSERT') || normalized.includes('APPETIZER') || normalized.includes('BEVERAGE')) return 'SNACK';
  return 'DINNER';
}

function inferMarketRegion(cuisine, tags) {
  const text = `${cuisine} ${tags.join(' ')}`.toUpperCase();
  if (text.includes('TURKISH')) return 'TR';
  if (text.includes('BRITISH') || text.includes('IRISH') || text.includes('UK')) return 'UK_IE';
  if (/(ITALIAN|FRENCH|GREEK|SPANISH|MEDITERRANEAN|EUROPEAN)/.test(text)) return 'EU';
  return 'GLOBAL';
}

function inferCategories({ name, cuisine, tags, mealType, ingredients }) {
  const text = `${name} ${cuisine} ${tags.join(' ')} ${ingredients.join(' ')}`.toUpperCase();
  const categories = new Set([mealType]);
  const addIf = (condition, category) => { if (condition) categories.add(category); };
  addIf(text.includes('VEGAN'), 'VEGAN');
  addIf(text.includes('VEGETARIAN') || text.includes('TOFU') || text.includes('CHICKPEA'), 'VEGETARIAN');
  addIf(text.includes('CHICKEN'), 'CHICKEN');
  addIf(text.includes('BEEF') || text.includes('MEAT'), 'MEAT');
  addIf(text.includes('FISH') || text.includes('SALMON') || text.includes('TUNA') || text.includes('SHRIMP'), 'FISH');
  addIf(text.includes('SALAD'), 'SALAD');
  addIf(text.includes('SOUP'), 'SOUP');
  addIf(text.includes('DESSERT') || text.includes('COOKIE') || text.includes('CAKE'), 'DESSERT');
  addIf(text.includes('QUICK') || text.includes('STIR-FRY') || text.includes('SMOOTHIE'), 'QUICK_MEAL');
  addIf(text.includes('TURKISH'), 'TURKISH');
  addIf(text.includes('MEDITERRANEAN') || text.includes('ITALIAN') || text.includes('GREEK'), 'MEDITERRANEAN');
  addIf(text.includes('QUINOA') || text.includes('BROCCOLI') || text.includes('VEGETABLE') || text.includes('CUCUMBER'), 'VEGETABLES');
  addIf(text.includes('PROTEIN') || text.includes('CHICKEN') || text.includes('BEEF') || text.includes('SHRIMP'), 'HIGH_PROTEIN');
  return Array.from(categories);
}

function inferAllergens(ingredients) {
  const text = ingredients.join(' ').toUpperCase();
  const allergens = new Set();
  const addIf = (condition, allergen) => { if (condition) allergens.add(allergen); };
  addIf(/MILK|CHEESE|YOGURT|CREAM|BUTTER|MOZZARELLA|PARMESAN|FETA/.test(text), 'MILK');
  addIf(/EGG|EGGS/.test(text), 'EGGS');
  addIf(/FLOUR|PASTA|BREAD|BAGUETTE|NOODLE|DOUGH|WHEAT/.test(text), 'WHEAT');
  addIf(/GLUTEN|FLOUR|PASTA|BREAD|BAGUETTE|NOODLE|DOUGH/.test(text), 'GLUTEN');
  addIf(/SOY SAUCE|TOFU|SOY/.test(text), 'SOYBEANS');
  addIf(/SESAME/.test(text), 'SESAME');
  addIf(/PEANUT/.test(text), 'PEANUTS');
  addIf(/ALMOND|WALNUT|CASHEW|PISTACHIO|NUT/.test(text), 'TREE_NUTS');
  addIf(/FISH|SALMON|TUNA/.test(text), 'FISH');
  addIf(/SHRIMP|CRAB|PRAWN/.test(text), 'CRUSTACEAN_SHELLFISH');
  return Array.from(allergens);
}

function normalizeIngredient(line) {
  const text = String(line).trim();
  const lower = text.toLowerCase();
  const parsed = parseAmount(lower);
  return {
    ingredientName: stripAmount(text),
    portionSize: parsed.portionSize,
    portionUnit: parsed.portionUnit,
    estimatedGrams: parsed.estimatedGrams
  };
}

function parseAmount(lower) {
  const fractionMap = { '1/2': 0.5, '1/3': 0.33, '2/3': 0.67, '1/4': 0.25, '3/4': 0.75 };
  const match = lower.match(/(\d+(?:\.\d+)?)(?:\s+(1\/2|1\/3|2\/3|1\/4|3\/4))?|\b(1\/2|1\/3|2\/3|1\/4|3\/4)\b/);
  let amount = 1;
  if (match) {
    amount = match[1] ? Number(match[1]) : 0;
    const fraction = match[2] || match[3];
    if (fraction) amount += fractionMap[fraction] ?? 0;
  }
  if (/tablespoon|tbsp/.test(lower)) return { portionSize: amount, portionUnit: 'TABLESPOON', estimatedGrams: round(amount * 15) };
  if (/teaspoon|tsp/.test(lower)) return { portionSize: amount, portionUnit: 'TEASPOON', estimatedGrams: round(amount * 5) };
  if (/\bml\b|milliliter/.test(lower)) return { portionSize: amount, portionUnit: 'MILLILITER', estimatedGrams: round(amount) };
  if (/\bg\b|gram/.test(lower)) return { portionSize: amount, portionUnit: 'GRAM', estimatedGrams: round(amount) };
  if (/slice/.test(lower)) return { portionSize: amount, portionUnit: 'SLICE', estimatedGrams: round(amount * 30) };
  if (/cup/.test(lower)) return { portionSize: amount * 240, portionUnit: 'MILLILITER', estimatedGrams: round(amount * 240) };
  return { portionSize: 100, portionUnit: 'GRAM', estimatedGrams: 100 };
}

function stripAmount(text) {
  return text
    .replace(/^\s*\d+(?:\.\d+)?(?:\s+(?:1\/2|1\/3|2\/3|1\/4|3\/4))?\s*/i, '')
    .replace(/^\s*(?:1\/2|1\/3|2\/3|1\/4|3\/4)\s*/i, '')
    .replace(/^(tablespoons?|tbsp|teaspoons?|tsp|cups?|grams?|g|ml|milliliters?|slices?)\s+/i, '')
    .trim()
    .replace(/,$/, '') || text;
}

function estimateTotalYieldGrams(servings, mealType, tags, name) {
  const text = `${mealType} ${tags.join(' ')} ${name}`.toUpperCase();
  const perServing = /SNACK|DESSERT|SMOOTHIE|BEVERAGE/.test(text) ? 180 : 350;
  return round(servings * perServing);
}

function round(value) {
  return Math.round(Number(value) * 100) / 100;
}

function buildBatch(sourceName, recipes, stageSize) {
  const today = new Date().toISOString().slice(0, 10);
  return {
    batchId: `${sourceName}-recipe-import-stage-${stageSize}-${today.replaceAll('-', '')}`,
    createdAt: today,
    purpose: `Stage ${stageSize} recipe import candidate batch for GRun recipe catalog QA. Candidates require admin ingredient mapping and review before publication.`,
    sourcePolicy: sourcePolicy(sourceName),
    recipes
  };
}

function sourcePolicy(sourceName) {
  if (sourceName === 'dummyjson') {
    return {
      sourceName: 'DummyJSON Recipes',
      sourceUrl: 'https://dummyjson.com/docs/recipes',
      selectionBasis: 'Prototype-only pilot recipes used to test GRun recipe import, review, mapping, and mobile catalog flows.',
      license: 'DummyJSON public placeholder API terms - internal QA only',
      licenseUrl: 'https://dummyjson.com/docs/recipes',
      attributionRequired: false,
      shareAlikeRequired: false,
      notes: 'Use for internal testing only unless final legal review confirms production content rights. Prefer CC0 datasets for app release seed data.'
    };
  }
  return {
    sourceName: 'CC0 recipe CSV dataset',
    sourceUrl: 'https://www.kaggle.com/datasets/prashantsingh001/recipes-dataset-64k-dishes',
    selectionBasis: 'CC0 public domain recipe rows selected for category balance, ingredient completeness, and mobile MVP catalog coverage.',
    license: 'CC0-1.0',
    licenseUrl: 'https://creativecommons.org/publicdomain/zero/1.0/',
    attributionRequired: false,
    shareAlikeRequired: false,
    notes: 'Verify dataset card and provenance before production import. Keep sourceKey values stable for duplicate protection.'
  };
}