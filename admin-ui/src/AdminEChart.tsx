import { useCallback, useEffect, useRef } from "react";
import { BarChart, FunnelChart, LineChart } from "echarts/charts";
import {
  AriaComponent,
  DataZoomComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent
} from "echarts/components";
import * as echarts from "echarts/core";
import type { EChartsCoreOption } from "echarts/core";
import { SVGRenderer } from "echarts/renderers";

echarts.use([
  BarChart,
  FunnelChart,
  LineChart,
  AriaComponent,
  DataZoomComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  SVGRenderer
]);

export type AdminChartPalette = {
  accent: string;
  danger: string;
  line: string;
  muted: string;
  primary: string;
  surface: string;
  text: string;
};

function cssColor(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

export function adminChartPalette(): AdminChartPalette {
  return {
    accent: cssColor("--accent", "#237c75"),
    danger: cssColor("--danger", "#b63c4b"),
    line: cssColor("--line", "#ded8e3"),
    muted: cssColor("--muted", "#6f6878"),
    primary: cssColor("--primary", "#6b538b"),
    surface: cssColor("--surface", "#ffffff"),
    text: cssColor("--text", "#231f27")
  };
}

export function AdminEChart({
  ariaLabel,
  buildOption,
  className
}: {
  ariaLabel: string;
  buildOption: () => EChartsCoreOption;
  className: string;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<echarts.EChartsType | null>(null);
  const optionRef = useRef(buildOption);
  optionRef.current = buildOption;

  const render = useCallback(() => {
    const chart = chartRef.current;
    if (!chart) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    chart.setOption({
      ...optionRef.current(),
      animationDuration: reduceMotion ? 0 : 350
    }, true);
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = echarts.init(container, undefined, { renderer: "svg" });
    chartRef.current = chart;
    const resizeObserver = new ResizeObserver(() => chart.resize());
    const themeObserver = new MutationObserver(render);

    render();
    resizeObserver.observe(container);
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

    return () => {
      resizeObserver.disconnect();
      themeObserver.disconnect();
      chartRef.current = null;
      chart.dispose();
    };
  }, [render]);

  useEffect(() => {
    render();
  }, [buildOption, render]);

  return <div aria-label={ariaLabel} className={className} ref={containerRef} role="img" />;
}
