import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const charts = fs.readFileSync(new URL("../src/CatalogQualityCharts.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");
const controller = fs.readFileSync(new URL("../../src/main/java/com/grun/calorietracker/controller/AdminCatalogOperationsController.java", import.meta.url), "utf8");
const summary = fs.readFileSync(new URL("../../src/main/java/com/grun/calorietracker/dto/AdminCatalogQualityAnalyticsDto.java", import.meta.url), "utf8");

assert.match(controller, /@GetMapping\("\/quality-analytics"\)/, "Catalog quality charts must use a dedicated aggregate endpoint.");
assert.match(controller, /@Min\(7\) @Max\(90\)/, "Catalog quality reporting windows must be bounded.");
assert.doesNotMatch(summary, /productName|barcode|foodItemId|email/i, "Catalog quality analytics must not expose product or user identifiers.");
assert.match(app, /catalog\/quality-analytics\?windowDays=/, "Quality UI must request the aggregate analytics endpoint.");
assert.match(app, /<option value=\{7\}>7 days/, "Quality UI must offer controlled reporting windows.");
assert.match(app, /lazy\(\(\) => import\("\.\/CatalogQualityCharts"\)/, "Catalog quality charts must be lazy loaded.");
assert.match(app, /<CatalogVerificationChart analytics=\{qualityAnalytics\}/, "Verification state must be visualized.");
assert.match(app, /<CatalogIssueChart analytics=\{qualityAnalytics\}/, "Open issue concentration must be visualized.");
assert.match(app, /<CatalogScanTrendChart analytics=\{qualityAnalytics\}/, "Scan productivity must be visualized.");
assert.match(charts, /VERIFIED[\s\S]*NEEDS_REVIEW[\s\S]*RAW_IMPORTED[\s\S]*REJECTED/, "Verification states must remain visually distinct.");
assert.match(charts, /\.slice\(0, 8\)/, "Issue chart must remain bounded and readable.");
assert.match(charts, /Scanned[\s\S]*Validated[\s\S]*Suggestions[\s\S]*Failed runs/, "Scan outcomes must remain operationally distinct.");
assert.match(charts, /aria: \{ enabled: true/, "Catalog quality charts must expose accessible descriptions.");
assert.match(styles, /\.catalog-quality-chart-grid[\s\S]*repeat\(2, minmax\(0, 1fr\)\)/, "Catalog quality charts must use a stable desktop grid.");
assert.match(styles, /@media \(max-width: 900px\)[\s\S]*\.catalog-quality-chart-grid[\s\S]*grid-template-columns: 1fr/, "Catalog quality charts must collapse on narrow screens.");
assert.match(app, /Recent quality scan runs[\s\S]*Quality suggestion queue/, "Exact scan and suggestion diagnostics must remain available.");

console.log("Catalog quality chart checks passed.");
