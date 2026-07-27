import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const charts = fs.readFileSync(new URL("../src/EngagementCharts.tsx", import.meta.url), "utf8");
const engine = fs.readFileSync(new URL("../src/AdminEChart.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.match(app, /lazy\(\(\) => import\("\.\/EngagementCharts"\)/, "Engagement charts must be lazy loaded.");
assert.match(app, /OnboardingFunnelChart items=\{onboardingFunnelItems\}/, "Onboarding analytics must render the funnel chart.");
assert.doesNotMatch(app, /onboardingFunnelItems = \[[\s\S]*Step viewed/, "Repeatable step-view events must not distort funnel width.");
assert.match(app, /No adoption activity/, "Zero-only adoption data must render an explicit empty state.");
assert.match(app, /FeatureAdoptionChart items=\{featureAdoptionItems\}/, "Feature adoption must render the comparison chart.");
assert.match(charts, /type: "funnel"/, "Onboarding must use a funnel visualization.");
assert.match(charts, /sort: "none"/, "Onboarding journey order must not be sorted by value.");
assert.match(charts, /name: "Unique users"[\s\S]*name: "Events"/, "Feature adoption must compare users and events.");
assert.match(charts, /slice\(0, 10\)/, "Feature adoption must remain readable with a bounded top-ten view.");
assert.match(charts, /aria:[\s\S]*enabled: true/, "Engagement charts must expose accessible descriptions.");
assert.match(engine, /FunnelChart/, "The shared chart engine must register funnel support once.");
assert.match(styles, /\.engagement-funnel-chart/, "The onboarding chart must have stable dimensions.");
assert.match(styles, /\.feature-adoption-chart/, "The feature adoption chart must have stable dimensions.");
assert.match(app, /<DataTable[\s\S]*columns=\{\["Feature", "Events", "Users"/, "Exact feature adoption values must remain available in a table.");
assert.doesNotMatch(app, /MiniBarChart label="Funnel events"/, "The legacy onboarding bar view must not remain active.");

console.log("Engagement chart checks passed.");
