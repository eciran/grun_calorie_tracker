import { readAdminAppSource } from "./admin-app-source.mjs";
import assert from "node:assert/strict";
import fs from "node:fs";

const app = readAdminAppSource();
const charts = fs.readFileSync(new URL("../src/AiOperationsCharts.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.match(app, /\/api\/v1\/admin\/ai\/requests\/summary\?windowHours=/, "AI charts must use the privacy-safe aggregate summary endpoint.");
assert.match(app, /userId: userContext\?\.userId/, "AI request operations must preserve the exact Customer 360 user filter.");
assert.match(app, /params\.set\("userId", String\(filters\.userId\)\)/, "AI request list must send the exact user id to the backend.");
assert.match(app, /readAiRequestRouteState[\s\S]*AI_REQUEST_TYPES\.includes\(requestType\)[\s\S]*AI_REQUEST_STATUSES\.includes\(status\)/, "AI request route state must validate request type and status values.");
assert.match(app, /if \(requestType\) search\.set\("requestType"[\s\S]*if \(status\) search\.set\("status"[\s\S]*if \(refundableOnly\) search\.set\("refundableOnly"[\s\S]*if \(page > 0\) search\.set\("page"[\s\S]*if \(pageSize !== 25\) search\.set\("size"/, "AI filters and pagination must remain addressable in the URL.");
assert.match(app, /if \(selectedInspectionId\) search\.set\("requestId"/, "AI request selection must coexist with list route state.");
assert.match(app, /inspection\.correlationId[\s\S]*sectionPaths\.errors[\s\S]*correlationId=/, "Owner AI inspection must link a persisted correlation id to Error Center.");
assert.match(app, /isOwner && inspection\.correlationId/, "Error Center correlation links must remain owner-only.");
assert.match(app, /mode === "overview"[\s\S]*AI monitoring summary/, "System-wide AI summary metrics must remain in the overview workspace.");
assert.match(app, /mode === "requests"[\s\S]*Request filters/, "User-filtered AI requests must remain in their own workspace.");
assert.match(app, /mode === "policy"[\s\S]*ai-policy-page[\s\S]*AI operating guardrails/, "Provider and reliability controls must remain in their own workspace.");
assert.match(app, /useEndpoint<PageResponse<AiMealDraft>>\(path, onError, mode === "requests"\)/, "Request pages must not load request rows outside the request workspace.");
assert.match(app, /useEndpoint<AiMonitoringSummary>\(summaryPath, onError, mode === "overview"\)/, "Summary metrics must load only in the overview workspace.");
assert.match(app, /useEndpoint<AiOperationsPolicy>\(policyPath, onError, mode !== "requests"\)/, "Request review must not load provider policy controls.");
assert.match(app, /lazy\(\(\) => import\("\.\.?\/AiOperationsCharts"\)/, "AI charts must be loaded outside the admin shell bundle.");
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
