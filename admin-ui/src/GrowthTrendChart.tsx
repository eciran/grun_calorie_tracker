import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { GrowthTrendPoint } from "./types";

function chartOptions(points: GrowthTrendPoint[], locale: "tr" | "en", metric: "both" | "registrations" | "active"): EChartsCoreOption {
  const hasZoom = points.length > 30;
  const palette = adminChartPalette();

  return {
    aria: {
      enabled: true,
      description: locale === "tr"
        ? "Seçilen dönemdeki günlük kayıt ve aktif kullanıcı eğilimi."
        : "Daily registration and active user trend for the selected period."
    },
    textStyle: { fontFamily: palette.fontFamily },
    color: [palette.primary, palette.accent],
    grid: {
      top: 42,
      right: 20,
      bottom: hasZoom ? 58 : 28,
      left: 46,
      containLabel: false
    },
    legend: {
      show: metric === "both",
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
      ...(metric === "both" || metric === "registrations" ? [
      {
        name: locale === "tr" ? "Kayıtlar" : "Registrations",
        type: "bar",
        data: points.map((point) => point.registrations),
        barMaxWidth: 18,
        itemStyle: { borderRadius: [3, 3, 0, 0] },
        emphasis: { focus: "series" }
      } as const] : []),
      ...(metric === "both" || metric === "active" ? [
      {
        name: locale === "tr" ? "Aktif kullanıcılar" : "Active users",
        type: "line",
        data: points.map((point) => point.activeUsers),
        smooth: 0.25,
        symbol: "circle",
        symbolSize: points.length > 30 ? 4 : 6,
        lineStyle: { width: 2.5 },
        areaStyle: { opacity: 0.08 },
        emphasis: { focus: "series" }
      } as const] : [])
    ]
  };
}

export function GrowthTrendChart({ points, locale = "en", metric = "both", onPointSelect }: { points: GrowthTrendPoint[]; locale?: "tr" | "en"; metric?: "both" | "registrations" | "active"; onPointSelect?: (point: GrowthTrendPoint) => void }) {
  const buildOption = useCallback(() => chartOptions(points, locale, metric), [points, locale, metric]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Günlük kayıt ve aktif kullanıcı eğilimi" : "Daily registration and active user trend"} buildOption={buildOption} className="growth-echart" dataPointCount={points.length} onDataPointClick={onPointSelect ? index => points[index] && onPointSelect(points[index]) : undefined} />;
}
