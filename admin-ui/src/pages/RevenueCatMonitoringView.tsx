import { lazy, Suspense, useState } from "react";

import { RevenueCatChart, RevenueCatMonitoringCharts, RevenueCatMonitoringOverview } from "../types";

import { EmptyState, SectionToolbar } from "../AdminPrimitives";

import { Badge, DatePickerButton, combineStates, formatValue, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

export const RevenueCatEChart = lazy(() => import("../RevenueCatEChart").then((module) => ({ default: module.RevenueCatEChart })));

export type RevenueCatRange = "7d" | "28d" | "90d" | "custom";

export function RevenueCatMonitoringView({ environment, onError }: { environment: "production" | "sandbox"; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  const [range, setRange] = useState<RevenueCatRange>("28d");
  const [customStartDate, setCustomStartDate] = useState(() => dateInputDaysAgo(27));
  const [customEndDate, setCustomEndDate] = useState(() => dateInputDaysAgo(0));
  const chartPath = range === "custom"
    ? `/api/v1/admin/revenuecat/monitoring/charts?environment=${environment}&range=custom&startDate=${customStartDate}&endDate=${customEndDate}`
    : `/api/v1/admin/revenuecat/monitoring/charts?environment=${environment}&range=${range}`;
  const { data: overview, state: overviewState, reload: reloadOverview } = useEndpoint<RevenueCatMonitoringOverview>(`/api/v1/admin/revenuecat/monitoring/overview?environment=${environment}`, onError);
  const { data: charts, state: chartsState, reload: reloadCharts } = useEndpoint<RevenueCatMonitoringCharts>(chartPath, onError);
  function reloadAll() {
    void reloadOverview();
    void reloadCharts();
  }

  return (
    <div className="stack">
      <SectionToolbar title={tx(`RevenueCat ${environment === "production" ? "production" : "sandbox"} monitoring`, `RevenueCat ${environment === "production" ? "production" : "sandbox"} izleme`)} state={combineStates([overviewState, chartsState])} onReload={reloadAll}>
        <div className="revenuecat-range-control">
          <div className="segmented-control compact">
          {(["7d", "28d", "90d", "custom"] as const).map((item) => (
            <button className={range === item ? "active" : ""} key={item} onClick={() => setRange(item)} type="button">{item}</button>
          ))}
          </div>
          {range === "custom" && (
            <div className="custom-range-fields">
              <DatePickerButton
                label={tx("Start date", "Başlangıç tarihi")}
                max={customEndDate}
                onChange={setCustomStartDate}
                value={customStartDate}
              />
              <span>{tx("to", "ile")}</span>
              <DatePickerButton
                label={tx("End date", "Bitiş tarihi")}
                max={dateInputDaysAgo(0)}
                min={customStartDate}
                onChange={setCustomEndDate}
                value={customEndDate}
              />
            </div>
          )}
        </div>
      </SectionToolbar>
      <RevenueCatMonitoringPanel charts={charts} environment={environment} overview={overview} range={range} />
    </div>
  );
}

export function RevenueCatMonitoringPanel({
  charts,
  environment,
  overview,
  range,
  setEnvironment
}: {
  charts: RevenueCatMonitoringCharts | null;
  environment: "production" | "sandbox";
  overview: RevenueCatMonitoringOverview | null;
  range: RevenueCatRange;
  setEnvironment?: (value: "production" | "sandbox") => void;
}) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  const metricRows = overview?.metrics ?? [];
  const chartRows = sortRevenueCatCharts(charts?.charts?.length
    ? charts.charts
    : revenueCatPlaceholderCharts(metricRows, environment, overview?.currency ?? charts?.currency, charts?.statusMessage ?? overview?.statusMessage));
  const [selectedChartKey, setSelectedChartKey] = useState<string | null>(null);
  const selectedChart = chartRows.find((chart) => chartKey(chart) === selectedChartKey) ?? chartRows[0] ?? null;

  return (
    <div className="stack">
      <div className="revenuecat-monitor-hero">
        <div>
          <p className="eyebrow">{tx("RevenueCat analytics", "RevenueCat analizi")}</p>
          <h2>{tx("Subscription revenue, customer, and trial signals.", "Abonelik geliri, müşteri ve deneme sinyalleri.")}</h2>
          <p>{formatMonitoringStatus(overview?.statusMessage, locale)} {tx("Chart range", "Grafik aralığı")}: {range}.</p>
        </div>
        {setEnvironment ? <div className="segmented-control">
          <button className={environment === "production" ? "active" : ""} onClick={() => setEnvironment("production")} type="button">
            Production
          </button>
          <button className={environment === "sandbox" ? "active" : ""} onClick={() => setEnvironment("sandbox")} type="button">
            Sandbox
          </button>
        </div> : <Badge value={environment === "production" ? "Production" : "Sandbox"} tone={environment === "production" ? "good" : "neutral"} />}
      </div>
      <div className="revenuecat-metric-grid">
        {metricRows.map((metric) => (
          <article className="revenuecat-metric-card" key={metric.key ?? metric.label}>
            <span>{metric.label ?? "-"}</span>
            <strong>{formatRevenueCatMetricClean(metric)}</strong>
            <small>{metric.description ?? "-"}</small>
          </article>
        ))}
        {!metricRows.length && <EmptyState message={tx("No RevenueCat metric returned.", "RevenueCat metriği bulunamadı.")} />}
      </div>
      <RevenueCatChartWorkspace charts={chartRows} selectedChart={selectedChart} onSelect={setSelectedChartKey} />
    </div>
  );
}

export function RevenueCatChartWorkspace({
  charts,
  onSelect,
  selectedChart
}: {
  charts: RevenueCatChart[];
  onSelect: (key: string) => void;
  selectedChart: RevenueCatChart | null;
}) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  if (!charts.length || !selectedChart) {
    return <EmptyState message={tx("No RevenueCat chart returned.", "RevenueCat grafiği bulunamadı.")} />;
  }
  return (
    <article className="revenuecat-chart-workspace">
      <div className="revenuecat-metric-selector" aria-label="RevenueCat metric selector">
        {charts.map((chart) => {
          const summary = revenueCatChartSummary(chart);
          const active = chartKey(chart) === chartKey(selectedChart);
          return (
            <button className={active ? "active" : ""} key={chartKey(chart)} onClick={() => onSelect(chartKey(chart))} type="button">
              <span>{chart.label ?? chart.chartName ?? "-"}</span>
              <small>{summary.hasPoints ? tx(`${summary.count} points`, `${summary.count} nokta`) : tx("Waiting for data", "Veri bekleniyor")}</small>
            </button>
          );
        })}
      </div>
      <RevenueCatAnalyticsChart chart={selectedChart} />
    </article>
  );
}

export function revenueCatPlaceholderCharts(
  metrics: Array<{ key?: string; label?: string }>,
  environment: "production" | "sandbox",
  currency?: string,
  statusMessage?: string
): RevenueCatChart[] {
  return metrics
    .filter((metric) => Boolean(metric.key))
    .map((metric) => ({
      chartName: metric.key,
      label: metric.label,
      environment,
      currency,
      providerReachable: false,
      statusMessage: statusMessage ?? "RevenueCat chart points are not available yet.",
      points: []
    }));
}

export function sortRevenueCatCharts(charts: RevenueCatChart[]): RevenueCatChart[] {
  const order = ["revenue", "actives", "trials", "mrr", "customers_new", "customers_active"];
  return [...charts].sort((left, right) => {
    const leftIndex = order.indexOf(chartKey(left));
    const rightIndex = order.indexOf(chartKey(right));
    return (leftIndex === -1 ? 99 : leftIndex) - (rightIndex === -1 ? 99 : rightIndex);
  });
}

export function chartKey(chart: RevenueCatChart): string {
  return chart.chartName ?? chart.label ?? "chart";
}

export function revenueCatChartSummary(chart: RevenueCatChart) {
  const values = (chart.points ?? [])
    .map((point) => Number(point.value ?? 0))
    .filter((value) => Number.isFinite(value));
  const latest = values[values.length - 1] ?? 0;
  const previous = values[values.length - 2] ?? latest;
  const total = values.reduce((sum, value) => sum + value, 0);
  return {
    average: values.length ? total / values.length : 0,
    count: values.length,
    delta: latest - previous,
    hasPoints: values.length > 0,
    latest,
    max: Math.max(...values, 0),
    min: Math.min(...values, 0)
  };
}

export function RevenueCatAnalyticsChart({ chart }: { chart: RevenueCatChart }) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  const points = (chart.points ?? []).filter((point) => Number.isFinite(Number(point.value ?? 0))).slice(-90);
  const isMoney = isRevenueCatMoneyChart(chart.chartName);
  const summary = revenueCatChartSummary(chart);
  const maxValue = summary.max;
  const latest = summary.latest;
  const delta = summary.delta;
  const average = summary.average;

  return (
    <article className="revenuecat-chart-card">
      <div className="revenuecat-chart-heading">
        <div>
          <span>{chart.label ?? chart.chartName ?? "-"}</span>
          <small>{formatMonitoringStatus(chart.statusMessage, locale)}</small>
        </div>
        <Badge value={chart.environment === "sandbox" ? "Sandbox" : "Production"} tone={chart.environment === "sandbox" ? "neutral" : "good"} />
      </div>
      <div className="revenuecat-chart-summary">
        <div><span>{tx("Latest", "Son değer")}</span><strong>{formatRevenueCatChartValue(latest, chart.currency, isMoney)}</strong></div>
        <div><span>{tx("Change", "Değişim")}</span><strong className={delta >= 0 ? "positive" : "negative"}>{delta >= 0 ? "+" : ""}{formatRevenueCatChartValue(delta, chart.currency, isMoney)}</strong></div>
        <div><span>{tx("Average", "Ortalama")}</span><strong>{formatRevenueCatChartValue(average, chart.currency, isMoney)}</strong></div>
        <div><span>{tx("High", "En yüksek")}</span><strong>{formatRevenueCatChartValue(maxValue, chart.currency, isMoney)}</strong></div>
      </div>
      {!points.length ? (
        <div className="revenuecat-chart-empty">
          <strong>{tx("No chart points yet", "Henüz grafik noktası yok")}</strong>
          <span>{formatMonitoringStatus(chart.statusMessage, locale)}</span>
          <small>{tx("RevenueCat API configuration is checked, but this metric did not return drawable time-series data.", "RevenueCat API yapılandırması kontrol edildi ancak bu metrik çizilebilir zaman serisi verisi döndürmedi.")}</small>
        </div>
      ) : (
        <Suspense fallback={<div className="admin-chart-loading revenuecat-chart-loading">{tx("Loading commercial chart...", "Ticari grafik yükleniyor...")}</div>}>
          <RevenueCatEChart chart={{ ...chart, points }} />
        </Suspense>
      )}
    </article>
  );
}

export function formatRevenueCatMetricClean(metric: { value?: string; unit?: string }): string {
  const value = formatValue(metric.value);
  if (!metric.unit) {
    return value;
  }
  if (metric.unit === "EUR" || metric.unit === "USD" || metric.unit === "GBP") {
    return `${metric.unit} ${value}`;
  }
  return `${value} ${metric.unit}`;
}

export function isRevenueCatMoneyChart(chartName?: string): boolean {
  return chartName === "revenue" || chartName === "mrr";
}

export function formatRevenueCatChartValue(value: number, currency?: string, isMoney = false): string {
  const rounded = Math.abs(value) >= 10 ? Math.round(value) : Number(value.toFixed(1));
  if (!isMoney) {
    return formatValue(rounded);
  }
  if (currency === "EUR" || currency === "USD" || currency === "GBP") {
    return `${currency} ${formatValue(rounded)}`;
  }
  return formatValue(rounded);
}

export function dateInputDaysAgo(daysAgo: number): string {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  return date.toISOString().slice(0, 10);
}

export function formatMonitoringStatus(value?: string, locale: "en" | "tr" = "en"): string {
  const tr = locale === "tr";
  if (!value) {
    return tr ? "RevenueCat izleme uç noktası etkin." : "RevenueCat monitoring endpoint is active.";
  }
  if (value.includes("authentication_error") || value.includes("Invalid API key") || value.includes("401")) {
    return tr ? "Kimlik doğrulama başarısız. RevenueCat API gizli anahtarını ve proje erişimini kontrol edin." : "Authentication failed. Check the RevenueCat API secret key and project access.";
  }
  if (value.includes("parameter_error") || value.includes("400 Bad Request")) {
    return tr ? "RevenueCat istek parametrelerini reddetti. Proje kimliğini, grafik aralığını ve para birimini kontrol edin." : "RevenueCat rejected the request parameters. Check the project id, chart range, and currency.";
  }
  return value.length > 180 ? `${value.slice(0, 177)}...` : value;
}
