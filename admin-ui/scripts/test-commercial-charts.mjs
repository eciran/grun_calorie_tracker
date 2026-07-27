import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const chart = fs.readFileSync(new URL("../src/RevenueCatEChart.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.match(app, /lazy\(\(\) => import\("\.\/RevenueCatEChart"\)/, "Commercial charts must be loaded outside the admin shell bundle.");
assert.match(app, /<RevenueCatEChart chart=\{\{ \.\.\.chart, points \}\}/, "RevenueCat monitoring must render the shared ECharts view.");
assert.doesNotMatch(app, /function RevenueCatAnalyticsChart[\s\S]*?<svg/, "RevenueCat analytics must not return to hand-built SVG charting.");
assert.match(chart, /AdminEChart/, "Commercial charts must use the shared chart engine.");
assert.match(chart, /type: "line"/, "Time-based provider data must use a line chart.");
assert.match(chart, /type: "bar"/, "Category-based provider data must use a comparison bar chart.");
assert.match(chart, /dataZoom:[\s\S]*points\.length > 30/, "Long provider ranges must remain zoomable.");
assert.match(chart, /isMoneyChart/, "Revenue and MRR values must retain currency-aware formatting.");
assert.match(chart, /aria:[\s\S]*enabled: true/, "Commercial charts must expose accessible descriptions.");
assert.match(styles, /\.revenuecat-echart[\s\S]*height: 340px/, "Commercial charts must have stable responsive dimensions.");
assert.match(app, /Latest[\s\S]*Change[\s\S]*Average[\s\S]*High/, "Exact commercial summaries must remain visible outside the chart.");

console.log("Commercial chart checks passed.");