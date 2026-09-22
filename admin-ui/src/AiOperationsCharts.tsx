import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { AiMonitoringSummary } from "./types";
import { useAdminLocale } from "./admin/locale";

export function AiPhotoOutcomeChart({ summary }: { summary: AiMonitoringSummary }) {
  const { locale } = useAdminLocale();
  const buildOption = useCallback((): EChartsCoreOption => {
    const p = adminChartPalette();
    const totals = new Map<string, number>();
    for (const item of summary.requestStatuses ?? []) {
      if (item.requestType !== "PHOTO_MEAL_LOG") continue;
      const status = item.status ?? "UNKNOWN";
      totals.set(status, (totals.get(status) ?? 0) + Number(item.requestCount ?? 0));
    }
    const colors: Record<string, string> = { DRAFT_CREATED: p.primary, CONFIRMED: p.accent, REJECTED: p.warning, FAILED: p.danger };
    const labels: Record<string, string> = locale === "tr"
      ? { DRAFT_CREATED: "Taslak", CONFIRMED: "Onaylandı", REJECTED: "Reddedildi", FAILED: "Başarısız" }
      : { DRAFT_CREATED: "Draft", CONFIRMED: "Confirmed", REJECTED: "Rejected", FAILED: "Failed" };
    const data = [...totals].filter(([, value]) => value > 0).map(([status, value]) => ({
      name: labels[status] ?? status, value, itemStyle: { color: colors[status] ?? p.muted }
    }));
    return {
      aria: { enabled: true },
      tooltip: { textStyle: { fontFamily: adminChartPalette().fontFamily }, trigger: "item" },
      legend: { bottom: 0, type: "scroll", textStyle: { color: p.muted, fontFamily: p.fontFamily } },
      series: [{ type: "pie", radius: ["55%", "73%"], center: ["50%", "43%"], label: { show: false }, data }],
      graphic: [{ type: "text", left: "center", top: "34%", style: {
        text: String(data.reduce((sum, item) => sum + item.value, 0)) + "\n" + (locale === "tr" ? "fotoğraf isteği" : "photo requests"),
        fill: p.text, fontSize: 16, fontFamily: p.fontFamily, textAlign: "center", lineHeight: 25
      } }]
    };
  }, [summary, locale]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Fotoğraf analizi sonuçları" : "Photo analysis outcomes"} buildOption={buildOption} className="ai-photo-echart" />;
}

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
      animationDuration: 420,
      color: colors,
      grid: { top: 46, right: 18, bottom: 78, left: 58 },
      legend: { top: 4, textStyle: { color: palette.muted, fontFamily: palette.fontFamily }, itemWidth: 12, itemHeight: 8 },
      tooltip: { textStyle: { fontFamily: adminChartPalette().fontFamily }, trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: {
        type: "category",
        data: requestTypes.map(readable),
        axisLabel: { fontFamily: palette.fontFamily, color: palette.muted, interval: 0, rotate: requestTypes.length > 4 ? 24 : 0, overflow: "truncate", width: 108 },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { fontFamily: palette.fontFamily, color: palette.muted, formatter: compactNumber },
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
      animationDuration: 420,
      grid: { top: 16, right: 62, bottom: 30, left: 62 },
      tooltip: { textStyle: { fontFamily: adminChartPalette().fontFamily }, trigger: "axis", axisPointer: { type: "shadow" }, valueFormatter: (value: unknown) => `${compactNumber(value)} ms` },
      xAxis: {
        type: "category",
        data: ["p50", "p95", "p99"],
        axisLabel: { fontFamily: palette.fontFamily, color: palette.text },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        axisLabel: { fontFamily: palette.fontFamily, color: palette.muted, formatter: (value: unknown) => `${compactNumber(value)} ms` },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: [{
        type: "bar",
        name: "Latency",
        data: values,
        barMaxWidth: 54,
        itemStyle: { color: (params: { dataIndex: number }) => [palette.accent, palette.primary, palette.danger][params.dataIndex], borderRadius: [4, 4, 0, 0] },
        label: { fontFamily: palette.fontFamily, show: true, position: "top", color: palette.text, formatter: ({ value }: { value: unknown }) => `${compactNumber(value)} ms` }
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
      animationDuration: 420,
      color: [palette.danger, palette.accent],
      grid: { top: 44, right: 24, bottom: 34, left: 72 },
      legend: { top: 4, textStyle: { color: palette.muted, fontFamily: palette.fontFamily }, itemWidth: 12, itemHeight: 8 },
      tooltip: { textStyle: { fontFamily: adminChartPalette().fontFamily },
        trigger: "axis",
        axisPointer: { type: "shadow" },
        valueFormatter: (value: unknown) => Number(value ?? 0).toFixed(4)
      },
      xAxis: {
        type: "category",
        data: currencies,
        axisLabel: { fontFamily: palette.fontFamily, color: palette.text },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        axisLabel: { fontFamily: palette.fontFamily, color: palette.muted, formatter: compactNumber },
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
