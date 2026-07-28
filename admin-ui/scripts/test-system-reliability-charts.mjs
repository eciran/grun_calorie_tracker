import fs from "node:fs";

const view = fs.readFileSync(new URL("../src/RuntimeOperationsView.tsx", import.meta.url), "utf8");
const chart = fs.readFileSync(new URL("../src/SystemReliabilityCharts.tsx", import.meta.url), "utf8");
const types = fs.readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");

const checks = [
  [view.includes("/api/v1/admin/system/operations/reliability-analytics?windowHours="), "Reliability analytics endpoint is not wired."],
  [view.includes("System and provider reliability") && view.includes("reliabilityWindowHours"), "Reliability panel or time window is missing."],
  [chart.includes("ApiReliabilityChart") && chart.includes("InfrastructureReliabilityChart"), "API or infrastructure chart is missing."],
  [chart.includes("ProviderReliabilityChart") && chart.includes("OperationReliabilityChart"), "Provider or operation chart is missing."],
  [chart.includes("No API traffic has been recorded") && !chart.includes("rawPayload"), "Safe API empty state or payload minimization is missing."],
  [types.includes("export type SystemReliabilityAnalytics"), "Reliability response type is missing."]
];

const failed = checks.filter(([ok]) => !ok);
if (failed.length) {
  for (const [, message] of failed) console.error(message);
  process.exit(1);
}
console.log("System reliability analytics UI contract passed.");