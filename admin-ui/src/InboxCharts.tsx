import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";

type Locale = "tr" | "en";
type ChartDatum = { name: string; value: number };

function tooltip() {
  const palette = adminChartPalette();
  return { backgroundColor: palette.surface, borderColor: palette.line, borderWidth: 1, textStyle: { color: palette.text, fontFamily: palette.fontFamily, fontSize: 12 } };
}

export function InboxPriorityChart({ critical, high, normal, locale }: { critical: number; high: number; normal: number; locale: Locale }) {
  const data: ChartDatum[] = [
    { name: locale === "tr" ? "Kritik" : "Critical", value: critical },
    { name: locale === "tr" ? "Yüksek" : "High", value: high },
    { name: locale === "tr" ? "Normal" : "Normal", value: normal }
  ];
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const total = critical + high + normal;
    return {
      textStyle: { fontFamily: palette.fontFamily },
      color: [palette.danger, palette.warning, palette.primary],
      tooltip: { ...tooltip(), trigger: "item" },
      legend: { bottom: 0, left: "center", itemWidth: 9, itemHeight: 9, textStyle: { color: palette.muted, fontSize: 10 } },
      graphic: [
        { type: "text", left: "center", top: "34%", style: { text: total.toLocaleString(), textAlign: "center", fill: palette.text, fontFamily: palette.fontFamily, fontSize: 20, fontWeight: 700 } },
        { type: "text", left: "center", top: "46%", style: { text: locale === "tr" ? "açık iş" : "open work", textAlign: "center", fill: palette.muted, fontFamily: palette.fontFamily, fontSize: 10 } }
      ],
      series: [{ type: "pie", radius: ["50%", "72%"], center: ["50%", "43%"], label: { show: false }, itemStyle: { borderColor: palette.surface, borderWidth: 3, borderRadius: 3 }, emphasis: { scaleSize: 3 }, data }]
    };
  }, [critical, high, locale, normal]);
  return <AdminEChart ariaLabel={locale === "tr" ? "İş öncelikleri" : "Work priorities"} buildOption={buildOption} className="inbox-chart" />;
}

export function InboxQueueChart({ data, locale }: { data: ChartDatum[]; locale: Locale }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      textStyle: { fontFamily: palette.fontFamily },
      grid: { top: 8, right: 58, bottom: 12, left: 112 },
      tooltip: { ...tooltip(), trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted, fontSize: 9 }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
      yAxis: { type: "category", inverse: true, data: data.map(item => item.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.muted, fontSize: 10, width: 96, overflow: "truncate" } },
      series: [{ type: "bar", barWidth: 18, showBackground: true, backgroundStyle: { color: palette.line, opacity: .3, borderRadius: 5 }, data: data.map((item, index) => ({ value: item.value, itemStyle: { color: [palette.primary, palette.accent, palette.warning, palette.danger, palette.success][index % 5], borderRadius: [0, 5, 5, 0] } })), label: { show: true, position: "right", color: palette.muted, fontSize: 10 } }]
    };
  }, [data, locale]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Kuyruklara göre iş yükü" : "Workload by queue"} buildOption={buildOption} className="inbox-chart" />;
}
