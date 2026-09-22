import fs from "node:fs";

// Legacy contracts previously inspected the monolithic App. Keep that exact
// migrated surface covered; architectural behavior is tested separately.
export function readAdminAppSource() {
  const paths = ["App.tsx", "admin/navigation.tsx", "admin/shared.tsx", ...fs.readdirSync(new URL("../src/pages/", import.meta.url)).filter(name => name.endsWith(".tsx")).sort().map(name => `pages/${name}`)];
  return paths.map(path => fs.readFileSync(new URL(`../src/${path}`, import.meta.url), "utf8")).join("\n");
}
