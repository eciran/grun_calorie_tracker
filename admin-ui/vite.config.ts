import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import routes from "./public/routes.json";
import type { IncomingMessage, ServerResponse } from "node:http";

function adminPageMiddleware(request: IncomingMessage, response: ServerResponse, next: () => void) {
  const path = (request.url ?? "").split("?")[0].replace(/\/$/, "");
  if (path === "/admin" || path.startsWith("/admin/")) {
    if (!Object.values(routes).includes(path)) { response.statusCode = 404; response.end("Not found"); return; }
    if (request.method !== "GET" && request.method !== "HEAD") { response.statusCode = 405; response.end(); return; }
    request.url = "/admin-ui/index.html";
  }
  next();
}

export default defineConfig({
  base: "/admin-ui/",
  plugins: [react(), {
    name: "admin-page-routes",
    configureServer(server) {
      server.middlewares.use(adminPageMiddleware);
    },
    configurePreviewServer(server) {
      server.middlewares.use(adminPageMiddleware);
    }
  }],
  build: {
    outDir: "../src/main/resources/static/admin-ui",
    emptyOutDir: true,
    target: "es2022"
  },
  server: {
    host: "192.168.1.96",
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
