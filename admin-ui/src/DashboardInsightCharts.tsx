import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import type { RevenueCatChartPoint, SystemReliabilityApiTrendPoint } from "./types";

type Locale = "tr" | "en";
type Datum = { name: string; value: number };

function baseTooltip() {
  const palette = adminChartPalette();
  return { trigger: "item" as const, backgroundColor: palette.surface, borderColor: palette.line, borderWidth: 1, textStyle: { color: palette.text, fontSize: 12 } };
}

function DonutChart({ data, ariaLabel, centerLabel }: { data: Datum[]; ariaLabel: string; centerLabel: string }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    const total = data.reduce((sum, item) => sum + item.value, 0);
    return {
      textStyle: { fontFamily: palette.fontFamily },
      color: [palette.primary, palette.accent, palette.success, palette.warning, palette.danger],
      tooltip: baseTooltip(),
      legend: { bottom: 0, left: "center", itemWidth: 9, itemHeight: 9, textStyle: { color: palette.muted, fontSize: 10 } },
      graphic: [
        { type: "text", left: "center", top: "35%", style: { text: total.toLocaleString(), textAlign: "center", fill: palette.text, fontFamily: palette.fontFamily, fontSize: 18, fontWeight: 700 } },
        { type: "text", left: "center", top: "46%", style: { text: centerLabel, textAlign: "center", fill: palette.muted, fontFamily: palette.fontFamily, fontSize: 10, fontWeight: 500 } }
      ],
      series: [{ type: "pie", radius: ["48%", "70%"], center: ["50%", "43%"], avoidLabelOverlap: true, itemStyle: { borderColor: palette.surface, borderWidth: 2, borderRadius: 2 }, label: { show: false }, emphasis: { label: { show: false }, scaleSize: 3 }, data }]
    };
  }, [ariaLabel, centerLabel, data]);
  return <AdminEChart ariaLabel={ariaLabel} buildOption={buildOption} className="dashboard-insight-chart" />;
}

export function SubscriptionMixChart({ plus, pro, canceled, locale }: { plus: number; pro: number; canceled: number; locale: Locale }) {
  const data = [{ name: "PLUS", value: plus }, { name: "PRO", value: pro }, { name: locale === "tr" ? "İptal" : "Canceled", value: canceled }];
  return <DonutChart data={data} ariaLabel={locale === "tr" ? "Abonelik dağılımı" : "Subscription distribution"} centerLabel={locale === "tr" ? "abonelik" : "subscriptions"} />;
}

export function AiOutcomeChart({ confirmed, rejected, failed, total, locale }: { confirmed: number; rejected: number; failed: number; total: number; locale: Locale }) {
  const pending = Math.max(0, total - confirmed - rejected - failed);
  const data = [
    { name: locale === "tr" ? "Onaylandı" : "Confirmed", value: confirmed },
    { name: locale === "tr" ? "Reddedildi" : "Rejected", value: rejected },
    { name: locale === "tr" ? "Başarısız" : "Failed", value: failed },
    { name: locale === "tr" ? "Diğer" : "Other", value: pending }
  ];
  return <DonutChart data={data} ariaLabel={locale === "tr" ? "Son yedi günlük AI istek sonuçları" : "AI request outcomes for the last seven days"} centerLabel={locale === "tr" ? "AI isteği" : "AI requests"} />;
}

export function CatalogPipelineChart({ verified, raw, review, rejected, locale }: { verified: number; raw: number; review: number; rejected: number; locale: Locale }) {
  const palette = adminChartPalette();
  const labels = locale === "tr" ? ["Doğrulandı", "Ham veri", "İnceleme", "Reddedildi"] : ["Verified", "Raw", "Review", "Rejected"];
  const values = [verified, raw, review, rejected];
  const buildOption = useCallback((): EChartsCoreOption => ({
    textStyle: { fontFamily: palette.fontFamily },
    color: [palette.success],
    grid: { top: 4, right: 72, bottom: 8, left: 82, containLabel: false },
    tooltip: { ...baseTooltip(), trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: { type: "value", axisLabel: { color: palette.muted, fontSize: 9 }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
    yAxis: { type: "category", data: labels, inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.muted, fontSize: 10 } },
    series: [{ type: "bar", data: values.map((value, index) => ({ value, itemStyle: { color: [palette.success, palette.accent, palette.warning, palette.danger][index], borderRadius: [0, 5, 5, 0] } })), barWidth: 17, label: { show: true, position: "right", color: palette.muted, fontSize: 9, formatter: ({ value }: { value: number }) => Number(value).toLocaleString() } }]
  }), [labels, locale, palette, values]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Katalog işlem hattı" : "Catalog pipeline"} buildOption={buildOption} className="dashboard-insight-chart" />;
}

export function WorkloadChart({ product, recipe, ai, alerts, locale }: { product: number; recipe: number; ai: number; alerts: number; locale: Locale }) {
  const palette = adminChartPalette();
  const labels = locale === "tr" ? ["Ürün", "Tarif", "AI iadesi", "Kritik"] : ["Product", "Recipe", "AI refund", "Critical"];
  const values = [product, recipe, ai, alerts];
  const buildOption = useCallback((): EChartsCoreOption => ({
    textStyle: { fontFamily: palette.fontFamily },
    grid: { top: 4, right: 72, bottom: 8, left: 72 },
    tooltip: { ...baseTooltip(), trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted, fontSize: 9 }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
    yAxis: { type: "category", data: labels, inverse: true, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.muted, fontSize: 9 } },
    series: [{ type: "bar", data: values.map((value, index) => ({ value, itemStyle: { color: [palette.primary, palette.accent, palette.warning, palette.danger][index], borderRadius: [0, 5, 5, 0] } })), barWidth: 18, barMinHeight: 3, showBackground: true, backgroundStyle: { color: palette.line, opacity: .28, borderRadius: 5 }, label: { show: true, position: "right", color: palette.muted, fontSize: 9, formatter: ({ value }: { value: number }) => Number(value).toLocaleString() } }]
  }), [labels, locale, palette, values]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Açık iş yükü" : "Open workload"} buildOption={buildOption} className="dashboard-insight-chart" />;
}

export function RevenuePulseChart({ points, locale }: { points: RevenueCatChartPoint[]; locale: Locale }) {
  const palette = adminChartPalette();
  const buildOption = useCallback((): EChartsCoreOption => ({
    textStyle: { fontFamily: palette.fontFamily },
    grid: { top: 16, right: 16, bottom: 28, left: 48 },
    tooltip: { ...baseTooltip(), trigger: "axis" },
    xAxis: { type: "category", data: points.map(point=>point.date??""), axisLine:{lineStyle:{color:palette.line}}, axisTick:{show:false}, axisLabel:{color:palette.muted,fontSize:9,hideOverlap:true,formatter:(value:string)=>value.slice(5)} },
    yAxis: { type:"value", axisLabel:{color:palette.muted,fontSize:9}, splitLine:{lineStyle:{color:palette.line,type:"dashed"}} },
    series: [{ type:"line", data:points.map(point=>point.value??0), smooth:.3, symbol:"circle", symbolSize:5, lineStyle:{width:3,color:palette.success}, itemStyle:{color:palette.success}, areaStyle:{color:palette.success,opacity:.1} }]
  }),[locale,palette,points]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Gelir eğilimi" : "Revenue trend"} buildOption={buildOption} className="dashboard-pulse-chart"/>;
}

export function RetentionPulseChart({ activeRate, inactiveRate, locale }: { activeRate:number; inactiveRate:number; locale:Locale }) {
  const palette=adminChartPalette();
  const labels=locale==="tr"?["30 gün aktif","30+ gün pasif"]:["Active in 30d","Inactive 30d+"];
  const buildOption=useCallback(():EChartsCoreOption=>({
    textStyle:{fontFamily:palette.fontFamily},
    grid:{top:10,right:48,bottom:8,left:92}, tooltip:{...baseTooltip(),trigger:"axis",valueFormatter:(value:number)=>`%${Number(value).toFixed(1)}`},
    xAxis:{type:"value",max:100,axisLabel:{color:palette.muted,fontSize:9,formatter:"{value}%"},splitLine:{lineStyle:{color:palette.line,type:"dashed"}}},
    yAxis:{type:"category",inverse:true,data:labels,axisLine:{show:false},axisTick:{show:false},axisLabel:{color:palette.muted,fontSize:9}},
    series:[{type:"bar",barWidth:18,data:[{value:activeRate,itemStyle:{color:palette.success,borderRadius:[0,5,5,0]}},{value:inactiveRate,itemStyle:{color:palette.warning,borderRadius:[0,5,5,0]}}],showBackground:true,backgroundStyle:{color:palette.line,opacity:.3,borderRadius:5},label:{show:true,position:"right",color:palette.text,fontSize:10,formatter:({value}:{value:number})=>`%${Number(value).toFixed(1)}`}}]
  }),[activeRate,inactiveRate,labels,locale,palette]);
  return <AdminEChart ariaLabel={locale==="tr"?"Aktiflik ve pasiflik göstergesi":"Activity and inactivity indicator"} buildOption={buildOption} className="dashboard-pulse-chart"/>;
}

export function ReliabilityPulseChart({ points, locale }: { points:SystemReliabilityApiTrendPoint[]; locale:Locale }) {
  const palette=adminChartPalette();
  const buildOption=useCallback(():EChartsCoreOption=>({
    textStyle:{fontFamily:palette.fontFamily},
    color:[palette.danger,palette.primary], grid:{top:28,right:48,bottom:28,left:44}, tooltip:{...baseTooltip(),trigger:"axis"},
    legend:{top:0,right:0,itemWidth:14,itemHeight:8,textStyle:{color:palette.muted,fontSize:9}},
    xAxis:{type:"category",data:points.map(point=>point.bucket),axisLine:{lineStyle:{color:palette.line}},axisTick:{show:false},axisLabel:{color:palette.muted,fontSize:9,hideOverlap:true,formatter:(value:string)=>value.slice(11,16)}},
    yAxis:[{type:"value",axisLabel:{color:palette.muted,fontSize:9,formatter:"{value}%"},splitLine:{lineStyle:{color:palette.line,type:"dashed"}}},{type:"value",axisLabel:{color:palette.muted,fontSize:9,formatter:"{value} ms"},splitLine:{show:false}}],
    series:[{name:locale==="tr"?"Hata oranı":"Error rate",type:"line",data:points.map(point=>Number((point.errorRate*100).toFixed(2))),smooth:true,symbolSize:4,lineStyle:{width:2}},{name:"p95",type:"line",yAxisIndex:1,data:points.map(point=>point.latencyP95Ms),smooth:true,symbolSize:4,lineStyle:{width:2}}]
  }),[locale,palette,points]);
  return <AdminEChart ariaLabel={locale==="tr"?"API güvenilirliği":"API reliability"} buildOption={buildOption} className="dashboard-pulse-chart"/>;
}
