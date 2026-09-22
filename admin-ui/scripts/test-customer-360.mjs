import { readAdminAppSource } from "./admin-app-source.mjs";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const app = readAdminAppSource();
const types = readFileSync(resolve("src/types.ts"), "utf8");

const required = [
  "/api/v1/admin/users?",
  "emailVerified",
  "ACTIVE_30_DAYS",
  "customer-360",
  "support-notes",
  "sessions/revoke",
  "/notifications",
  "IN_APP_AND_PUSH",
  "ANNOUNCEMENT",
  "customer-notification-history",
  "confirmed: true",
  "Customer 360",
  "Support notes"
  ,"userId: profile.id"
  ,"readUserRouteContext"
  ,'onNavigate("aiRequests", { userId: profile.id, userEmail: profile.email })'
  ,"readUsersRouteState"
  ,"usersRouteSearch"
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
