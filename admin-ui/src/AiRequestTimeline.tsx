import { useCallback, useEffect, useMemo, useState } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import { useAdminLocale } from "./admin/locale";
import type { AiMonitoringSummary } from "./types";

export default function AiRequestTimeline({ summary, currency, displayCurrency = currency, conversionRate = 1 }: { summary: AiMonitoringSummary; currency?: string; displayCurrency?: string; conversionRate?: number }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const [measure, setMeasure] = useState<"requests" | "cost">("requests");
  const [selectedTime, setSelectedTime] = useState<number | null>(null);
  useEffect(() => setSelectedTime(null), [summary.windowStart]);
  const dateLabel = (time: number) => new Date(time).toLocaleDateString(tr ? "tr-TR" : "en-GB", { day: "numeric", month: "long", year: "numeric" });
  const selectedRows = selectedTime == null ? [] : (summary.timeline ?? []).filter(row => new Date(row.bucketStart).toDateString() === new Date(selectedTime).toDateString());
  const selectedCost = selectedRows.filter(row => row.costCurrency === currency).reduce((sum, row) => sum + row.estimatedCost, 0) * conversionRate;
  const selectedRequests = selectedRows.reduce((sum, row) => sum + row.requestCount, 0);
  const hourly = (summary.windowHours ?? 24) <= 24;
  const points = useMemo(() => {
    if (!summary.timeline || !summary.windowStart || !summary.generatedAt) return [];
    const start = new Date(summary.windowStart);
    const end = new Date(summary.generatedAt).getTime();
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end)) return [];
    start.setMinutes(0, 0, 0);
    if (!hourly) start.setHours(0);
    const buckets = new Map<number, number>();
    for (let date = new Date(start); date.getTime() <= end && buckets.size < 750; hourly ? date.setHours(date.getHours() + 1) : date.setDate(date.getDate() + 1)) buckets.set(date.getTime(), 0);
    for (const row of summary.timeline) {
      if (measure === "cost" && row.costCurrency !== currency) continue;
      const date = new Date(row.bucketStart);
      date.setMinutes(0, 0, 0);
      if (!hourly) date.setHours(0);
      const time = date.getTime();
      if (buckets.has(time)) buckets.set(time, buckets.get(time)! + (measure === "cost" ? row.estimatedCost * conversionRate : row.requestCount));
    }
    return [...buckets];
  }, [summary, currency, measure, hourly, conversionRate]);
  const buildOption = useCallback((): EChartsCoreOption => {
    const p = adminChartPalette();
    return {
      aria: { enabled: true },
      grid: { top: 24, left: 16, right: 20, bottom: 48, containLabel: true },
      tooltip: { textStyle: { fontFamily: '"Questrial", sans-serif', fontWeight: 400 }, trigger: "axis", renderMode: "html", className: "ai-timeline-tooltip", confine: true, valueFormatter: (value: unknown) => Number(value).toLocaleString(tr ? "tr-TR" : "en-GB", { maximumFractionDigits: measure === "cost" ? 6 : 0 }) + (measure === "cost" ? " " + displayCurrency : "") },
      xAxis: { type: "time", min: points[0]?.[0], max: points.at(-1)?.[0], splitNumber: 5, axisPointer: { label: { formatter: (params: { value: number }) => new Date(params.value).toLocaleString(tr ? "tr-TR" : "en-GB", hourly ? { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" } : { day: "numeric", month: "long", year: "numeric" }) } }, axisTick: { show: false }, axisLine: { lineStyle: { color: p.line } }, axisLabel: { fontFamily: p.fontFamily, hideOverlap: true, color: p.muted, formatter: (value: number) => new Date(value).toLocaleString(tr ? "tr-TR" : "en-GB", hourly ? { hour: "2-digit", minute: "2-digit" } : { day: "numeric", month: "short" }) } },
      yAxis: { type: "value", min: 0, minInterval: measure === "requests" ? 1 : undefined, axisLabel: { fontFamily: p.fontFamily, color: p.muted }, splitLine: { lineStyle: { color: p.line, type: "dashed" } } },
      dataZoom: [{ type: "slider", height: 18, bottom: 4, borderColor: p.line, fillerColor: p.primary + "22", textStyle: { fontFamily: p.fontFamily }, showDetail: false }],
      series: [{ type: "line", name: measure === "requests" ? (tr ? "İstek sayısı" : "Requests") : (tr ? "Tahmini maliyet" : "Estimated cost"), data: points, smooth: false, symbol: "circle", symbolSize: 6, showSymbol: points.length <= 32, lineStyle: { width: 3, color: p.primary }, itemStyle: { color: p.primary }, areaStyle: { color: p.primary, opacity: 0.06 } }]
    };
  }, [points, measure, displayCurrency, hourly, tr]);
  return <div className="ai-cost-map">
    <div className="ai-timeline-header"><strong>{tr ? "Zaman içindeki kullanım" : "Usage over time"}</strong><div className="segmented-control" role="group" aria-label={tr ? "Grafik ölçümü" : "Chart measure"}><button type="button" className={measure === "requests" ? "active" : ""} aria-pressed={measure === "requests"} onClick={() => setMeasure("requests")}>{tr ? "İstek hacmi" : "Requests"}</button><button type="button" disabled={!currency} className={measure === "cost" ? "active" : ""} aria-pressed={measure === "cost"} onClick={() => setMeasure("cost")}>{tr ? "Maliyet" : "Cost"}</button></div></div>
    {points.length ? <AdminEChart ariaLabel={tr ? "AI zaman serisi" : "AI time series"} buildOption={buildOption} className="ai-request-timeline" onSeriesDataClick={index => { const time = points[index]?.[0]; if (time != null) setSelectedTime(time); }} /> : <div className="ai-cost-empty">{tr ? "Zaman serisi verisi henüz sağlanmıyor." : "Time-series data is not available yet."}</div>}
    {selectedTime != null && <div className="ai-selected-day" role="status"><strong>{dateLabel(selectedTime)}</strong><span>{tr ? "Günlük tahmini maliyet" : "Estimated daily cost"}: <b>{currency ? selectedCost.toLocaleString(tr ? "tr-TR" : "en-GB", { minimumFractionDigits: 2, maximumFractionDigits: 6 }) + " " + displayCurrency : "—"}</b></span><span>{selectedRequests} {tr ? "istek" : "requests"}</span><button type="button" className="ghost-button" onClick={() => setSelectedTime(null)}>{tr ? "Seçimi temizle" : "Clear selection"}</button></div>}
    <small>{(hourly ? (tr ? "Saatlik" : "Hourly") : (tr ? "Günlük" : "Daily")) + " · " + (measure === "requests" ? (tr ? "Tüm özellikler ve para birimleri" : "All features and currencies") : displayCurrency) + " · " + (tr ? "Dönemin ilk ve son aralığı kısmi olabilir." : "First and last intervals may be partial.")}</small>
  </div>;
}
