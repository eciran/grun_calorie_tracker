import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { performance } from "node:perf_hooks";

export function percentile(values, ratio) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1);
  return Math.round(sorted[Math.max(0, index)] * 100) / 100;
}

export async function runAnalyticsLoad(options) {
  const {
    baseUrl,
    token,
    paths,
    requestCount,
    concurrency,
    p95BudgetMs,
    maxErrorRate,
    fetchImpl = fetch,
  } = options;
  if (!token) throw new Error("GRUN_LOAD_TEST_TOKEN is required.");
  if (!Array.isArray(paths) || paths.length === 0) throw new Error("At least one analytics path is required.");
  if (requestCount < 1 || concurrency < 1) throw new Error("Requests and concurrency must be positive.");

  const headers = { Authorization: `Bearer ${token}`, Accept: "application/json" };
  for (const endpoint of paths) {
    const response = await fetchImpl(new URL(endpoint, baseUrl), { headers });
    if (!response.ok) throw new Error(`Warm-up failed for ${endpoint}: HTTP ${response.status}`);
    await response.arrayBuffer();
  }

  const results = new Array(requestCount);
  let nextIndex = 0;
  const startedAt = performance.now();
  async function worker() {
    while (true) {
      const index = nextIndex++;
      if (index >= requestCount) return;
      const endpoint = paths[index % paths.length];
      const requestStartedAt = performance.now();
      try {
        const response = await fetchImpl(new URL(endpoint, baseUrl), { headers });
        await response.arrayBuffer();
        results[index] = {
          endpoint,
          status: response.status,
          ok: response.ok,
          durationMs: performance.now() - requestStartedAt,
        };
      } catch (error) {
        results[index] = {
          endpoint,
          status: 0,
          ok: false,
          durationMs: performance.now() - requestStartedAt,
          error: error instanceof Error ? error.message : String(error),
        };
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, requestCount) }, () => worker()));
  const elapsedMs = performance.now() - startedAt;
  const durations = results.map((result) => result.durationMs);
  const failures = results.filter((result) => !result.ok);
  const errorRate = failures.length / requestCount;
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl,
    paths,
    requestCount,
    concurrency,
    elapsedMs: Math.round(elapsedMs * 100) / 100,
    throughputPerSecond: Math.round((requestCount * 1000 / elapsedMs) * 100) / 100,
    latencyMs: {
      p50: percentile(durations, 0.50),
      p95: percentile(durations, 0.95),
      p99: percentile(durations, 0.99),
      max: Math.round(Math.max(...durations) * 100) / 100,
    },
    failures: failures.length,
    errorRate: Math.round(errorRate * 10000) / 10000,
    thresholds: { p95BudgetMs, maxErrorRate },
    passed: failures.length / requestCount <= maxErrorRate && percentile(durations, 0.95) <= p95BudgetMs,
    statusCounts: Object.fromEntries(
      [...new Set(results.map((result) => result.status))]
        .sort((a, b) => a - b)
        .map((status) => [status, results.filter((result) => result.status === status).length]),
    ),
  };
  return report;
}

function parseArgs(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) values.set(argv[index], argv[index + 1]);
  const today = new Date();
  const end = today.toISOString().slice(0, 10);
  const startDate = new Date(today);
  startDate.setUTCDate(startDate.getUTCDate() - 29);
  const start = startDate.toISOString().slice(0, 10);
  return {
    baseUrl: values.get("--base-url") ?? "http://localhost:8080",
    token: process.env.GRUN_LOAD_TEST_TOKEN,
    paths: (values.get("--paths") ?? `/api/v1/dashboard/daily-summary?date=${end},/api/v1/progress/analytics/basic?start=${start}&end=${end}`)
      .split(",").map((value) => value.trim()).filter(Boolean),
    requestCount: Number(values.get("--requests") ?? 200),
    concurrency: Number(values.get("--concurrency") ?? 20),
    p95BudgetMs: Number(values.get("--p95-budget-ms") ?? 300),
    maxErrorRate: Number(values.get("--max-error-rate") ?? 0),
    reportPath: values.get("--report") ?? "target/reports/user-analytics-cache-load.json",
  };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const report = await runAnalyticsLoad(options);
  await fs.mkdir(path.dirname(options.reportPath), { recursive: true });
  await fs.writeFile(options.reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  process.stdout.write(`ANALYTICS_CACHE_LOAD_RESULT ${JSON.stringify(report)}\n`);
  if (!report.passed) process.exitCode = 1;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) main().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
});