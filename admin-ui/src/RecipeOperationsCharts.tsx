import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { AdminRecipeOperationsAnalytics } from "./types";

function readable(value?: string): string {
  const raw = value ?? "UNSPECIFIED";
  const ageRange = raw.match(/^(\d+)_(\d+)_DAYS$/);
  if (ageRange) return `${ageRange[1]}-${ageRange[2]} days`;
  const openAgeRange = raw.match(/^(\d+)_PLUS_DAYS$/);
  if (openAgeRange) return `${openAgeRange[1]}+ days`;
  return raw
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function compactNumber(value: unknown): string {
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(Number(value ?? 0));
}

function horizontalBarOption(
  description: string,
  metrics: Array<{ name?: string; count?: number }>,
  colors: Record<string, string>
): EChartsCoreOption {
  const palette = adminChartPalette();
  const visible = metrics.filter((item) => Number(item.count ?? 0) > 0);
  return {
    aria: { enabled: true, description },
    grid: { top: 10, right: 54, bottom: 24, left: 112 },
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: palette.muted, formatter: compactNumber },
      splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
    },
    yAxis: {
      type: "category",
      data: visible.map((item) => readable(item.name)),
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: palette.text, width: 104, overflow: "truncate" }
    },
    series: [{
      type: "bar",
      data: visible.map((item) => ({
        value: Number(item.count ?? 0),
        itemStyle: { color: colors[item.name ?? ""] ?? palette.primary, borderRadius: [0, 4, 4, 0] }
      })),
      barMaxWidth: 25,
      label: { show: true, position: "right", color: palette.text, formatter: ({ value }: { value: unknown }) => compactNumber(value) }
    }]
  };
}

export function RecipeModerationChart({ analytics }: { analytics: AdminRecipeOperationsAnalytics }) {
  const metrics = analytics.verificationStatuses ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Recipe verification status distribution",
    metrics,
    { VERIFIED: "#2f8f68", NEEDS_REVIEW: "#b7832f", RAW_IMPORTED: "#8065a0", REJECTED: "#b63c4b" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Recipe verification status chart" buildOption={buildOption} className="recipe-operations-echart" />;
}

export function RecipeBacklogChart({ analytics }: { analytics: AdminRecipeOperationsAnalytics }) {
  const metrics = analytics.pendingAgeBands ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Pending recipe review backlog grouped by age",
    metrics,
    { "0_2_DAYS": "#2f8f68", "3_7_DAYS": "#b7832f", "8_30_DAYS": "#bf6a35", "31_PLUS_DAYS": "#b63c4b" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Recipe review backlog age chart" buildOption={buildOption} className="recipe-operations-echart" />;
}

export function RecipeImportPipelineChart({ analytics }: { analytics: AdminRecipeOperationsAnalytics }) {
  const metrics = analytics.importStatuses ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Recipe import candidate status distribution",
    metrics,
    { APPROVED: "#2f8f68", PENDING: "#b7832f", REJECTED: "#b63c4b", FAILED: "#8a4050" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Recipe import candidate status chart" buildOption={buildOption} className="recipe-operations-echart" />;
}

export function RecipeSubmissionTrendChart({ analytics }: { analytics: AdminRecipeOperationsAnalytics }) {
  const points = analytics.submissionTrend ?? [];
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true, description: `Recipe submissions over ${analytics.windowDays ?? 30} days` },
      grid: { top: 20, right: 24, bottom: 42, left: 52 },
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
      series: [{
        type: "line",
        name: "Created recipes",
        data: points.map((item) => Number(item.createdRecipes ?? 0)),
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 3, color: palette.primary },
        itemStyle: { color: palette.primary },
        areaStyle: { color: "rgba(115, 78, 150, 0.10)" }
      }]
    };
  }, [analytics.windowDays, points]);
  return <AdminEChart ariaLabel="Recipe submission trend chart" buildOption={buildOption} className="recipe-submission-trend-echart" />;
}