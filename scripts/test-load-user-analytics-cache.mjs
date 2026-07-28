import assert from "node:assert/strict";
import http from "node:http";
import test from "node:test";
import { percentile, runAnalyticsLoad } from "./load-user-analytics-cache.mjs";

test("percentile uses the nearest-rank result", () => {
  assert.equal(percentile([1, 2, 3, 4, 5], 0.95), 5);
});

test("load runner reports successful concurrent requests without exposing token", async () => {
  const server = http.createServer((request, response) => {
    assert.equal(request.headers.authorization, "Bearer secret-test-token");
    response.writeHead(200, { "content-type": "application/json" });
    response.end("{}");
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    const address = server.address();
    const report = await runAnalyticsLoad({
      baseUrl: `http://127.0.0.1:${address.port}`,
      token: "secret-test-token",
      paths: ["/analytics"],
      requestCount: 20,
      concurrency: 5,
      p95BudgetMs: 1000,
      maxErrorRate: 0,
    });
    assert.equal(report.passed, true);
    assert.equal(report.failures, 0);
    assert.equal(report.statusCounts[200], 20);
    assert.equal(JSON.stringify(report).includes("secret-test-token"), false);
  } finally {
    await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
});