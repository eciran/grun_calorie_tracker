import assert from "node:assert/strict";
import fs from "node:fs";
import ts from "typescript";
const source = fs.readFileSync(new URL("../src/admin/navigation.tsx", import.meta.url), "utf8");
const paths = JSON.parse(fs.readFileSync(new URL("../public/routes.json", import.meta.url), "utf8"));
const js = ts.transpileModule(source.replace('import routePaths from "../../public/routes.json";', `const routePaths = ${JSON.stringify(paths)};`), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText;
const nav = await import(`data:text/javascript;base64,${Buffer.from(js).toString("base64")}`);
const { sections, navigation, canViewSection, permissionForSection, filterNavigationByAccess, isSectionKey, sectionFromLocation, sectionHref, readUserRouteContext } = nav;
const routes = sections.map(item => item.key);
assert.equal(routes.length, 59);
assert.equal(new Set(routes).size, routes.length);
assert.deepEqual(Object.keys(paths).sort(), [...routes].sort());
assert.equal(new Set(Object.values(paths)).size, routes.length);
assert.equal(sectionHref("subscriptionAccess", { userId: 42, userEmail: "user+test@example.com" }), "/admin/subscriptions/access?userId=42&userEmail=user%2Btest%40example.com");
assert.deepEqual(readUserRouteContext("?userId=42&userEmail=user%2Btest%40example.com"), { userId: 42, userEmail: "user+test@example.com" });
for (const invalidContext of ["", "?userId=0", "?userId=-1", "?userId=text", "?userId=1.5"]) assert.equal(readUserRouteContext(invalidContext), null);
const menuRoutes = navigation.flatMap(item => item.children?.map(child => child.key) ?? [item.key]);
const compatibilityOnlyRoutes = [
  "foodImports", "foodRegions", "productDuplicates", "productNutrition", "productRejected",
  "subscriptionEntitlements", "brevoSenders", "mailEvents", "integrationProviders", "settings",
  "systemRuntime", "systemDatabase", "systemProviders", "trackingWater", "trackingFasting", "trackingSteps"
];
assert.deepEqual([...menuRoutes].sort(), routes.filter(route => !compatibilityOnlyRoutes.includes(route)).sort(), "Consolidated workspaces have one sidebar entry while legacy routes remain addressable.");
for (const invalid of ["__proto__", "constructor", "toString", "", undefined, "unknown", "admin-main"]) {
  assert.equal(isSectionKey(invalid), false);
  globalThis.window = { location: { hash: `#/${invalid ?? ""}`, pathname: "/admin-ui/" } };
  assert.equal(sectionFromLocation(), "dashboard");
}
const canonicalSection = (route) => {
  if (["trackingWater", "trackingFasting", "trackingSteps"].includes(route)) return "tracking";
  if (["systemRuntime", "systemDatabase", "systemProviders"].includes(route)) return "system";
  if (route === "settings") return "systemProduction";
  if (route === "integrationProviders") return "integrations";
  if (route === "subscriptionEntitlements") return "subscriptionMapping";
  if (route === "brevoSenders" || route === "mailEvents") return "pushDelivery";
  return route;
};
for (const route of routes) {
  globalThis.window = { location: { hash: `#/${route}`, pathname: "/admin-ui/" } };
  assert.equal(sectionFromLocation(), route);
  globalThis.window = { location: { hash: "", pathname: paths[route] } };
  assert.equal(sectionFromLocation(), canonicalSection(route));
  globalThis.window.location.pathname += "/";
  assert.equal(sectionFromLocation(), canonicalSection(route));
  assert.equal(canViewSection(null, route), false, "No pages before access profile resolves.");
  assert.equal(canViewSection({ permissions: [] }, route), false);
  assert.equal(canViewSection({ mfaRequired: true, mfaEnabled: false }, route), route === "admins");
}
for (const permission of new Set(routes.map(permissionForSection))) {
  const profile = { role: "OWNER", permissions: [permission] };
  const visible = filterNavigationByAccess(navigation, profile).flatMap(item => item.children?.map(child => child.key) ?? [item.key]);
  assert.deepEqual(visible.sort(), routes.filter(route => !compatibilityOnlyRoutes.includes(route) && permissionForSection(route) === permission).sort(), `${permission} sees only permitted workspace entries, including children of mixed-permission groups.`);
}
const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
assert.ok([...app.matchAll(/const \w+ = lazy\(/g)].length >= 33);
for (const role of ["ADMIN", "ADMIN_TECHNICAL", "ADMIN_READ_ONLY", "USER"]) assert.equal(canViewSection({ role, permissions: ["TECHNICAL_READ"] }, "errors"), false);
assert.equal(canViewSection({ role: "OWNER", permissions: ["TECHNICAL_READ"] }, "errors"), true);
for (const role of ["ADMIN", "ADMIN_TECHNICAL", "ADMIN_READ_ONLY", "USER"]) assert.equal(canViewSection({ role, permissions: ["TECHNICAL_READ"] }, "ownerAlerts"), false);
assert.equal(canViewSection({ role: "OWNER", permissions: ["TECHNICAL_READ"] }, "ownerAlerts"), true);
for (const role of ["ADMIN", "ADMIN_FINANCE", "ADMIN_READ_ONLY", "USER"]) assert.equal(canViewSection({ role, permissions: ["ADMIN_TEAM_READ"] }, "approvals"), false);
assert.equal(canViewSection({ role: "OWNER", permissions: ["ADMIN_TEAM_READ"] }, "approvals"), true);
assert.doesNotMatch(app, /import \{[^}]*\b[A-Z]\w*View\b[^}]*\} from/);
assert.match(app, /!canViewSection\(accessProfile, active\)[\s\S]*<PageBoundary[\s\S]*<Suspense[\s\S]*<DashboardView/);
assert.match(app, /setTargetContext\(null\);[\s\S]*setActive\(fallback\)/);
console.log(`V2 navigation passed: ${routes.length} routes, integrated user verification, split AI workspaces, exhaustive menu/permission coverage and lazy page modules.`);
