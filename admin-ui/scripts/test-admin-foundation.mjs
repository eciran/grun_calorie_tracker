import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const primitives = fs.readFileSync(new URL("../src/AdminPrimitives.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.match(app, /from "\.\/AdminPrimitives"/, "App must consume the shared admin primitives.");
assert.doesNotMatch(app, /function SectionToolbar\(/, "SectionToolbar must not be redefined in App.");
assert.doesNotMatch(app, /function DataTable</, "DataTable must not be redefined in App.");
assert.doesNotMatch(app, /window\.(alert|confirm)\(/, "Native browser dialogs are not allowed.");

for (const component of [
  "SectionToolbar",
  "MetricCard",
  "Panel",
  "CollapsiblePanel",
  "DataTable",
  "PaginationControls",
  "AsyncState",
  "EmptyState"
]) {
  assert.match(primitives, new RegExp(`export function ${component}`), `${component} must be exported.`);
}

assert.match(primitives, /scope="col"/, "Table headers must expose column scope.");
assert.match(primitives, /onKeyDown=/, "Clickable rows must support keyboard activation.");
assert.match(primitives, /aria-label="Table pagination"/, "Pagination must expose an accessible label.");
assert.match(styles, /\.clickable-row:focus-visible/, "Keyboard focus must be visible.");
assert.match(styles, /\.async-state\.error-state/, "Async error state must be styled.");

assert.match(app, /<QRCodeSVG value=\{enrollment\.otpauthUri\}/, "MFA enrollment must render its QR code locally from the backend URI.");
assert.match(app, /<summary>Manual setup<\/summary>/, "MFA enrollment must retain a manual setup fallback.");
assert.match(app, /aria-label="QR code for authenticator app enrollment"/, "MFA enrollment QR code must have an accessible label.");

console.log("Admin foundation checks passed.");
