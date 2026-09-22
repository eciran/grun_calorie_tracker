import assert from "node:assert/strict";
import { createServer, preview } from "vite";

for (const mode of ["dev", "preview"]) {
  const server = mode === "dev"
    ? await createServer({ server: { host: "127.0.0.1", port: 0 }, optimizeDeps: { noDiscovery: true, entries: [], include: [] } })
    : await preview({ preview: { host: "127.0.0.1", port: 0 } });
  try {
    if (mode === "dev") await server.listen();
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`;
    for (const route of ["/admin", "/admin/products", "/admin/reports/tracking/water", "/admin/users/"]) {
      const response = await fetch(origin + route);
      assert.equal(response.status, 200, `${mode} ${route}`);
      assert.match(await response.text(), /id="root"/);
    }
    assert.equal((await fetch(origin + "/admin/unknown")).status, 404);
    assert.equal((await fetch(origin + "/admin/assets/missing.js")).status, 404);
    assert.equal((await fetch(origin + "/admin/products", { method: "POST" })).status, 405);
    assert.equal((await fetch(origin + "/admin-ui/grun/grun-app-icon.svg")).status, 200);
  } finally {
    if (mode === "dev") await server.close();
    else await new Promise(resolve => server.httpServer.close(resolve));
  }
}
console.log("Direct page serving passed in Vite development and preview, including nested paths, 404s and asset URLs.");
