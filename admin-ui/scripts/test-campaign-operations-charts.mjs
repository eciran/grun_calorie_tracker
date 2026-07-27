import assert from "node:assert/strict";
import fs from "node:fs";

const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
const charts = fs.readFileSync(new URL("../src/CampaignOperationsCharts.tsx", import.meta.url), "utf8");
const styles = fs.readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");
const controller = fs.readFileSync(new URL("../../src/main/java/com/grun/calorietracker/controller/AdminNotificationCampaignController.java", import.meta.url), "utf8");
const summary = fs.readFileSync(new URL("../../src/main/java/com/grun/calorietracker/dto/AdminNotificationCampaignSummaryDto.java", import.meta.url), "utf8");

assert.match(controller, /@GetMapping\("\/summary"\)/, "Campaign analytics must use a dedicated aggregate endpoint.");
assert.match(controller, /@Max\(90\)/, "Campaign analytics windows must be bounded by the backend.");
assert.doesNotMatch(summary, /userReference|email|title|message/i, "Campaign summary must not expose recipient identity or message payloads.");
assert.match(app, /notification-campaigns\/summary\?windowDays=/, "Campaign UI must request the privacy-safe summary.");
assert.match(app, /\[7, 31, 90\]/, "Campaign UI must provide controlled reporting windows.");
assert.match(app, /lazy\(\(\) => import\("\.\/CampaignOperationsCharts"\)/, "Campaign charts must be lazy loaded.");
assert.match(app, /<CampaignDeliveryChart summary=\{campaignSummary\}/, "Delivery health must be visualized.");
assert.match(app, /<CampaignEngagementFunnel summary=\{campaignSummary\}/, "Engagement funnel must be visualized.");
assert.match(app, /<CampaignStatusChart summary=\{campaignSummary\}/, "Campaign lifecycle must be visualized.");
assert.match(charts, /Delivered[\s\S]*Suppressed[\s\S]*Failed/, "Recipient outcomes must remain operationally distinct.");
assert.match(charts, /Delivered[\s\S]*Opened[\s\S]*Clicked[\s\S]*Converted/, "Engagement funnel must preserve the user journey order.");
assert.match(charts, /aria: \{ enabled: true/, "Campaign charts must expose accessible descriptions.");
assert.match(styles, /\.campaign-analytics-chart-grid[\s\S]*repeat\(2, minmax\(0, 1fr\)\)/, "Campaign charts must use a stable desktop grid.");
assert.match(styles, /@media \(max-width: 980px\)[\s\S]*\.campaign-analytics-chart-grid[\s\S]*grid-template-columns: 1fr/, "Campaign charts must collapse on narrow screens.");
assert.match(app, /Campaign history[\s\S]*CampaignRecipientLedger/, "Exact campaign history and recipient diagnostics must remain available.");

console.log("Campaign operations chart checks passed.");