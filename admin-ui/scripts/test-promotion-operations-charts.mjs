import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const chart = fs.readFileSync(new URL("../src/PromotionOperationsCharts.tsx", import.meta.url), "utf8");
const types = fs.readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");

const checks = [
  [app.includes("/api/v1/admin/promotions/analytics?windowDays="), "Promotion analytics endpoint is not wired into the admin UI."],
  [app.includes("PromotionLifecycleChart") && app.includes("PromotionRedemptionTrendChart"), "Promotion operation charts are not rendered."],
  [app.includes("Promotion performance") && app.includes("90 days"), "Promotion analytics window control is missing."],
  [chart.includes("AdminEChart") && chart.includes("promotionTypes") && chart.includes("rejectionCategories") && chart.includes("duplicateAttempts"), "Promotion chart module does not cover rejection and abuse signals."],
  [types.includes("export type AdminPromotionOperationsAnalytics"), "Promotion analytics response type is missing."]
];

const failed = checks.filter(([ok]) => !ok);
if (failed.length) {
  for (const [, message] of failed) console.error(message);
  process.exit(1);
}
console.log("Promotion operations analytics UI contract passed.");
