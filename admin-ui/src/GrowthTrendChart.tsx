import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { GrowthTrendPoint } from "./types";

function chartOptions(points: GrowthTrendPoint[]): EChartsCoreOption {
  const hasZoom = points.length > 30;
  const palette = adminChartPalette();

  return {
    aria: {
      enabled: true,
      description: "Daily registrations shown as bars and active users shown as a line."
    },
    color: [palette.primary, palette.accent],
    grid: {
      top: 42,
      right: 20,
      bottom: hasZoom ? 58 : 28,
      left: 46,
      containLabel: false
    },
    legend: {
      top: 0,
      right: 8,
      itemHeight: 9,
      itemWidth: 18,
      textStyle: { color: palette.muted, fontSize: 11 }
    },
    tooltip: {
      trigger: "axis",
      backgroundColor: palette.surface,
      borderColor: palette.line,
      borderWidth: 1,
      textStyle: { color: palette.text, fontSize: 12 },
      axisPointer: { type: "shadow", shadowStyle: { color: "rgba(107, 83, 139, 0.08)" } }
    },
    xAxis: {
      type: "category",
      data: points.map((point) => point.date),
      axisLine: { lineStyle: { color: palette.line } },
      axisTick: { show: false },
      axisLabel: {
        color: palette.muted,
        fontSize: 10,
        hideOverlap: true,
        formatter: (value: string) => value.slice(5)
      }
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: palette.muted, fontSize: 10 },
      splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
    },
    dataZoom: hasZoom
      ? [
          { type: "inside", start: 66, end: 100 },
          {
            type: "slider",
            start: 66,
            end: 100,
            height: 16,
            bottom: 8,
            borderColor: palette.line,
            fillerColor: "rgba(107, 83, 139, 0.16)",
            handleStyle: { color: palette.primary, borderColor: palette.primary },
            textStyle: { color: palette.muted, fontSize: 9 }
          }
        ]
      : [],
    series: [
      {
        name: "Registrations",
        type: "bar",
        data: points.map((point) => point.registrations),
        barMaxWidth: 18,
        itemStyle: { borderRadius: [3, 3, 0, 0] },
        emphasis: { focus: "series" }
      },
      {
        name: "Active users",
        type: "line",
        data: points.map((point) => point.activeUsers),
        smooth: 0.25,
        symbol: "circle",
        symbolSize: points.length > 30 ? 4 : 6,
        lineStyle: { width: 2.5 },
        areaStyle: { opacity: 0.08 },
        emphasis: { focus: "series" }
      }
    ]
  };
}

export function GrowthTrendChart({ points }: { points: GrowthTrendPoint[] }) {
  const buildOption = useCallback(() => chartOptions(points), [points]);
  return <AdminEChart ariaLabel="Daily registration and active user trend" buildOption={buildOption} className="growth-echart" />;
}
