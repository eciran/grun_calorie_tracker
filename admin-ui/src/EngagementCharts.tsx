import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";

export type OnboardingFunnelDatum = {
  label: string;
  value: number;
};

export type FeatureAdoptionDatum = {
  events: number;
  label: string;
  users: number;
};

export type CoreFlowDatum = { label: string; started: number; completed: number; failed: number };

export function OnboardingFunnelChart({ items }: { items: OnboardingFunnelDatum[] }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true },
      color: [palette.primary, palette.accent, "#8d70ad", "#b192ce", "#5d8d88"],
      tooltip: {
        trigger: "item",
        backgroundColor: palette.surface,
        borderColor: palette.line,
        borderWidth: 1,
        textStyle: { color: palette.text, fontFamily: palette.fontFamily, fontSize: 12 },
        formatter: "{b}: {c}"
      },
      series: [{
        name: "Onboarding",
        type: "funnel",
        left: "8%",
        right: "8%",
        top: 8,
        bottom: 8,
        minSize: "22%",
        maxSize: "100%",
        sort: "none",
        gap: 3,
        label: {
          show: true,
          position: "inside",
          color: "#ffffff",
          fontFamily: palette.fontFamily, fontSize: 11,
          formatter: "{b}  {c}"
        },
        labelLine: { show: false },
        itemStyle: {
          borderColor: palette.surface,
          borderWidth: 1,
          borderRadius: 3
        },
        emphasis: { focus: "self" },
        data: items.map((item) => ({ name: item.label, value: item.value }))
      }]
    };
  }, [items]);

  return <AdminEChart ariaLabel="Onboarding event funnel" buildOption={buildOption} className="engagement-funnel-chart" />;
}

export function FeatureAdoptionChart({ items }: { items: FeatureAdoptionDatum[] }) {
  const sorted = [...items]
    .sort((left, right) => right.users - left.users || right.events - left.events)
    .slice(0, 10)
    .reverse();
  const buildOption = useCallback((): EChartsCoreOption => {
    const palette = adminChartPalette();
    return {
      aria: { enabled: true },
      color: [palette.primary, palette.accent],
      grid: { top: 38, right: 24, bottom: 22, left: 138 },
      legend: {
        top: 0,
        right: 8,
        itemHeight: 9,
        itemWidth: 18,
        textStyle: { color: palette.muted, fontFamily: palette.fontFamily, fontSize: 11 }
      },
      tooltip: {
        trigger: "axis",
        backgroundColor: palette.surface,
        borderColor: palette.line,
        borderWidth: 1,
        textStyle: { color: palette.text, fontFamily: palette.fontFamily, fontSize: 12 },
        axisPointer: { type: "shadow", shadowStyle: { color: "rgba(107, 83, 139, 0.08)" } }
      },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: palette.muted, fontFamily: palette.fontFamily, fontSize: 10 },
        splitLine: { lineStyle: { color: palette.line, type: "dashed" } }
      },
      yAxis: {
        type: "category",
        data: sorted.map((item) => item.label),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: palette.text, fontFamily: palette.fontFamily, fontSize: 10, width: 118, overflow: "truncate" }
      },
      series: [
        {
          name: "Unique users",
          type: "bar",
          data: sorted.map((item) => item.users),
          barMaxWidth: 16,
          itemStyle: { borderRadius: [0, 3, 3, 0] },
          emphasis: { focus: "series" }
        },
        {
          name: "Events",
          type: "bar",
          data: sorted.map((item) => item.events),
          barMaxWidth: 16,
          itemStyle: { borderRadius: [0, 3, 3, 0] },
          emphasis: { focus: "series" }
        }
      ]
    };
  }, [sorted]);

  return <AdminEChart ariaLabel="Feature adoption by users and events" buildOption={buildOption} className="feature-adoption-chart" />;
}

export function CoreFlowComparisonChart({ items, tr }: { items: CoreFlowDatum[]; tr: boolean }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const p=adminChartPalette();
    return { aria:{enabled:true}, color:[p.primary,p.accent,"#b75c68"], grid:{top:42,right:18,bottom:34,left:46}, legend:{top:0,right:8,textStyle:{color:p.muted,fontFamily:p.fontFamily,fontSize:11}}, tooltip:{trigger:"axis",backgroundColor:p.surface,borderColor:p.line,textStyle:{color:p.text,fontFamily:p.fontFamily},axisPointer:{type:"shadow"}}, xAxis:{type:"category",data:items.map(x=>x.label),axisTick:{show:false},axisLine:{lineStyle:{color:p.line}},axisLabel:{color:p.text,fontFamily:p.fontFamily}}, yAxis:{type:"value",minInterval:1,axisLabel:{color:p.muted,fontFamily:p.fontFamily},splitLine:{lineStyle:{color:p.line,type:"dashed"}}}, series:[{name:tr?"Başladı":"Started",type:"bar",data:items.map(x=>x.started),barMaxWidth:30,itemStyle:{borderRadius:[4,4,0,0]}},{name:tr?"Tamamlandı":"Completed",type:"bar",data:items.map(x=>x.completed),barMaxWidth:30,itemStyle:{borderRadius:[4,4,0,0]}},{name:tr?"Hata":"Failed",type:"bar",data:items.map(x=>x.failed),barMaxWidth:30,itemStyle:{borderRadius:[4,4,0,0]}}] };
  },[items,tr]);
  return <AdminEChart ariaLabel={tr?"Temel kayıt akışları karşılaştırması":"Core logging flow comparison"} buildOption={buildOption} className="engagement-flow-chart"/>;
}
