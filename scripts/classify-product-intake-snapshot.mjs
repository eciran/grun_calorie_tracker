#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

function argument(name) {
  const index = process.argv.indexOf(name);
  if (index < 0 || !process.argv[index + 1]) throw new Error(`Missing ${name}`);
  return process.argv[index + 1];
}

const snapshot = JSON.parse(fs.readFileSync(argument("--snapshot"), "utf8"));
const details = JSON.parse(fs.readFileSync(argument("--details"), "utf8"));
const output = argument("--output");
const rows = snapshot.items ?? snapshot.content ?? snapshot.cases ?? snapshot.data ?? [];

const byBarcode = new Map();
for (const row of rows) {
  const barcode = row.barcode ?? row.summary?.barcode;
  if (!barcode) continue;
  const matches = byBarcode.get(String(barcode)) ?? [];
  matches.push(row);
  byBarcode.set(String(barcode), matches);
}

const duplicateBarcodes = [...byBarcode.entries()]
  .filter(([, matches]) => matches.length > 1)
  .sort(([left], [right]) => left.localeCompare(right))
  .map(([barcode, matches]) => ({
    barcode,
    count: matches.length,
    cases: matches.map((row) => ({
      id: row.id ?? row.summary?.id,
      status: row.status ?? row.summary?.status,
      resolutionMode: row.resolutionMode ?? row.summary?.resolutionMode,
    })),
  }));

const counts = {};
const cases = details.details.map((detail) => {
  const summary = detail.summary ?? {};
  const submitted = detail.submittedFields ?? {};
  const keys = Object.keys(submitted).sort();
  const feedbackOnly = keys.includes("note")
    && keys.filter((key) => key !== "note").every((key) => submitted[key] == null);
  let classification = "STRUCTURED_UPDATE_REVIEW";
  if (summary.resolutionMode === "NEW_CANDIDATE") classification = "NEW_PRODUCT_REVIEW";
  else if (feedbackOnly) classification = summary.status === "APPROVED"
    ? "FEEDBACK_ONLY_APPROVED"
    : "FEEDBACK_REVIEW_PENDING";
  counts[classification] = (counts[classification] ?? 0) + 1;
  return {
    id: summary.id,
    status: summary.status,
    resolutionMode: summary.resolutionMode,
    barcode: summary.barcode,
    linkedFoodItemId: detail.linkedFoodItemId,
    submittedFields: keys,
    classification,
    safeAutomaticCatalogMutation: false,
    reason: "Human review or structured evidence is required before any catalog write.",
  };
});

const report = {
  source: "READ_ONLY_AWS_PRODUCT_INTAKE_SNAPSHOT",
  catalogMutated: false,
  summary: {
    openCases: cases.length,
    safeAutomaticCatalogApply: 0,
    safeAutomaticPublish: 0,
    classificationCounts: Object.fromEntries(Object.entries(counts).sort()),
    duplicateBarcodeGroups: duplicateBarcodes.length,
  },
  decision: "No open case is eligible for automatic catalog mutation or publication. Feedback-only cases remain evidence/review records.",
  duplicateBarcodes,
  cases,
};

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
