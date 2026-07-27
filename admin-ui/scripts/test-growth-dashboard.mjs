import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const chart = fs.readFileSync(new URL("../src/GrowthTrendChart.tsx", import.meta.url), "utf8");
const engine = fs.readFileSync(new URL("../src/AdminEChart.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");
const types = fs.readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");

assert.match(app, /\/api\/v1\/admin\/dashboard\/growth\?from=/, "Growth data must come from the aggregate backend endpoint.");
assert.match(app, /\(\[7, 30, 90\] as const\)/, "Dashboard must expose 7, 30, and 90 day ranges.");
assert.match(app, /function GrowthKpiCard\(/, "Growth KPI cards must be rendered by a dedicated component.");
assert.match(app, /lazy\(\(\) => import\("\.\/GrowthTrendChart"\)/, "The chart must be loaded outside the admin shell bundle.");
assert.match(chart, /AdminEChart/, "Growth trends must use the shared chart engine.");
assert.match(chart, /type: "bar"[\s\S]*type: "line"/, "Registration bars and active-user lines must both be configured.");
assert.match(chart, /dataZoom:/, "Long reporting ranges must remain navigable.");
assert.match(engine, /DataZoomComponent/, "The shared chart engine must register zoom support once.");
assert.match(chart, /aria:[\s\S]*enabled: true/, "The chart must expose an accessible description.");
assert.match(engine, /renderer: "svg"/, "Admin charts must use the SVG renderer.");
assert.match(engine, /ResizeObserver/, "Admin charts must respond to panel and viewport resizing.");
assert.match(engine, /MutationObserver/, "Admin charts must refresh when the admin theme changes.");
assert.match(engine, /prefers-reduced-motion/, "Admin charts must respect reduced-motion preferences.");
assert.match(app, /function GrowthFunnel\(/, "The activation funnel must be present.");
assert.match(app, /function GrowthDistribution\(/, "Plan, region, and language breakdowns must be present.");
assert.match(app, /registrationCoveragePercent/, "Legacy timestamp coverage must be visible.");
assert.match(types, /export type DashboardGrowth =/, "The growth response must have a typed contract.");
assert.match(styles, /\.growth-kpi-grid\s*\{[\s\S]*repeat\(5, minmax\(0, 1fr\)\)/, "Desktop KPI layout must remain compact.");
assert.match(styles, /@media \(max-width: 760px\)[\s\S]*\.growth-kpi-grid/, "Growth metrics must have a mobile layout.");
assert.doesNotMatch(app, /window\.(alert|confirm)\(/, "Growth interactions must not use native browser dialogs.");

console.log("Growth dashboard checks passed.");
