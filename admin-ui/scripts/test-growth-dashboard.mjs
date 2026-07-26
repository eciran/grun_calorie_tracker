import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");
const types = fs.readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");

assert.match(app, /\/api\/v1\/admin\/dashboard\/growth\?from=/, "Growth data must come from the aggregate backend endpoint.");
assert.match(app, /\(\[7, 30, 90\] as const\)/, "Dashboard must expose 7, 30, and 90 day ranges.");
assert.match(app, /function GrowthKpiCard\(/, "Growth KPI cards must be rendered by a dedicated component.");
assert.match(app, /function GrowthTrendChart\(/, "Registration and activity trends must have a dedicated chart.");
assert.match(app, /function GrowthFunnel\(/, "The activation funnel must be present.");
assert.match(app, /function GrowthDistribution\(/, "Plan, region, and language breakdowns must be present.");
assert.match(app, /registrationCoveragePercent/, "Legacy timestamp coverage must be visible.");
assert.match(types, /export type DashboardGrowth =/, "The growth response must have a typed contract.");
assert.match(styles, /\.growth-kpi-grid\s*\{[\s\S]*repeat\(5, minmax\(0, 1fr\)\)/, "Desktop KPI layout must remain compact.");
assert.match(styles, /@media \(max-width: 760px\)[\s\S]*\.growth-kpi-grid/, "Growth metrics must have a mobile layout.");
assert.doesNotMatch(app, /window\.(alert|confirm)\(/, "Growth interactions must not use native browser dialogs.");

console.log("Growth dashboard checks passed.");
