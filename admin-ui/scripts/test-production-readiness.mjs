import { readAdminAppSource } from "./admin-app-source.mjs";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = new URL("..", import.meta.url);
const read = (relative) => fs.readFileSync(new URL(relative, root), "utf8");
const app = readAdminAppSource();
const api = read("src/api.ts");
const primitives = read("src/AdminPrimitives.tsx");
const styles = read("src/styles.css");
const runbook = read("README.md");

assert.match(app, /sectionFromLocation\(\)/, "Deep links must resolve through the typed section allowlist.");
assert.match(app, /canViewSection\(accessProfile, active\)/, "Deep links must be checked against backend permissions.");
assert.match(app, /addEventListener\("(hashchange|popstate)"/, "Browser navigation must update the active section.");
assert.match(app, /className="skip-link"/, "Keyboard users need a skip link.");
assert.match(app, /id="admin-main"[^>]+tabIndex=\{-1\}/, "The main workspace must be programmatically focusable.");
assert.match(app, /<nav className="section-tabs"/, "Related pages must expose navigation semantics.");
assert.match(app, /aria-current=\{tab\.key === active \? "page"/, "Related pages must expose the current page.");
assert.match(app, /role="alert"/, "Global request failures must be announced.");
assert.match(app, /aria-expanded=\{section\.children/, "Expandable navigation groups must expose state.");
assert.match(primitives, /scope="col"/, "Data table headers must remain accessible.");
assert.match(primitives, /onKeyDown=/, "Clickable table rows must remain keyboard operable.");
assert.match(styles, /prefers-reduced-motion/, "Reduced-motion preferences must be respected.");
assert.match(styles, /\.skip-link:focus/, "Skip link focus styling is required.");

assert.doesNotMatch(app, /window\.(alert|confirm|prompt)\(/, "Native dialogs are not allowed in production admin flows.");
assert.doesNotMatch(api, /window\.(alert|confirm|prompt)\(/, "Native dialogs are not allowed in API security flows.");
assert.doesNotMatch(app, /dangerouslySetInnerHTML/, "Untrusted HTML rendering is not allowed.");
assert.doesNotMatch(api, /localStorage\.setItem\([^,]*(token|refresh)/i, "Auth tokens must not use persistent local storage.");
assert.match(api, /let accessToken: string \| null = null/, "Access tokens must remain in memory only.");
assert.doesNotMatch(api, /(localStorage|sessionStorage)\.(setItem|getItem)\([^)]*(token|refresh)/i, "Auth tokens must not use browser storage.");

const activeSections = new Set([...app.matchAll(/active === "([A-Za-z0-9]+)"/g)].map((match) => match[1]));
const navigableSections = new Set([...app.matchAll(/navSection\("([A-Za-z0-9]+)"\)/g)].map((match) => match[1]));
for (const section of navigableSections) {
  assert.ok(activeSections.has(section), `Navigable section ${section} must render a view.`);
}
const sectionDefinitions = app.match(/const sections: SectionMeta\[\] = \[([\s\S]*?)\n\];/)?.[1] ?? "";
const definedSections = new Set([...sectionDefinitions.matchAll(/key: "([A-Za-z0-9]+)"/g)].map((match) => match[1]));
const compatibilityOnlySections = new Set([
  "foodImports", "foodRegions", "productDuplicates", "productNutrition", "productRejected",
  "subscriptionEntitlements", "brevoSenders", "mailEvents", "integrationProviders", "settings",
  "systemRuntime", "systemDatabase", "systemProviders", "trackingWater", "trackingFasting", "trackingSteps"
]);
for (const section of definedSections) {
  if (compatibilityOnlySections.has(section)) continue;
  assert.ok(activeSections.has(section), `Deep-linkable section ${section} must render a view.`);
}

const assets = fs.readdirSync(new URL("../src/main/resources/static/admin-ui/assets/", root));
const jsAssets = assets.filter((name) => name.endsWith(".js"));
const indexHtml = read("../src/main/resources/static/admin-ui/index.html");
const cssAsset = indexHtml.match(/assets\/(index-[^"]+\.css)/)?.[1];
const entryAsset = indexHtml.match(/assets\/(index-[^"]+\.js)/)?.[1];
assert.ok(entryAsset && cssAsset, "Production admin entry assets must be built before the release gate.");
const jsBytes = fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${entryAsset}`, root)).size;
const cssBytes = fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${cssAsset}`, root)).size;
const totalJsBytes = jsAssets.reduce(
  (total, asset) => total + fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${asset}`, root)).size,
  0
);
// V2 page splitting reduced the entry from 704 KB to about 265 KB.
assert.ok(jsBytes <= 300_000, `Admin entry JS budget exceeded: ${jsBytes} bytes (limit 300000).`);
assert.ok(jsAssets.every((asset) => fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${asset}`, root)).size <= 750_000), "Every lazy JS chunk must remain below 750000 bytes.");
assert.ok(totalJsBytes <= 2_500_000, `Total admin JS budget exceeded: ${totalJsBytes} bytes (limit 2500000).`);
// Consolidated admin workspaces, responsive editors and operational dashboards establish a 325 KB baseline.
assert.ok(cssBytes <= 340_000, `Admin CSS budget exceeded: ${cssBytes} bytes (limit 340000).`);

for (const heading of ["Operator Runbook", "Role Walkthroughs", "Incident Playbook", "Glossary", "Known Production Dependencies"]) {
  assert.match(runbook, new RegExp(`## ${heading}`), `${heading} must be documented.`);
}

console.log(`Production readiness checks passed. Entry JS ${jsBytes} bytes; total JS ${totalJsBytes} bytes; CSS ${cssBytes} bytes.`);
