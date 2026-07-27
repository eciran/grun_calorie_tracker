import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const chart = fs.readFileSync(new URL("../src/RecipeOperationsCharts.tsx", import.meta.url), "utf8");
const types = fs.readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");

const checks = [
  [app.includes('/api/v1/admin/recipes/analytics?windowDays='), "Recipe analytics endpoint is not wired into the admin UI."],
  [app.includes("RecipeModerationChart") && app.includes("RecipeSubmissionTrendChart"), "Recipe operation charts are not rendered."],
  [app.includes("Analytics window") && app.includes("90 days"), "Recipe analytics window control is missing."],
  [chart.includes("AdminEChart") && chart.includes("pendingAgeBands") && chart.includes("importStatuses"), "Recipe chart module does not cover moderation backlog and imports."],
  [types.includes("export type AdminRecipeOperationsAnalytics"), "Recipe analytics response type is missing."]
];

const failed = checks.filter(([ok]) => !ok);
if (failed.length) {
  for (const [, message] of failed) console.error(message);
  process.exit(1);
}
console.log("Recipe operations analytics UI contract passed.");