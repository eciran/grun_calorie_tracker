import assert from "node:assert/strict";
import fs from "node:fs";
import ts from "typescript";

const source = fs.readFileSync(new URL("../src/admin/barcode.ts", import.meta.url), "utf8");
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } });
const { normalizeBarcode, validateGtin } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`);

assert.equal(normalizeBarcode(" 0301-7620 4220 03 "), "03017620422003", "leading zeroes must be preserved");
for (const barcode of ["96385074", "036000291452", "3017620422003", "10012345000017"]) {
  assert.deepEqual(validateGtin(barcode), { valid: true, barcode, reason: undefined });
}
assert.deepEqual(validateGtin("3017620422004"), { valid: false, barcode: "3017620422004", reason: "checksum" });
assert.deepEqual(validateGtin("12345"), { valid: false, barcode: "12345", reason: "length" });
console.log("Barcode scanner contract passed: normalization, leading zeroes, GTIN lengths and check digits.");
