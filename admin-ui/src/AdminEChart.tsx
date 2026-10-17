import { useCallback, useEffect, useRef } from "react";
import { BarChart, FunnelChart, LineChart, PieChart } from "echarts/charts";
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
import { CanvasRenderer } from "echarts/renderers";

echarts.use([
  BarChart,
  FunnelChart,
  LineChart,
  PieChart,
  AriaComponent,
  DataZoomComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
]);

export type AdminChartPalette = {
  accent: string;
  danger: string;
  fontFamily: string;
  line: string;
  muted: string;
  primary: string;
  success: string;
  surface: string;
  text: string;
  warning: string;
};

function cssColor(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

export function adminChartPalette(): AdminChartPalette {
  return {
    accent: cssColor("--accent", "#237c75"),
    danger: cssColor("--danger", "#b63c4b"),
    fontFamily: getComputedStyle(document.body).fontFamily || '"Questrial", sans-serif',
    line: cssColor("--line", "#ded8e3"),
    muted: cssColor("--muted", "#6f6878"),
    primary: cssColor("--primary", "#6b538b"),
    success: cssColor("--success", "#237c75"),
    surface: cssColor("--surface", "#ffffff"),
    text: cssColor("--text", "#231f27"),
    warning: cssColor("--warning", "#b46b14")
  };
}

export function AdminEChart({
  ariaLabel,
  buildOption,
  className,
  onDataPointClick,
  onSeriesDataClick,
  dataPointCount
}: {
  ariaLabel: string;
  buildOption: () => EChartsCoreOption;
  className: string;
  onDataPointClick?: (dataIndex: number) => void;
  onSeriesDataClick?: (dataIndex: number, name: string) => void;
  dataPointCount?: number;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<echarts.EChartsType | null>(null);
  const optionRef = useRef(buildOption);
  const seriesClickRef = useRef(onSeriesDataClick);
  const dataPointClickRef = useRef(onDataPointClick);
  seriesClickRef.current = onSeriesDataClick;
  dataPointClickRef.current = onDataPointClick;
  optionRef.current = buildOption;

  const render = useCallback(() => {
    const chart = chartRef.current;
    if (!chart) return;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const palette = adminChartPalette();
    const option = optionRef.current();
    chart.setOption({
      ...option,
      textStyle: { fontFamily: palette.fontFamily, color: palette.text, ...((option as { textStyle?: object }).textStyle ?? {}) },
      animationDuration: reduceMotion ? 0 : 350
    }, true);
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = echarts.init(container, undefined, { renderer: "canvas", devicePixelRatio: Math.max(2, window.devicePixelRatio || 1) });
    chartRef.current = chart;
    chart.on("click", params => {
      if (params.componentType === "series" && typeof params.dataIndex === "number") {
        dataPointClickRef.current?.(params.dataIndex);
        seriesClickRef.current?.(params.dataIndex, params.name ?? "");
      }
    });
    const resizeObserver = new ResizeObserver(() => chart.resize());
    const themeObserver = new MutationObserver(render);

    render();
    // Canvas text must be painted again after the web font finishes loading.
    let disposed = false;
    void document.fonts.ready.then(() => { if (!disposed) render(); });
    document.fonts.addEventListener("loadingdone", render);
    resizeObserver.observe(container);
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

    return () => {
      disposed = true;
      document.fonts.removeEventListener("loadingdone", render);
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
