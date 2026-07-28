import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { SystemReliabilityAnalytics } from "./types";

function readable(value: string): string {
  return value.toLowerCase().replace(/_/g, " ").replace(/^./, (letter) => letter.toUpperCase());
}

function emptyGraphic(message: string): EChartsCoreOption["graphic"] {
  return [{ type: "text", left: "center", top: "middle", style: { text: message, fill: "#6f6878", fontSize: 13 } }];
}

export function ApiReliabilityChart({ analytics }: { analytics: SystemReliabilityAnalytics }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const points = analytics.apiTrend ?? [];
    return {
      aria: { enabled: true, description: "Hourly API request, error, and p95 latency trend" },
      color: [palette.accent, palette.danger, palette.primary],
      graphic: points.length ? undefined : emptyGraphic("No API traffic has been recorded in this process window."),
      grid: { top: 46, right: 66, bottom: 54, left: 54 },
      legend: { top: 4, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
      tooltip: { trigger: "axis" },
      xAxis: { type: "category", data: points.map((point) => new Intl.DateTimeFormat("en-GB", { weekday: "short", hour: "2-digit" }).format(new Date(point.bucket))), axisLabel: { color: palette.muted }, axisLine: { lineStyle: { color: palette.line } } },
      yAxis: [
        { type: "value", minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
        { type: "value", axisLabel: { color: palette.muted, formatter: "{value} ms" }, splitLine: { show: false } }
      ],
      series: [
        { type: "bar", name: "Requests", data: points.map((point) => point.requests), barMaxWidth: 28, itemStyle: { color: palette.accent, borderRadius: [3, 3, 0, 0] } },
        { type: "bar", name: "Errors", data: points.map((point) => point.errors), barMaxWidth: 28, itemStyle: { color: palette.danger, borderRadius: [3, 3, 0, 0] } },
        { type: "line", name: "p95 latency", yAxisIndex: 1, data: points.map((point) => point.latencyP95Ms), smooth: true, symbolSize: 6, lineStyle: { width: 2, color: palette.primary } }
      ]
    };
  }, [analytics]);
  return <AdminEChart ariaLabel="API reliability trend chart" buildOption={buildOption} className="reliability-echart" />;
}

export function InfrastructureReliabilityChart({ analytics }: { analytics: SystemReliabilityAnalytics }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const rows = analytics.infrastructure ?? [];
    return {
      aria: { enabled: true, description: "Database, Redis, JVM, and analytics cache reliability" },
      color: [palette.primary, palette.accent],
      grid: { top: 46, right: 62, bottom: 44, left: 62 },
      legend: { top: 4, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "category", data: rows.map((row) => row.component), axisLabel: { color: palette.muted, interval: 0 }, axisLine: { lineStyle: { color: palette.line } }, axisTick: { show: false } },
      yAxis: [
        { type: "value", name: "Latency", axisLabel: { color: palette.muted, formatter: "{value} ms" }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
        { type: "value", name: "Percent", max: 100, axisLabel: { color: palette.muted, formatter: "{value}%" }, splitLine: { show: false } }
      ],
      series: [
        { type: "bar", name: "Latency", data: rows.map((row) => row.latencyMs ?? 0), barMaxWidth: 38, itemStyle: { color: palette.primary, borderRadius: [4, 4, 0, 0] } },
        { type: "bar", name: "Utilization / hit rate", yAxisIndex: 1, data: rows.map((row) => row.utilizationPercent ?? 0), barMaxWidth: 38, itemStyle: { color: palette.accent, borderRadius: [4, 4, 0, 0] } }
      ]
    };
  }, [analytics]);
  return <AdminEChart ariaLabel="Infrastructure reliability chart" buildOption={buildOption} className="reliability-echart" />;
}

export function ProviderReliabilityChart({ analytics }: { analytics: SystemReliabilityAnalytics }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const rows = analytics.providers ?? [];
    return {
      aria: { enabled: true, description: "Aggregate provider success and failure outcomes" },
      color: [palette.accent, palette.danger],
      grid: { top: 46, right: 22, bottom: 42, left: 54 },
      legend: { top: 4, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "category", data: rows.map((row) => row.provider), axisLabel: { color: palette.muted }, axisLine: { lineStyle: { color: palette.line } }, axisTick: { show: false } },
      yAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
      series: [
        { type: "bar", name: "Succeeded", stack: "outcomes", data: rows.map((row) => row.successes), barMaxWidth: 44, itemStyle: { color: palette.accent } },
        { type: "bar", name: "Failed", stack: "outcomes", data: rows.map((row) => row.failures), barMaxWidth: 44, itemStyle: { color: palette.danger, borderRadius: [4, 4, 0, 0] } }
      ]
    };
  }, [analytics]);
  return <AdminEChart ariaLabel="Provider reliability outcome chart" buildOption={buildOption} className="reliability-echart" />;
}

export function OperationReliabilityChart({ analytics }: { analytics: SystemReliabilityAnalytics }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const rows = analytics.operations ?? [];
    return {
      aria: { enabled: true, description: "Scheduled job, incident, backup, and restore operation outcomes" },
      color: [palette.accent, palette.danger, "#a66a16", palette.primary],
      grid: { top: 46, right: 22, bottom: 58, left: 54 },
      legend: { top: 4, textStyle: { color: palette.muted }, itemWidth: 12, itemHeight: 8 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "category", data: rows.map((row) => readable(row.recordType)), axisLabel: { color: palette.muted, interval: 0, rotate: rows.length > 3 ? 16 : 0 }, axisLine: { lineStyle: { color: palette.line } }, axisTick: { show: false } },
      yAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
      series: [
        { type: "bar", name: "Succeeded", stack: "outcomes", data: rows.map((row) => row.succeeded) },
        { type: "bar", name: "Failed", stack: "outcomes", data: rows.map((row) => row.failed) },
        { type: "bar", name: "Dead letter", stack: "outcomes", data: rows.map((row) => row.deadLetters) },
        { type: "bar", name: "Open", stack: "outcomes", data: rows.map((row) => row.open), itemStyle: { borderRadius: [4, 4, 0, 0] } }
      ]
    };
  }, [analytics]);
  return <AdminEChart ariaLabel="Runtime operation outcome chart" buildOption={buildOption} className="reliability-echart" />;
}