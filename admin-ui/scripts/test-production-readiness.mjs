import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = new URL("..", import.meta.url);
const read = (relative) => fs.readFileSync(new URL(relative, root), "utf8");
const app = read("src/App.tsx");
const api = read("src/api.ts");
const primitives = read("src/AdminPrimitives.tsx");
const styles = read("src/styles.css");
const runbook = read("README.md");

assert.match(app, /sectionFromHash\(\)/, "Deep links must resolve through the typed section allowlist.");
assert.match(app, /canViewSection\(accessProfile, active\)/, "Deep links must be checked against backend permissions.");
assert.match(app, /addEventListener\("(hashchange|popstate)"/, "Browser navigation must update the active section.");
assert.match(app, /className="skip-link"/, "Keyboard users need a skip link.");
assert.match(app, /id="admin-main"[^>]+tabIndex=\{-1\}/, "The main workspace must be programmatically focusable.");
assert.match(app, /role="tablist"/, "Section navigation must expose tab semantics.");
assert.match(app, /aria-selected=\{tab\.key === active\}/, "Section tabs must expose selected state.");
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
for (const section of definedSections) {
  assert.ok(activeSections.has(section), `Deep-linkable section ${section} must render a view.`);
}

const assets = fs.readdirSync(new URL("../src/main/resources/static/admin-ui/assets/", root));
const jsAssets = assets.filter((name) => name.endsWith(".js"));
const cssAsset = assets.find((name) => name.endsWith(".css"));
const indexHtml = read("../src/main/resources/static/admin-ui/index.html");
const entryAsset = indexHtml.match(/assets\/(index-[^"]+\.js)/)?.[1];
assert.ok(entryAsset && cssAsset, "Production admin entry assets must be built before the release gate.");
const jsBytes = fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${entryAsset}`, root)).size;
const cssBytes = fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${cssAsset}`, root)).size;
const totalJsBytes = jsAssets.reduce(
  (total, asset) => total + fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${asset}`, root)).size,
  0
);
assert.ok(jsBytes <= 650_000, `Admin entry JS budget exceeded: ${jsBytes} bytes (limit 650000).`);
assert.ok(jsAssets.every((asset) => fs.statSync(new URL(`../src/main/resources/static/admin-ui/assets/${asset}`, root)).size <= 650_000), "Every lazy JS chunk must remain below 650000 bytes.");
assert.ok(totalJsBytes <= 1_300_000, `Total admin JS budget exceeded: ${totalJsBytes} bytes (limit 1300000).`);
assert.ok(cssBytes <= 160_000, `Admin CSS budget exceeded: ${cssBytes} bytes (limit 160000).`);

for (const heading of ["Operator Runbook", "Role Walkthroughs", "Incident Playbook", "Glossary", "Known Production Dependencies"]) {
  assert.match(runbook, new RegExp(`## ${heading}`), `${heading} must be documented.`);
}

console.log(`Production readiness checks passed. Entry JS ${jsBytes} bytes; total JS ${totalJsBytes} bytes; CSS ${cssBytes} bytes.`);