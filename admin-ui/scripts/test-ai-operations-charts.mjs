import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const charts = fs.readFileSync(new URL("../src/AiOperationsCharts.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.match(app, /\/api\/v1\/admin\/ai\/requests\/summary\?windowHours=/, "AI charts must use the privacy-safe aggregate summary endpoint.");
assert.match(app, /lazy\(\(\) => import\("\.\/AiOperationsCharts"\)/, "AI charts must be loaded outside the admin shell bundle.");
assert.match(app, /<AiOutcomeChart summary=\{summary\}/, "AI Ops must render request outcomes.");
assert.match(app, /<AiLatencyChart summary=\{summary\}/, "AI Ops must render latency percentiles.");
assert.match(app, /<AiEconomicsChart summary=\{summary\}/, "AI Ops must render currency-safe economics.");
assert.match(charts, /stack: "outcomes"/, "Request outcome series must use a common stacked scale.");
assert.match(charts, /DRAFT_CREATED[\s\S]*CONFIRMED[\s\S]*REJECTED[\s\S]*FAILED/, "Controlled request outcome states must be represented.");
assert.match(charts, /latencyP50Ms[\s\S]*latencyP95Ms[\s\S]*latencyP99Ms/, "Latency chart must compare p50, p95, and p99.");
assert.match(charts, /Object\.keys\(cost\)[\s\S]*Object\.keys\(revenue\)/, "Economics chart must preserve backend currency groups.");
assert.doesNotMatch(charts, /prompt|response|outputPayload|requestPayload/i, "Aggregate charts must not consume raw AI content.");
assert.match(charts, /aria:[\s\S]*enabled: true/, "AI charts must expose accessible descriptions.");
assert.match(styles, /\.ai-analytics-chart-grid[\s\S]*repeat\(2, minmax\(0, 1fr\)\)/, "AI charts must use the shared responsive grid.");
assert.match(styles, /@media \(max-width: 980px\)[\s\S]*\.ai-analytics-chart-grid[\s\S]*grid-template-columns: 1fr/, "AI charts must collapse without horizontal overflow.");
assert.match(app, /Provider, model, and prompt version[\s\S]*Request type and status/, "Exact AI monitoring tables must remain available.");

console.log("AI operations chart checks passed.");