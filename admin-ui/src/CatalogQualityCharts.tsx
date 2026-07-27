import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { AdminCatalogQualityAnalytics } from "./types";

function readable(value?: string): string {
  return (value ?? "Unspecified")
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function compactNumber(value: unknown): string {
  const numeric = Number(value ?? 0);
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(numeric);
}

export function CatalogVerificationChart({ analytics }: { analytics: AdminCatalogQualityAnalytics }) {
  const metrics = [...(analytics.verificationStatuses ?? [])]
    .filter((item) => Number(item.count ?? 0) > 0)
    .sort((left, right) => Number(right.count ?? 0) - Number(left.count ?? 0));
  const colors: Record<string, string> = {
    VERIFIED: "#2f8f68",
    NEEDS_REVIEW: "#b7832f",
    RAW_IMPORTED: "#8065a0",
    REJECTED: "#b63c4b",
    UNSPECIFIED: "#847c89"
  };
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true, description: "Food catalog verification status distribution" },
      grid: { top: 12, right: 58, bottom: 26, left: 108 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      yAxis: {
        type: "category",
        data: metrics.map((item) => readable(item.name)),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: palette.text }
      },
      series: [{
        type: "bar",
        name: "Products",
        data: metrics.map((item) => ({
          value: Number(item.count ?? 0),
          itemStyle: { color: colors[item.name ?? ""] ?? palette.primary, borderRadius: [0, 4, 4, 0] }
        })),
        barMaxWidth: 28,
        label: { show: true, position: "right", color: palette.text, formatter: ({ value }: { value: unknown }) => compactNumber(value) }
      }]
    };
  }, [metrics]);

  return <AdminEChart ariaLabel="Food catalog verification status chart" buildOption={buildOption} className="catalog-quality-echart" />;
}

export function CatalogIssueChart({ analytics }: { analytics: AdminCatalogQualityAnalytics }) {
  const metrics = [...(analytics.openIssueTypes ?? [])]
    .filter((item) => Number(item.count ?? 0) > 0)
    .sort((left, right) => Number(right.count ?? 0) - Number(left.count ?? 0))
    .slice(0, 8);
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true, description: "Most frequent open catalog quality issue types" },
      grid: { top: 12, right: 58, bottom: 26, left: 154 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      yAxis: {
        type: "category",
        data: metrics.map((item) => readable(item.name)),
        inverse: true,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: palette.text, width: 142, overflow: "truncate" }
      },
      series: [{
        type: "bar",
        name: "Open issues",
        data: metrics.map((item) => Number(item.count ?? 0)),
        barMaxWidth: 24,
        itemStyle: { color: palette.primary, borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: "right", color: palette.text, formatter: ({ value }: { value: unknown }) => compactNumber(value) }
      }]
    };
  }, [metrics]);

  return <AdminEChart ariaLabel="Open catalog quality issue chart" buildOption={buildOption} className="catalog-quality-echart" />;
}

export function CatalogScanTrendChart({ analytics }: { analytics: AdminCatalogQualityAnalytics }) {
  const points = analytics.scanTrend ?? [];
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true, description: `Catalog quality scan outcomes over ${analytics.windowDays ?? 30} days` },
      color: [palette.primary, "#2f8f68", palette.accent, palette.danger],
      grid: { top: 42, right: 28, bottom: 42, left: 52 },
      legend: { top: 4, textStyle: { color: palette.muted } },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category",
        data: points.map((item) => item.date ?? "-"),
        axisLabel: { color: palette.muted, hideOverlap: true },
        axisLine: { lineStyle: { color: palette.line } }
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: [
        { type: "bar", name: "Scanned", data: points.map((item) => Number(item.scannedProducts ?? 0)), barMaxWidth: 24 },
        { type: "line", name: "Validated", data: points.map((item) => Number(item.validatedProducts ?? 0)), smooth: true, symbolSize: 6 },
        { type: "line", name: "Suggestions", data: points.map((item) => Number(item.createdSuggestions ?? 0)), smooth: true, symbolSize: 6 },
        { type: "line", name: "Failed runs", data: points.map((item) => Number(item.failedRuns ?? 0)), symbolSize: 6 }
      ]
    };
  }, [analytics.windowDays, points]);

  return <AdminEChart ariaLabel="Catalog quality scan trend chart" buildOption={buildOption} className="catalog-quality-trend-echart" />;
}
