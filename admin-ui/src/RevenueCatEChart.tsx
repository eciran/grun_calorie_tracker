import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { RevenueCatChart } from "./types";

function isDateLike(value?: string): boolean {
  return Boolean(value && (/^\d{4}-\d{2}-\d{2}/.test(value) || /^\d{4}-\d{2}/.test(value)));
}

function formatLabel(value?: string): string {
  if (!value) return "-";
  if (!isDateLike(value)) return value;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short" }).format(date);
}

function isMoneyChart(chartName?: string): boolean {
  return chartName === "revenue" || chartName === "mrr";
}

function formatValue(value: unknown, currency?: string, money = false): string {
  const numeric = Number(value ?? 0);
  const rounded = Math.abs(numeric) >= 10 ? Math.round(numeric) : Number(numeric.toFixed(1));
  const formatted = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 1 }).format(rounded);
  return money && currency ? `${currency} ${formatted}` : formatted;
}

export function RevenueCatEChart({ chart }: { chart: RevenueCatChart }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const points = (chart.points ?? []).filter((point) => Number.isFinite(Number(point.value ?? 0))).slice(-90);
    const labels = points.map((point) => point.date ?? "-");
    const values = points.map((point) => Number(point.value ?? 0));
    const money = isMoneyChart(chart.chartName);
    const timeAxis = points.some((point) => isDateLike(point.date));
    const valueFormatter = (value: unknown) => formatValue(value, chart.currency, money);

    if (!timeAxis) {
      return {
        aria: { enabled: true, description: `${chart.label ?? chart.chartName ?? "RevenueCat"} category comparison` },
        animationDuration: 420,
        grid: { top: 8, right: 54, bottom: 30, left: 132 },
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, valueFormatter },
        xAxis: {
          type: "value",
          axisLabel: { color: palette.muted, formatter: valueFormatter },
          axisLine: { lineStyle: { color: palette.line } },
          splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
        },
        yAxis: {
          type: "category",
          data: labels,
          axisLabel: { color: palette.text, formatter: formatLabel, overflow: "truncate", width: 112 },
          axisLine: { lineStyle: { color: palette.line } },
          axisTick: { show: false }
        },
        series: [{
          type: "bar",
          name: chart.label ?? chart.chartName ?? "Value",
          data: values,
          barMaxWidth: 24,
          itemStyle: { color: palette.primary, borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: "right", color: palette.text, formatter: ({ value }: { value: unknown }) => valueFormatter(value) }
        }]
      };
    }

    return {
      aria: { enabled: true, description: `${chart.label ?? chart.chartName ?? "RevenueCat"} time series` },
      animationDuration: 420,
      dataZoom: points.length > 30 ? [{ type: "inside", start: 0, end: 100 }, { type: "slider", height: 18, bottom: 2 }] : [],
      grid: { top: 20, right: 22, bottom: points.length > 30 ? 58 : 34, left: 72 },
      tooltip: { trigger: "axis", valueFormatter },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: labels,
        axisLabel: { color: palette.muted, formatter: formatLabel, hideOverlap: true },
        axisLine: { lineStyle: { color: palette.line } },
        axisTick: { show: false }
      },
      yAxis: {
        type: "value",
        scale: true,
        axisLabel: { color: palette.muted, formatter: valueFormatter },
        axisLine: { show: false },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      series: [{
        type: "line",
        name: chart.label ?? chart.chartName ?? "Value",
        data: values,
        symbol: "circle",
        symbolSize: (_value: unknown, params: { dataIndex: number }) => params.dataIndex === values.length - 1 ? 8 : 4,
        showSymbol: points.length <= 31,
        lineStyle: { color: palette.primary, width: 3 },
        itemStyle: { color: palette.primary, borderColor: palette.surface, borderWidth: 2 },
        areaStyle: { color: palette.primary, opacity: 0.1 },
        emphasis: { focus: "series" }
      }]
    };
  }, [chart]);

  return <AdminEChart ariaLabel={`${chart.label ?? chart.chartName ?? "RevenueCat"} analytics chart`} buildOption={buildOption} className="revenuecat-echart" />;
}