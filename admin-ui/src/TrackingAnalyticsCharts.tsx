import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import { AdminTrackingTrendPoint } from "./types";

type MetricKey = "waterLogs" | "fastingSessions" | "stepRecords";
type ValueKey = "waterMl" | "fastingMinutes" | "steps";
const dateLabel = (value?: string, tr = false) => value ? new Intl.DateTimeFormat(tr ? "tr-TR" : "en-GB", { day: "2-digit", month: "short" }).format(new Date(`${value}T12:00:00`)) : "-";

export function TrackingActivityChart({ trends, tr }: { trends: AdminTrackingTrendPoint[]; tr: boolean }) {
  const buildOption = useCallback((): EChartsCoreOption => { const p = adminChartPalette(); return {
    color: [p.primary, p.accent, p.warning], tooltip: { trigger: "axis", backgroundColor: p.surface, borderColor: p.line, textStyle: { color: p.text, fontFamily: p.fontFamily } },
    legend: { top: 0, right: 0, textStyle: { color: p.muted, fontFamily: p.fontFamily } }, grid: { left: 44, right: 18, top: 44, bottom: 28, containLabel: true },
    xAxis: { type: "category", boundaryGap: false, data: trends.map(item => dateLabel(item.date, tr)), axisLine: { lineStyle: { color: p.line } }, axisLabel: { color: p.muted, fontFamily: p.fontFamily } },
    yAxis: { type: "value", minInterval: 1, axisLabel: { color: p.muted, fontFamily: p.fontFamily }, splitLine: { lineStyle: { color: p.line, type: "dashed" } } },
    series: [
      { name: tr ? "Su kayıtları" : "Water logs", type: "line", smooth: .35, symbolSize: 6, areaStyle: { opacity: .08 }, data: trends.map(item => item.waterLogs ?? 0) },
      { name: tr ? "Oruç oturumları" : "Fasting sessions", type: "line", smooth: .35, symbolSize: 6, areaStyle: { opacity: .07 }, data: trends.map(item => item.fastingSessions ?? 0) },
      { name: tr ? "Adım kayıtları" : "Step records", type: "line", smooth: .35, symbolSize: 6, areaStyle: { opacity: .06 }, data: trends.map(item => item.stepRecords ?? 0) }
    ]
  }; }, [tr, trends]);
  return <AdminEChart ariaLabel={tr ? "Takip etkinliği zaman grafiği" : "Tracking activity timeline"} buildOption={buildOption} className="tracking-overview-chart" />;
}

export function TrackingAdoptionChart({ trends, tr }: { trends: AdminTrackingTrendPoint[]; tr: boolean }) {
  const buildOption = useCallback((): EChartsCoreOption => { const p = adminChartPalette(); return {
    color: [p.primary, p.accent, p.warning], tooltip: { trigger: "axis", backgroundColor: p.surface, borderColor: p.line, textStyle: { color: p.text, fontFamily: p.fontFamily } },
    legend: { bottom: 0, textStyle: { color: p.muted, fontFamily: p.fontFamily } }, grid: { left: 38, right: 14, top: 12, bottom: 45, containLabel: true },
    xAxis: { type: "category", data: trends.map(item => dateLabel(item.date, tr)), axisLabel: { show: false }, axisLine: { lineStyle: { color: p.line } } },
    yAxis: { type: "value", minInterval: 1, axisLabel: { color: p.muted, fontFamily: p.fontFamily }, splitLine: { lineStyle: { color: p.line, type: "dashed" } } },
    series: [
      { name: tr ? "Su kullanıcıları" : "Water users", type: "bar", stack: "users", barMaxWidth: 18, data: trends.map(item => item.waterUsers ?? 0) },
      { name: tr ? "Oruç kullanıcıları" : "Fasting users", type: "bar", stack: "users", barMaxWidth: 18, data: trends.map(item => item.fastingUsers ?? 0) },
      { name: tr ? "Adım kullanıcıları" : "Step users", type: "bar", stack: "users", barMaxWidth: 18, data: trends.map(item => item.stepUsers ?? 0) }
    ]
  }; }, [tr, trends]);
  return <AdminEChart ariaLabel={tr ? "Günlük aktif kullanıcı dağılımı" : "Daily active user distribution"} buildOption={buildOption} className="tracking-adoption-chart" />;
}

export function TrackingModuleChart({ trends, tr, valueKey, activityKey, valueLabel, activityLabel, color }: { trends: AdminTrackingTrendPoint[]; tr: boolean; valueKey: ValueKey; activityKey: MetricKey; valueLabel: string; activityLabel: string; color: "primary" | "accent" | "warning" }) {
  const buildOption = useCallback((): EChartsCoreOption => { const p = adminChartPalette(); const lineColor = p[color]; return {
    color: [lineColor, p.muted], tooltip: { trigger: "axis", backgroundColor: p.surface, borderColor: p.line, textStyle: { color: p.text, fontFamily: p.fontFamily } },
    legend: { top: 0, right: 0, textStyle: { color: p.muted, fontFamily: p.fontFamily } }, grid: { left: 42, right: 42, top: 42, bottom: 28, containLabel: true },
    xAxis: { type: "category", boundaryGap: false, data: trends.map(item => dateLabel(item.date, tr)), axisLine: { lineStyle: { color: p.line } }, axisLabel: { color: p.muted, fontFamily: p.fontFamily, hideOverlap: true } },
    yAxis: [{ type: "value", axisLabel: { color: p.muted, fontFamily: p.fontFamily }, splitLine: { lineStyle: { color: p.line, type: "dashed" } } }, { type: "value", minInterval: 1, axisLabel: { show: false }, splitLine: { show: false } }],
    series: [
      { name: valueLabel, type: "line", smooth: .35, symbolSize: 5, areaStyle: { opacity: .09 }, data: trends.map(item => item[valueKey] ?? 0) },
      { name: activityLabel, type: "bar", yAxisIndex: 1, barMaxWidth: 10, itemStyle: { opacity: .42, borderRadius: [4, 4, 0, 0] }, data: trends.map(item => item[activityKey] ?? 0) }
    ]
  }; }, [activityKey, activityLabel, color, tr, trends, valueKey, valueLabel]);
  return <AdminEChart ariaLabel={`${valueLabel} ${tr ? "zaman grafiği" : "timeline"}`} buildOption={buildOption} className="tracking-module-chart" />;
}
