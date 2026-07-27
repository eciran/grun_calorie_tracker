import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { AiMonitoringSummary } from "./types";

const OUTCOME_ORDER = ["DRAFT_CREATED", "CONFIRMED", "REJECTED", "FAILED"] as const;

function readable(value?: string): string {
  if (!value) return "Unknown";
  return value.toLowerCase().replace(/_/g, " ").replace(/^./, (letter) => letter.toUpperCase());
}

function compactNumber(value: unknown): string {
  const numeric = Number(value ?? 0);
  return new Intl.NumberFormat("en-GB", { notation: Math.abs(numeric) >= 10000 ? "compact" : "standard", maximumFractionDigits: 1 }).format(numeric);
}

export function AiOutcomeChart({ summary }: { summary: AiMonitoringSummary }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const metrics = summary.requestStatuses ?? [];
    const requestTypes = [...new Set(metrics.map((item) => item.requestType ?? "UNKNOWN"))]
      .sort((left, right) => {
        const total = (type: string) => metrics.filter((item) => (item.requestType ?? "UNKNOWN") === type).reduce((sum, item) => sum + Number(item.requestCount ?? 0), 0);
        return total(right) - total(left);
      })
      .slice(0, 8);
    const colors = [palette.primary, palette.accent, "#a66a16", palette.danger];

    return {
      aria: { enabled: true, description: "AI request outcomes grouped by controlled feature" },
      animationDuration: 420,
      color: colors,
      grid: { top: 46, right: 18, bottom: 78, left: 58 },
      legend: { top: 4, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: {
        type: "category",
        data: requestTypes.map(readable),
        axisLabel: { color: palette.muted, interval: 0, rotate: requestTypes.length > 4 ? 24 : 0, overflow: "truncate", width: 108 },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: OUTCOME_ORDER.map((status, index) => ({
        type: "bar",
        name: readable(status),
        stack: "outcomes",
        barMaxWidth: 34,
        data: requestTypes.map((requestType) => metrics
          .filter((item) => (item.requestType ?? "UNKNOWN") === requestType && item.status === status)
          .reduce((sum, item) => sum + Number(item.requestCount ?? 0), 0)),
        itemStyle: { color: colors[index] }
      }))
    };
  }, [summary]);

  return <AdminEChart ariaLabel="AI request outcome chart" buildOption={buildOption} className="ai-outcome-echart" />;
}

export function AiLatencyChart({ summary }: { summary: AiMonitoringSummary }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const values = [Number(summary.latencyP50Ms ?? 0), Number(summary.latencyP95Ms ?? 0), Number(summary.latencyP99Ms ?? 0)];
    return {
      aria: { enabled: true, description: "AI provider latency percentiles in milliseconds" },
      animationDuration: 420,
      grid: { top: 16, right: 62, bottom: 30, left: 62 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, valueFormatter: (value: unknown) => `${compactNumber(value)} ms` },
      xAxis: {
        type: "category",
        data: ["p50", "p95", "p99"],
        axisLabel: { color: palette.text },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        axisLabel: { color: palette.muted, formatter: (value: unknown) => `${compactNumber(value)} ms` },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: [{
        type: "bar",
        name: "Latency",
        data: values,
        barMaxWidth: 54,
        itemStyle: { color: (params: { dataIndex: number }) => [palette.accent, palette.primary, palette.danger][params.dataIndex], borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: "top", color: palette.text, formatter: ({ value }: { value: unknown }) => `${compactNumber(value)} ms` }
      }]
    };
  }, [summary]);

  return <AdminEChart ariaLabel="AI latency percentile chart" buildOption={buildOption} className="ai-latency-echart" />;
}

export function AiEconomicsChart({ summary }: { summary: AiMonitoringSummary }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const cost = summary.estimatedCostByCurrency ?? {};
    const revenue = summary.subscriptionRevenueByCurrency ?? {};
    const currencies = [...new Set([...Object.keys(cost), ...Object.keys(revenue)])].sort();
    return {
      aria: { enabled: true, description: "AI estimated cost and subscription revenue compared separately by currency" },
      animationDuration: 420,
      color: [palette.danger, palette.accent],
      grid: { top: 44, right: 24, bottom: 34, left: 72 },
      legend: { top: 4, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "shadow" },
        valueFormatter: (value: unknown) => Number(value ?? 0).toFixed(4)
      },
      xAxis: {
        type: "category",
        data: currencies,
        axisLabel: { color: palette.text },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: [
        { type: "bar", name: "AI estimated cost", data: currencies.map((currency) => Number(cost[currency] ?? 0)), barMaxWidth: 46, itemStyle: { color: palette.danger, borderRadius: [4, 4, 0, 0] } },
        { type: "bar", name: "Subscription revenue", data: currencies.map((currency) => Number(revenue[currency] ?? 0)), barMaxWidth: 46, itemStyle: { color: palette.accent, borderRadius: [4, 4, 0, 0] } }
      ]
    };
  }, [summary]);

  return <AdminEChart ariaLabel="AI cost and subscription revenue chart" buildOption={buildOption} className="ai-economics-echart" />;
}