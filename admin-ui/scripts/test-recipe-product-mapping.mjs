import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const app = readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const types = readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");

for (const status of ["VERIFIED", "NEEDS_REVIEW", "RAW_IMPORTED"]) {
  assert.match(app, new RegExp(`verificationStatuses[\\s\\S]*\\"${status}\\"`), `Recipe mapping search must include ${status} products.`);
}
assert.match(app, /searchAdminRecipeMappingProducts\(searchText, createForm\.marketRegion\)/, "Manual recipe creation must use the admin mapping search.");
assert.match(app, /searchAdminRecipeMappingProducts\([\s\S]*selectedImportCandidate\?\.marketRegion/, "Recipe imports must use the admin mapping search.");
assert.match(app, /Product ID[^]*Publication status/, "Product review detail must expose the internal ID and publication status.");
assert.match(app, /Product ID #\$\{item\.id\}/, "Product review header must make the mapping ID easy to copy.");
assert.match(app, /item\.publicationStatus/, "Recipe mapping result labels must expose publication state.");
assert.match(types, /publicationStatus\?: string/, "FoodProduct must type the backend publication state.");

console.log("Recipe product mapping UI contract passed.");
