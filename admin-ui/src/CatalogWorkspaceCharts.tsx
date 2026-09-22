import { useCallback } from "react";
import { AdminEChart, adminChartPalette } from "./AdminEChart";
import { AdminCatalogImportJob, DashboardSummary, FoodProduct } from "./types";

type CountItem = { name: string; count: number };

const compact = (value: unknown) => {
  const candidate = typeof value === "object" && value !== null && "value" in value ? (value as { value?: unknown }).value : value;
  const numeric = Number(candidate ?? 0);
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(Number.isFinite(numeric) ? numeric : 0);
};

export function CatalogPipelineOverviewChart({ summary, locale = "en" }: { summary?: DashboardSummary | null; locale?: "tr" | "en" }) {
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    const values = [Number(summary?.rawImportedProducts ?? 0), Number(summary?.needsReviewProducts ?? 0), Number(summary?.verifiedProducts ?? 0), Number(summary?.rejectedProducts ?? 0)];
    return {
      grid: { top: 18, right: 28, bottom: 34, left: 105 }, tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted, formatter: compact }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
      yAxis: { type: "category", inverse: true, data: locale === "tr" ? ["Ham aktarım", "İnceleme", "Doğrulandı", "Reddedildi"] : ["Raw imports", "Needs review", "Verified", "Rejected"], axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text } },
      series: [{ type: "bar", barMaxWidth: 25, data: values.map((value, index) => ({ value, itemStyle: { color: ["#397ea8", "#b7832f", "#2f8f68", "#b63c4b"][index], borderRadius: [0, 5, 5, 0] }, label: { show: true, position: "right", color: palette.text, formatter: compact } })) }]
    };
  }, [locale, summary]);
  return <AdminEChart ariaLabel="Food catalog pipeline" buildOption={buildOption} className="catalog-workspace-chart" />;
}

export function CatalogRegionChart({ items }: { items: CountItem[] }) {
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    return ({
    tooltip: { trigger: "item" }, legend: { bottom: 0, textStyle: { color: palette.muted } },
    series: [{ type: "pie", radius: ["47%", "72%"], center: ["50%", "43%"], padAngle: 2, itemStyle: { borderRadius: 4, borderWidth: 2, borderColor: palette.surface }, label: { show: false }, data: items.map((item) => ({ name: item.name, value: item.count })) }],
    graphic: { type: "text", left: "center", top: "38%", style: { text: compact(items.reduce((sum, item) => sum + item.count, 0)), fill: palette.text, fontFamily: palette.fontFamily, fontSize: 20, fontWeight: 700 } }
    });
  }, [items]);
  return <AdminEChart ariaLabel="Food catalog region distribution" buildOption={buildOption} className="catalog-workspace-chart" />;
}

export function CatalogImportJobsChart({ jobs }: { jobs: AdminCatalogImportJob[] }) {
  const statuses = ["COMPLETED", "RUNNING", "PENDING", "FAILED"];
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    return ({
    grid: { top: 16, right: 24, bottom: 32, left: 82 }, tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
    yAxis: { type: "category", inverse: true, data: statuses, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text } },
    series: [{ type: "bar", barMaxWidth: 24, data: statuses.map((status, index) => ({ value: jobs.filter((job) => (job.status ?? "PENDING").toUpperCase() === status).length, itemStyle: { color: ["#2f8f68", "#397ea8", "#b7832f", "#b63c4b"][index], borderRadius: [0, 5, 5, 0] }, label: { show: true, position: "right", color: palette.text } })) }]
    });
  }, [jobs]);
  return <AdminEChart ariaLabel="Catalog import job status" buildOption={buildOption} className="catalog-workspace-chart" />;
}

export function ProductQualityWorkloadChart({ images, nutrition, rejected, locale = "en" }: { images: number; nutrition: number; rejected: number; locale?: "tr" | "en" }) {
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    const total = images + nutrition + rejected;
    return ({
    tooltip: { trigger: "item" }, legend: { bottom: 0, show: total > 0, textStyle: { color: palette.muted } },
    series: [{ type: "pie", radius: ["48%", "73%"], center: ["50%", "43%"], padAngle: 3, itemStyle: { borderRadius: 5, borderWidth: 2, borderColor: palette.surface }, label: { show: false }, data: [
      ...(total === 0 ? [{ name: "No workload", value: 1, itemStyle: { color: palette.line } }] : [
        { name: locale === "tr" ? "Görsel inceleme" : "Image review", value: images, itemStyle: { color: "#8065a0" } },
        { name: locale === "tr" ? "Besin inceleme" : "Nutrition review", value: nutrition, itemStyle: { color: "#2f8f68" } },
        { name: locale === "tr" ? "Reddedildi" : "Rejected", value: rejected, itemStyle: { color: "#b63c4b" } }
      ])
    ] }],
    graphic: { type: "text", left: "center", top: "38%", style: { text: compact(total), fill: palette.text, fontFamily: palette.fontFamily, fontSize: 20, fontWeight: 700 } }
    });
  }, [images, locale, nutrition, rejected]);
  return <AdminEChart ariaLabel="Product quality workload" buildOption={buildOption} className="product-quality-workload-chart" />;
}

export function ProductReviewHealthChart({ products, locale = "en" }: { products: FoodProduct[]; locale?: "tr" | "en" }) {
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    const bands = [
      { name: locale === "tr" ? "Yüksek kalite" : "High quality", value: products.filter((item) => Number(item.qualityScore ?? 0) >= 80).length, color: "#2f8f68" },
      { name: locale === "tr" ? "İnceleme" : "Review", value: products.filter((item) => Number(item.qualityScore ?? 0) >= 50 && Number(item.qualityScore ?? 0) < 80).length, color: "#b7832f" },
      { name: locale === "tr" ? "Düşük kalite" : "Low quality", value: products.filter((item) => Number(item.qualityScore ?? 0) < 50).length, color: "#b63c4b" }
    ];
    return {
      grid: { top: 18, right: 32, bottom: 34, left: 108 }, tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
      yAxis: { type: "category", inverse: true, data: bands.map((item) => item.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text } },
      series: [{ type: "bar", barMaxWidth: 25, data: bands.map((item) => ({ value: item.value, itemStyle: { color: item.color, borderRadius: [0, 5, 5, 0] }, label: { show: true, position: "right", color: palette.text } })) }]
    };
  }, [locale, products]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Ürün inceleme kalite dağılımı" : "Product review quality distribution"} buildOption={buildOption} className="product-review-health-chart" />;
}

export function ContributionQueueChart({ needsAction, highRisk, unassigned, locale = "en" }: { needsAction: number; highRisk: number; unassigned: number; locale?: "tr" | "en" }) {
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    const items = [
      { name: locale === "tr" ? "İşlem bekleyen" : "Needs action", value: needsAction, color: "#8065a0" },
      { name: locale === "tr" ? "Yüksek risk" : "High risk", value: highRisk, color: "#b63c4b" },
      { name: locale === "tr" ? "Atanmamış" : "Unassigned", value: unassigned, color: "#b7832f" }
    ];
    if (items.every((item) => item.value === 0)) return {
      graphic: { type: "text", left: "center", top: "middle", style: { text: locale === "tr" ? "Bu filtre için bekleyen iş yok" : "No pending work for this filter", fill: palette.muted, fontFamily: palette.fontFamily, fontSize: 14, fontWeight: 600 } }
    };
    return {
      grid: { top: 16, right: 38, bottom: 32, left: 112 }, tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      xAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.muted }, splitLine: { lineStyle: { color: palette.line, type: "dashed" } } },
      yAxis: { type: "category", inverse: true, data: items.map((item) => item.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: palette.text } },
      series: [{ type: "bar", barMaxWidth: 25, data: items.map((item) => ({ value: item.value, itemStyle: { color: item.color, borderRadius: [0, 5, 5, 0] }, label: { show: true, position: "right", color: palette.text } })) }]
    };
  }, [highRisk, locale, needsAction, unassigned]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Katkı inceleme iş yükü" : "Contribution review workload"} buildOption={buildOption} className="catalog-workspace-chart" />;
}

export function DuplicateResolutionChart({ resolved, attention, locale = "en" }: { resolved: number; attention: number; locale?: "tr" | "en" }) {
  const buildOption = useCallback(() => {
    const palette = adminChartPalette();
    const total = resolved + attention;
    return {
      tooltip: { trigger: "item" }, legend: { bottom: 0, show: total > 0, textStyle: { color: palette.muted } },
      series: [{ type: "pie", radius: ["48%", "73%"], center: ["50%", "43%"], padAngle: 3, itemStyle: { borderRadius: 5, borderWidth: 2, borderColor: palette.surface }, label: { show: false }, data: total === 0
        ? [{ name: locale === "tr" ? "Kayıt yok" : "No records", value: 1, itemStyle: { color: palette.line } }]
        : [
          { name: locale === "tr" ? "Karar verildi" : "Resolved", value: resolved, itemStyle: { color: "#2f8f68" } },
          { name: locale === "tr" ? "Karar bekliyor" : "Needs attention", value: attention, itemStyle: { color: "#b7832f" } }
        ] }],
      graphic: { type: "text", left: "center", top: "38%", style: { text: compact(total), fill: palette.text, fontFamily: palette.fontFamily, fontSize: 20, fontWeight: 700 } }
    };
  }, [attention, locale, resolved]);
  return <AdminEChart ariaLabel={locale === "tr" ? "Tekrarlanan ürün karar durumu" : "Canonical duplicate decision status"} buildOption={buildOption} className="catalog-workspace-chart" />;
}
