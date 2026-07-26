import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const app = readFileSync(resolve("src/App.tsx"), "utf8");
const types = readFileSync(resolve("src/types.ts"), "utf8");

const required = [
  "/api/v1/admin/users?",
  "emailVerified",
  "ACTIVE_30_DAYS",
  "customer-360",
  "support-notes",
  "sessions/revoke",
  "confirmed: true",
  "Customer 360",
  "Support notes"
];

for (const marker of required) {
  if (!app.includes(marker)) {
    throw new Error(`Missing Customer 360 contract marker: ${marker}`);
  }
}

if (app.includes("/api/v1/admin/users/userList")) {
  throw new Error("Legacy unpaginated userList endpoint must not be used by admin UI.");
}

if (!types.includes("export type AdminCustomer360")) {
  throw new Error("AdminCustomer360 frontend contract is missing.");
}

if (app.includes("Body profile")) {
  throw new Error("Sensitive body profile data must not be exposed in Customer 360.");
}

console.log("Customer 360 admin UI contract verified.");
