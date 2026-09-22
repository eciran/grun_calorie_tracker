import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";

export type OwnerDailyOperations = {
  backendErrorGroups: number; backendErrorOccurrences: number;
  financialApprovalRequests: number; operationalApprovalRequests: number; pendingApprovals: number;
  approvedToday: number; rejectedToday: number; sentAlerts: number; failedAlerts: number;
  newErrorGroups: number; investigatingErrorGroups: number; resolvedErrorGroups: number; reopenedErrorGroups: number;
};

export function OwnerOperationsLoadChart({ summary, tr }: { summary: OwnerDailyOperations; tr: boolean }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const p = adminChartPalette();
    const rows = [
      [tr ? "Backend olayları" : "Backend occurrences", summary.backendErrorOccurrences],
      [tr ? "Hata grupları" : "Error groups", summary.backendErrorGroups],
      [tr ? "Onay talepleri" : "Approval requests", summary.financialApprovalRequests + summary.operationalApprovalRequests],
      [tr ? "Bekleyen onaylar" : "Pending approvals", summary.pendingApprovals]
    ] as const;
    return {
      aria: { enabled: true },
      grid: { top: 12, right: 34, bottom: 20, left: 132 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, textStyle: { fontFamily: p.fontFamily } },
      xAxis: { type: "value", minInterval: 1, axisLabel: { color: p.muted, fontFamily: p.fontFamily }, splitLine: { lineStyle: { color: p.line, type: "dashed" } } },
      yAxis: { type: "category", data: rows.map(row => row[0]), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: p.text, fontFamily: p.fontFamily } },
      series: [{ type: "bar", name: tr ? "Kayıt" : "Records", data: rows.map(row => row[1]), barMaxWidth: 24, label: { show: true, position: "right", color: p.text, fontFamily: p.fontFamily }, itemStyle: { color: p.primary, borderRadius: [0, 5, 5, 0] } }]
    };
  }, [summary, tr]);
  return <AdminEChart ariaLabel={tr ? "Günlük operasyon yükü" : "Daily operational load"} buildOption={buildOption} className="owner-alert-chart" />;
}

export function OwnerWorkflowChart({ summary, tr }: { summary: OwnerDailyOperations; tr: boolean }) {
  const buildOption = useCallback((): EChartsCoreOption => {
    const p = adminChartPalette();
    return {
      aria: { enabled: true },
      color: [p.primary, p.accent, p.danger, "#8d70ad"],
      grid: { top: 44, right: 20, bottom: 34, left: 42 },
      legend: { top: 4, textStyle: { color: p.muted, fontFamily: p.fontFamily }, itemWidth: 12, itemHeight: 8 },
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, textStyle: { fontFamily: p.fontFamily } },
      xAxis: { type: "category", data: [tr ? "Hata grupları" : "Error groups", tr ? "Onaylar" : "Approvals", tr ? "Teslimat" : "Delivery"], axisTick: { show: false }, axisLine: { lineStyle: { color: p.line } }, axisLabel: { color: p.text, fontFamily: p.fontFamily } },
      yAxis: { type: "value", minInterval: 1, axisLabel: { color: p.muted, fontFamily: p.fontFamily }, splitLine: { lineStyle: { color: p.line, type: "dashed" } } },
      series: [
        { type: "bar", stack: "state", name: tr ? "Yeni / onaylandı / gönderildi" : "New / approved / sent", data: [summary.newErrorGroups, summary.approvedToday, summary.sentAlerts], barMaxWidth: 42 },
        { type: "bar", stack: "state", name: tr ? "İnceleniyor / bekliyor" : "Investigating / pending", data: [summary.investigatingErrorGroups, summary.pendingApprovals, 0], barMaxWidth: 42 },
        { type: "bar", stack: "state", name: tr ? "Yeniden açıldı / reddedildi / başarısız" : "Reopened / rejected / failed", data: [summary.reopenedErrorGroups, summary.rejectedToday, summary.failedAlerts], barMaxWidth: 42 },
        { type: "bar", stack: "state", name: tr ? "Çözüldü" : "Resolved", data: [summary.resolvedErrorGroups, 0, 0], barMaxWidth: 42, itemStyle: { borderRadius: [4, 4, 0, 0] } }
      ]
    };
  }, [summary, tr]);
  return <AdminEChart ariaLabel={tr ? "Günlük iş akışı sonuçları" : "Daily workflow outcomes"} buildOption={buildOption} className="owner-alert-chart" />;
}
