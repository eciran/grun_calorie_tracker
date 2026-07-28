import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { AdminPromotionOperationsAnalytics } from "./types";

function readable(value?: string): string {
  return (value ?? "UNSPECIFIED").toLowerCase().split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
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
    graphic: visible.length ? undefined : {
      type: "text", left: "center", top: "middle",
      style: { text: "No data yet", fill: palette.muted, fontSize: 13 }
    },
    grid: { top: 10, right: 54, bottom: 24, left: 112 },
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: {
      type: "value", minInterval: 1,
      axisLabel: { color: palette.muted, formatter: compactNumber },
      splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
    },
    yAxis: {
      type: "category", data: visible.map((item) => readable(item.name)), inverse: true,
      axisLine: { show: false }, axisTick: { show: false },
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

export function PromotionLifecycleChart({ analytics }: { analytics: AdminPromotionOperationsAnalytics }) {
  const metrics = analytics.promotionStatuses ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Promotion lifecycle status distribution", metrics,
    { ACTIVE: "#2f8f68", DRAFT: "#8065a0", DEACTIVATED: "#7b7f88", EXPIRED: "#b7832f" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Promotion lifecycle chart" buildOption={buildOption} className="promotion-operations-echart" />;
}

export function PromotionTypeChart({ analytics }: { analytics: AdminPromotionOperationsAnalytics }) {
  const metrics = analytics.promotionTypes ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Promotion offer type distribution", metrics,
    { CAMPAIGN: "#8065a0", INTRO_OFFER: "#397ea8", WIN_BACK: "#b7832f", SUPPORT_GRANT: "#2f8f68" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Promotion offer type chart" buildOption={buildOption} className="promotion-operations-echart" />;
}
export function RedemptionOutcomeChart({ analytics }: { analytics: AdminPromotionOperationsAnalytics }) {
  const metrics = analytics.redemptionStatuses ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Promotion redemption outcome distribution", metrics,
    { CONVERTED: "#2f8f68", REJECTED: "#b63c4b", PROVIDER_VERIFIED: "#397ea8", RESERVED: "#b7832f" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Promotion redemption outcome chart" buildOption={buildOption} className="promotion-operations-echart" />;
}

export function PromotionRejectionChart({ analytics }: { analytics: AdminPromotionOperationsAnalytics }) {
  const metrics = analytics.rejectionCategories ?? [];
  const buildOption = useCallback(() => horizontalBarOption(
    "Sanitized promotion rejection category distribution", metrics,
    { LIMIT: "#b7832f", ELIGIBILITY: "#8065a0", PROVIDER: "#b63c4b", OTHER: "#7b7f88" }
  ), [metrics]);
  return <AdminEChart ariaLabel="Promotion rejection category chart" buildOption={buildOption} className="promotion-operations-echart" />;
}

export function PromotionRedemptionTrendChart({ analytics }: { analytics: AdminPromotionOperationsAnalytics }) {
  const points = analytics.redemptionTrend ?? [];
  const buildOption = useCallback((): EChartsCoreOption => {
    const hasActivity = points.some((item) => Number(item.attempts ?? 0) + Number(item.duplicateAttempts ?? 0) > 0);
    const palette = adminChartPalette();
    return {
      aria: { enabled: true, description: `Promotion redemption flow over ${analytics.windowDays ?? 30} days` },
      graphic: hasActivity ? undefined : {
        type: "text", left: "center", top: "middle",
        style: { text: "No redemption activity in this window", fill: palette.muted, fontSize: 13 }
      },
      grid: { top: 34, right: 24, bottom: 42, left: 52 },
      legend: { top: 0, textStyle: { color: palette.muted } },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category", data: points.map((item) => item.date ?? "-"),
        axisLabel: { color: palette.muted, hideOverlap: true },
        axisLine: { lineStyle: { color: palette.line } }
      },
      yAxis: {
        type: "value", minInterval: 1,
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: [
        {
          type: "bar", name: "Attempts", data: points.map((item) => Number(item.attempts ?? 0)),
          itemStyle: { color: "rgba(115, 78, 150, 0.28)", borderRadius: [3, 3, 0, 0] }, barMaxWidth: 24
        },
        {
          type: "line", name: "Converted", data: points.map((item) => Number(item.converted ?? 0)),
          smooth: true, symbolSize: 6, lineStyle: { width: 3, color: "#2f8f68" }, itemStyle: { color: "#2f8f68" }
        },
        {
          type: "line", name: "Rejected", data: points.map((item) => Number(item.rejected ?? 0)),
          smooth: true, symbolSize: 6, lineStyle: { width: 2, color: "#b63c4b" }, itemStyle: { color: "#b63c4b" }
        },
        {
          type: "line", name: "Duplicates", data: points.map((item) => Number(item.duplicateAttempts ?? 0)),
          smooth: true, symbolSize: 5, lineStyle: { width: 2, type: "dashed", color: "#b7832f" }, itemStyle: { color: "#b7832f" }
        }
      ]
    };
  }, [analytics.windowDays, points]);
  return <AdminEChart ariaLabel="Promotion redemption flow chart" buildOption={buildOption} className="promotion-redemption-trend-echart" />;
}
