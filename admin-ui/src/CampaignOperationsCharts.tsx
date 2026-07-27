import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { NotificationCampaignSummary } from "./types";

function readable(value?: string): string {
  if (!value) return "Unknown";
  return value.toLowerCase().replace(/_/g, " ").replace(/^./, (letter) => letter.toUpperCase());
}

function compactNumber(value: unknown): string {
  return new Intl.NumberFormat("en-GB", {
    notation: Math.abs(Number(value ?? 0)) >= 10000 ? "compact" : "standard",
    maximumFractionDigits: 1
  }).format(Number(value ?? 0));
}

export function CampaignDeliveryChart({ summary }: { summary: NotificationCampaignSummary }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const items = [
      { name: "Delivered", value: Number(summary.deliveredCount ?? 0), color: palette.accent },
      { name: "Suppressed", value: Number(summary.suppressedCount ?? 0), color: "#a66a16" },
      { name: "Failed", value: Number(summary.failedRecipientCount ?? 0), color: palette.danger }
    ];
    return {
      aria: { enabled: true, description: "Notification recipient delivery outcomes" },
      grid: { top: 12, right: 62, bottom: 26, left: 94 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: palette.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      yAxis: {
        type: "category",
        data: items.map((item) => item.name),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: palette.text }
      },
      series: [{
        type: "bar",
        name: "Recipients",
        data: items.map((item) => ({ value: item.value, itemStyle: { color: item.color, borderRadius: [0, 4, 4, 0] } })),
        barMaxWidth: 30,
        label: { show: true, position: "right", color: palette.text, formatter: ({ value }: { value: unknown }) => compactNumber(value) }
      }]
    };
  }, [summary]);

  return <AdminEChart ariaLabel="Campaign recipient delivery outcome chart" buildOption={buildOption} className="campaign-delivery-echart" />;
}

export function CampaignEngagementFunnel({ summary }: { summary: NotificationCampaignSummary }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const items = [
      { name: "Delivered", value: Number(summary.deliveredCount ?? 0) },
      { name: "Opened", value: Number(summary.openedCount ?? 0) },
      { name: "Clicked", value: Number(summary.clickedCount ?? 0) },
      { name: "Converted", value: Number(summary.convertedCount ?? 0) }
    ];
    return {
      aria: { enabled: true, description: "Campaign engagement from delivery to conversion" },
      color: [palette.primary, "#8065a0", palette.accent, "#2f8f68"],
      tooltip: { trigger: "item", formatter: "{b}: {c}" },
      series: [{
        type: "funnel",
        left: "8%",
        right: "8%",
        top: 8,
        bottom: 8,
        minSize: "20%",
        maxSize: "100%",
        sort: "none",
        gap: 3,
        label: { show: true, position: "inside", color: "#ffffff", fontSize: 11, formatter: "{b}  {c}" },
        labelLine: { show: false },
        itemStyle: { borderColor: palette.surface, borderWidth: 1, borderRadius: 3 },
        emphasis: { focus: "self" },
        data: items
      }]
    };
  }, [summary]);

  return <AdminEChart ariaLabel="Campaign engagement funnel" buildOption={buildOption} className="campaign-engagement-echart" />;
}

export function CampaignStatusChart({ summary }: { summary: NotificationCampaignSummary }) {
  const metrics = [...(summary.campaignStatuses ?? [])]
    .filter((item) => Number(item.count ?? 0) > 0)
    .sort((left, right) => Number(right.count ?? 0) - Number(left.count ?? 0));
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true, description: "Campaign count grouped by lifecycle status" },
      grid: { top: 12, right: 56, bottom: 26, left: 104 },
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
        axisLabel: { color: palette.text, width: 92, overflow: "truncate" }
      },
      series: [{
        type: "bar",
        name: "Campaigns",
        data: metrics.map((item) => Number(item.count ?? 0)),
        barMaxWidth: 24,
        itemStyle: { color: palette.primary, borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: "right", color: palette.text, formatter: ({ value }: { value: unknown }) => compactNumber(value) }
      }]
    };
  }, [metrics]);

  return <AdminEChart ariaLabel="Campaign lifecycle status chart" buildOption={buildOption} className="campaign-status-echart" />;
}