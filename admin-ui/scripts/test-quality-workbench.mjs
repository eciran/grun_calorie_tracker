import { readAdminAppSource } from "./admin-app-source.mjs";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const app = readAdminAppSource();
const productIntake = readFileSync(resolve(root, "src/ProductIntakeView.tsx"), "utf8");

const requiredWorkbenchTabs = ["overview", "nutrition", "names", "aliases", "serving", "evidence", "ai", "audit"];
for (const tab of requiredWorkbenchTabs) {
  assert.match(app, new RegExp(`\\"${tab}\\"`), `Missing product workbench tab: ${tab}`);
}

assert.match(app, /Analyze product with AI/, "Product-level AI analyze action is missing.");
assert.match(app, /productIds:\s*\[product\.id\]/, "Product-level AI request must send the selected product id.");
assert.match(app, /quality-workbench/, "Product workbench context endpoint is not wired.");
assert.match(app, /pendingHighImpact/, "High-impact nutrition confirmation state is missing.");
assert.match(app, /Apply verified change/, "High-impact nutrition confirmation action is missing.");
assert.match(app, /canApplyWorkbenchSuggestion/, "Unsafe recommendation types must remain review-only.");
assert.match(app, /Review only/, "Review-only recommendation state is missing.");
assert.match(app, /SAFE_NUTRITION_SUGGESTION_TYPES/, "Nutrition suggestions must use an explicit type allowlist.");
assert.match(app, /suggestionType === "MISSING_SERVING_SIZE"/, "Serving fields must only apply from serving-size suggestions.");
const safeApplySection = app.match(/const SAFE_NUTRITION_SUGGESTION_TYPES[\s\S]*?function isHighImpactProductSuggestion/)?.[0] ?? "";
for (const unsafeField of ["verificationStatus", "marketRegion", "imageUrl", "allergens", "canonicalFoodKey", "preparationState"]) {
  assert.doesNotMatch(safeApplySection, new RegExp('"' + unsafeField + '"'), 'Unsafe field must remain review-only: ' + unsafeField);
}
assert.match(app, /ai-validate-selected/, "Bulk AI validation endpoint is not wired.");
assert.match(app, /selectedOpenSuggestionIds/, "Bulk AI validation must use explicit admin selection.");
assert.match(app, /Math\.min\(selectedOpenSuggestionIds\.length, 25\)/, "Bulk AI selection must remain capped at 25.");
assert.match(app, /readProductReviewRouteState/, "Product review filters must be restorable from the page URL.");
assert.match(app, /productReviewRouteSearch/, "Product review state must be serialized into the page URL.");
assert.match(app, /params\.set\("productId", String\(state\.selectedProductId\)\)/, "Selected product identity must be persisted for reload/back navigation.");
assert.match(productIntake, /OCR processing history/, "Product intake must expose persisted OCR processing history.");
assert.match(productIntake, /v3Fields[\s\S]*v4Fields[\s\S]*fallbackFields[\s\S]*confirmedFields/, "OCR review must compare parser, fallback, and user-confirmed fields.");
assert.match(productIntake, /run\.fallbackInvoked[\s\S]*run\.latencyMs[\s\S]*run\.estimatedCostUsd/, "OCR review must distinguish fallback execution, latency, and measured cost.");
assert.match(productIntake, /isOwner && run\.correlationId[\s\S]*sectionPaths\.errors/, "OCR Error Center drill-down must be owner-only and correlation-based.");

console.log("Product quality workbench UI contract passed.");
