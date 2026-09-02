import assert from "node:assert/strict";
import fs from "node:fs";

const view = fs.readFileSync("src/modules/FreePromotionPolicyView.tsx", "utf8");
const app = fs.readFileSync("src/App.tsx", "utf8");

assert.match(app, /freePromotion/);
assert.match(app, /GROWTH_READ/);
assert.match(view, /GROWTH_MANAGE/);
assert.match(view, /\/api\/v1\/admin\/free-promotion/);
for (const field of ["enabled", "minimumIntervalHours", "maxImpressions24h", "dismissCooldownHours", "minimumSessionNumber", "rolloutPercentage", "changeReason", "version"]) {
  assert.match(view, new RegExp(field), `Missing managed field: ${field}`);
}
assert.match(view, /0% is a second kill switch/);
assert.match(view, /No automatic paywall will be shown/);
assert.match(view, /changed in another admin session/);
assert.match(view, /Required audit reason/);

console.log("Free promotion admin policy checks passed.");
