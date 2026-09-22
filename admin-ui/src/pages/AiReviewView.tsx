import { FormEvent, lazy, Suspense, useEffect, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminAccessProfile, AdminApprovalRequest, AiMealDraft, AiRequestInspection, AiMonitoringSummary, AiOperationsPolicy, AiQuotaRefundResponse } from "../types";

import { CollapsiblePanel, DataTable, EmptyState, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { AdminTargetContext, ApprovalSubmissionNotice, Badge, ConfirmDialog, DetailItem, TargetAwareValue, TargetContextBanner, combineStates, formatDate, formatValue, isTargetMatch, shortFeature, submitAdminApproval, useEndpoint } from "./../admin/shared";
import { readUserRouteContext, sectionPaths, UserRouteContext } from "../admin/navigation";
import { useAdminLocale } from "../admin/locale";
import { AiOverviewPanel } from "../AiOverviewPanel";

export const AiOutcomeChart = lazy(() => import("../AiOperationsCharts").then((module) => ({ default: module.AiOutcomeChart })));

export const AiLatencyChart = lazy(() => import("../AiOperationsCharts").then((module) => ({ default: module.AiLatencyChart })));

export const AiEconomicsChart = lazy(() => import("../AiOperationsCharts").then((module) => ({ default: module.AiEconomicsChart })));
const AiPhotoOutcomeChart = lazy(() => import("../AiOperationsCharts").then((module) => ({ default: module.AiPhotoOutcomeChart })));
const AiCostWorkbench = lazy(() => import("../AiCostWorkbench"));

export const AI_REQUEST_TYPES = [
  "VOICE_FOOD_LOG",
  "PHOTO_MEAL_LOG",
  "AI_RECIPE_GENERATION",
  "AI_MEAL_PREPARATION_GUIDE",
  "AI_NUTRITION_PLAN",
  "AI_WORKOUT_PLAN",
  "AI_DAILY_INSIGHT",
  "AI_WEEKLY_INSIGHT"
];

export const AI_REQUEST_STATUSES = ["DRAFT_CREATED", "CONFIRMED", "REJECTED", "FAILED"];
export type AiOperationsMode = "overview" | "requests" | "policy";

export type AiRequestRouteState = {
  requestType: string;
  status: string;
  refundableOnly: boolean;
  page: number;
  size: number;
  requestId: number | null;
  userContext: UserRouteContext | null;
};

export function AiReviewView({ mode, onError, targetContext, onClearTarget, accessProfile }: { mode: AiOperationsMode; onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void; accessProfile: AdminAccessProfile | null }) {
  const { locale } = useAdminLocale();
  const [initialRequestRoute] = useState(() => readAiRequestRouteState());
  const [userContext, setUserContext] = useState(() => initialRequestRoute.userContext);
  const [requestType, setRequestType] = useState(initialRequestRoute.requestType);
  const [status, setStatus] = useState(initialRequestRoute.status);
  const [refundableOnly, setRefundableOnly] = useState(initialRequestRoute.refundableOnly);
  const [page, setPage] = useState(initialRequestRoute.page);
  const [pageSize, setPageSize] = useState(initialRequestRoute.size);
  const [summaryWindowHours, setSummaryWindowHours] = useState(24);
  const [policyActionState, setPolicyActionState] = useState<LoadState>("idle");
  const [rollbackConfirmationOpen, setRollbackConfirmationOpen] = useState(false);
  const [policyDraft, setPolicyDraft] = useState({ circuitOpen: false, failureRateThreshold: "0.20", rejectionRateThreshold: "0.40", maxTokensPer24Hours: "1000000", maxCostPer24Hours: "20", costCurrency: "USD", activeModel: "", activePhotoProvider: "OPENAI", activePhotoModel: "", activePromptVersion: "", reason: "" });
  const [smokeState, setSmokeState] = useState<LoadState>("idle");
  const [refundState, setRefundState] = useState<LoadState>("idle");
  const [smokeResult, setSmokeResult] = useState<string | null>(null);
  const [refundDraft, setRefundDraft] = useState<{ item: AiMealDraft; amount: string; reason: string } | null>(null);
  const [refundRejectDraft, setRefundRejectDraft] = useState<{ item: AiMealDraft; reason: string } | null>(null);
  const [refundResult, setRefundResult] = useState<AiQuotaRefundResponse | null>(null);
  const [refundApprovalNotice, setRefundApprovalNotice] = useState<{ request: AdminApprovalRequest; message: string } | null>(null);
  const [inspection, setInspection] = useState<AiRequestInspection | null>(null);
  const [inspectionState, setInspectionState] = useState<LoadState>("idle");
  const [selectedInspectionId, setSelectedInspectionId] = useState<number | null>(initialRequestRoute.requestId);
  const [requestFiltersOpen, setRequestFiltersOpen] = useState(false);
  const path = buildAiOperationsPath({ requestType, status, refundableOnly, page, size: pageSize, userId: userContext?.userId });
  const summaryPath = `/api/v1/admin/ai/requests/summary?windowHours=${summaryWindowHours}`;
  const policyPath = "/api/v1/admin/ai/monitoring/policy";
  const { data, state, reload } = useEndpoint<PageResponse<AiMealDraft>>(path, onError, mode === "requests");
  const { data: summary, state: summaryState, reload: reloadSummary } = useEndpoint<AiMonitoringSummary>(summaryPath, onError, mode === "overview");
  const { data: policy, state: policyState, reload: reloadPolicy } = useEndpoint<AiOperationsPolicy>(policyPath, onError, mode !== "requests");
  const rows = data?.content ?? [];
  const hasAiOutcomes = (summary?.requestStatuses ?? []).some((item) => Number(item.requestCount ?? 0) > 0);
  const hasAiLatency = [summary?.latencyP50Ms, summary?.latencyP95Ms, summary?.latencyP99Ms].some((value) => Number(value ?? 0) > 0);
  const hasAiEconomics = new Set([
    ...Object.keys(summary?.estimatedCostByCurrency ?? {}),
    ...Object.keys(summary?.subscriptionRevenueByCurrency ?? {})
  ]).size > 0;
  const photoStatuses = (summary?.requestStatuses ?? []).filter((item) => item.requestType === "PHOTO_MEAL_LOG");
  const photoSegments = (summary?.segments ?? []).filter((item) => item.requestType === "PHOTO_MEAL_LOG");
  const photoRequestCount = photoStatuses.reduce((total, item) => total + safeNumber(item.requestCount), 0);
  const photoTokenCount = photoStatuses.reduce((total, item) => total + safeNumber(item.totalTokens), 0);
  const photoCostByCurrency = photoSegments.reduce<Record<string, number>>((totals, item) => {
    const currency = item.costCurrency || policy?.photoCostCurrency || "USD";
    totals[currency] = (totals[currency] ?? 0) + safeNumber(item.estimatedCost);
    return totals;
  }, {});
  const workspaceState = mode === "overview"
    ? combineStates([summaryState, policyState])
    : mode === "requests"
      ? combineStates([state, inspectionState, refundState])
      : combineStates([policyState, policyActionState, smokeState]);
  const focusedRequestId = targetContext?.targetType === "AI_REQUEST" ? targetContext.targetId : undefined;
  useEffect(() => {
    if (!policy) return;
    setPolicyDraft({
      circuitOpen: Boolean(policy.circuitOpen),
      failureRateThreshold: String(policy.failureRateThreshold ?? 0.2),
      rejectionRateThreshold: String(policy.rejectionRateThreshold ?? 0.4),
      maxTokensPer24Hours: String(policy.maxTokensPer24Hours ?? 1000000),
      maxCostPer24Hours: String(policy.maxCostPer24Hours ?? 20),
      costCurrency: policy.costCurrency ?? "USD",
      activeModel: policy.activeModel ?? "",
      activePhotoProvider: policy.activePhotoProvider ?? "OPENAI",
      activePhotoModel: policy.activePhotoModel ?? policy.activeModel ?? "",
      activePromptVersion: policy.activePromptVersion ?? "",
      reason: ""
    });
  }, [policy]);

  useEffect(() => {
    if (targetContext?.targetType !== "AI_REQUEST" || !targetContext.targetId) return;
    setStatus("");
    setRefundableOnly(false);
    setPage(0);
  }, [targetContext?.targetType, targetContext?.targetId]);

  useEffect(() => {
    if (mode !== "requests") return;
    const search = new URLSearchParams();
    if (requestType) search.set("requestType", requestType);
    if (status) search.set("status", status);
    if (refundableOnly) search.set("refundableOnly", "true");
    if (page > 0) search.set("page", String(page));
    if (pageSize !== 25) search.set("size", String(pageSize));
    if (userContext?.userId) search.set("userId", String(userContext.userId));
    if (userContext?.userEmail) search.set("userEmail", userContext.userEmail);
    if (selectedInspectionId) search.set("requestId", String(selectedInspectionId)); else search.delete("requestId");
    window.history.replaceState(null, "", `${sectionPaths.aiRequests}${search.size ? `?${search.toString()}` : ""}`);
  }, [mode, requestType, status, refundableOnly, page, pageSize, userContext, selectedInspectionId]);

  useEffect(() => {
    if (mode === "requests" && selectedInspectionId && inspectionState === "idle" && inspection?.requestId !== selectedInspectionId) {
      void inspectAiRequest({ requestId: selectedInspectionId });
    }
  }, [mode, selectedInspectionId, inspectionState, inspection?.requestId]);

  function resetFilters() {
    setRequestType("");
    setStatus("");
    setRefundableOnly(false);
    setPage(0);
  }

  function clearUserContext() {
    setUserContext(null);
    setPage(0);
  }

  async function saveOperationsPolicy(event: FormEvent) {
    event.preventDefault();
    if (policy?.version == null || policyDraft.reason.trim().length < 8) {
      onError("A reason of at least 8 characters is required.");
      return;
    }
    setPolicyActionState("loading");
    try {
      await request<AiOperationsPolicy>(policyPath, {
        method: "PUT",
        body: {
          version: policy.version,
          circuitOpen: policyDraft.circuitOpen,
          failureRateThreshold: Number(policyDraft.failureRateThreshold),
          rejectionRateThreshold: Number(policyDraft.rejectionRateThreshold),
          maxTokensPer24Hours: Number(policyDraft.maxTokensPer24Hours),
          maxCostPer24Hours: Number(policyDraft.maxCostPer24Hours),
          costCurrency: policyDraft.costCurrency.trim().toUpperCase(),
          activeModel: policyDraft.activeModel.trim(),
          activePhotoProvider: policyDraft.activePhotoProvider,
          activePhotoModel: policyDraft.activePhotoModel.trim(),
          activePromptVersion: policyDraft.activePromptVersion.trim(),
          reason: policyDraft.reason.trim()
        }
      });
      setPolicyActionState("ready");
      await reloadPolicy();
      await reloadSummary();
    } catch (error) {
      setPolicyActionState("error");
      onError(formatRequestError(error));
      await reloadPolicy();
    }
  }

  async function rollbackOperationsDeployment() {
    if (policy?.version == null || policyDraft.reason.trim().length < 8) {
      onError("Enter an operational reason before rollback.");
      return;
    }
    setPolicyActionState("loading");
    try {
      await request<AiOperationsPolicy>(`${policyPath}/rollback`, {
        method: "POST",
        body: { version: policy.version, reason: policyDraft.reason.trim() }
      });
      setRollbackConfirmationOpen(false);
      setPolicyActionState("ready");
      await reloadPolicy();
      await reloadSummary();
    } catch (error) {
      setRollbackConfirmationOpen(false);
      setPolicyActionState("error");
      onError(formatRequestError(error));
      await reloadPolicy();
    }
  }
  async function runProviderSmoke(smokeRequestType: "VOICE_FOOD_LOG" | "PHOTO_MEAL_LOG") {
    setSmokeState("loading");
    setSmokeResult(null);
    try {
      const result = await request<unknown>(`/api/v1/admin/system/ai-provider/smoke?requestType=${encodeURIComponent(smokeRequestType)}`, { method: "POST" });
      setSmokeResult(JSON.stringify(result, null, 2));
      setSmokeState("ready");
    } catch (error) {
      setSmokeState("error");
      onError(formatRequestError(error));
    }
  }

  async function inspectAiRequest(item: AiMealDraft) {
    const requestId = aiRequestId(item);
    if (!requestId) return;
    setSelectedInspectionId(requestId);
    setInspectionState("loading");
    try {
      const result = await request<AiRequestInspection>(`/api/v1/admin/ai/meal-drafts/${requestId}/inspection`);
      setInspection(result);
      setInspectionState("ready");
    } catch (error) {
      setInspectionState("error");
      onError(formatRequestError(error));
    }
  }
  function closeInspection() {
    setInspection(null);
    setInspectionState("idle");
    setSelectedInspectionId(null);
  }
  async function refundSelectedAiQuota(event: FormEvent) {
    event.preventDefault();
    if (!refundDraft) return;
    const requestId = aiRequestId(refundDraft.item);
    const amount = Number(refundDraft.amount);
    const reason = refundDraft.reason.trim();
    const refundableAmount = safeNumber(refundDraft.item.refundableAmount);
    if (!requestId || !Number.isFinite(amount) || amount <= 0 || amount > refundableAmount || !reason) {
      onError("Refund amount must be positive, must not exceed refundable amount, and reason is required.");
      return;
    }
    setRefundState("loading");
    try {
      const approval = await submitAdminApproval("AI_QUOTA_REFUND", String(requestId), { amount, reason }, reason);
      setRefundApprovalNotice({ request: approval, message: "AI quota refund is pending approval. The user's quota has not changed yet." });
      setRefundDraft(null);
      setRefundState("ready");
    } catch (error) {
      setRefundState("error");
      onError(formatRequestError(error));
      await reload();
      await reloadSummary();
    }
  }

  async function rejectSelectedAiQuotaRefund(event: FormEvent) {
    event.preventDefault();
    if (!refundRejectDraft) return;
    const requestId = aiRequestId(refundRejectDraft.item);
    const reason = refundRejectDraft.reason.trim();
    if (!requestId || !reason) {
      onError("A reason is required to reject the refund request.");
      return;
    }
    setRefundState("loading");
    try {
      const result = await request<AiQuotaRefundResponse>(`/api/v1/admin/ai/meal-drafts/${requestId}/quota-refund/reject`, {
        method: "POST",
        body: { reason }
      });
      setRefundResult(result);
      setRefundRejectDraft(null);
      setRefundState("ready");
      await reload();
      await reloadSummary();
    } catch (error) {
      setRefundState("error");
      onError(formatRequestError(error));
      await reload();
      await reloadSummary();
    }
  }

  return (
    <div className="stack ai-operations-view ai-dashboard-overview">
      <SectionToolbar title={mode === "overview" ? "AI operations overview" : mode === "requests" ? "AI requests and OCR review" : (locale === "tr" ? "AI sağlayıcı ve güvenilirlik politikası" : "AI provider and reliability policy")} description={mode === "policy" ? (locale === "tr" ? "Model yönlendirmesini, güvenilirlik eşiklerini ve 24 saatlik bütçe sınırlarını tek çalışma alanından yönetin." : "Manage model routing, reliability thresholds, and 24-hour budget guardrails from one workspace.") : undefined} state={workspaceState} onReload={() => { if (mode === "requests") void reload(); else if (mode === "overview") void reloadSummary(); else void reloadPolicy(); }}>
      </SectionToolbar>
      {mode === "requests" && targetContext && <TargetContextBanner context={targetContext} onClear={onClearTarget} />}
      {mode === "requests" && userContext && <div className="target-context-banner"><div><span>{locale === "tr" ? "KULLANICI BAĞLAMI" : "USER CONTEXT"}</span><strong>{userContext.userEmail ?? `${locale === "tr" ? "Kullanıcı" : "User"} #${userContext.userId}`}</strong><small>{locale === "tr" ? "AI istek listesi bu kullanıcıyla filtrelenir." : "The AI request list is filtered to this user."}</small></div><button className="ghost-button" type="button" onClick={clearUserContext}>{locale === "tr" ? "Kullanıcı bağlamını temizle" : "Clear user context"}</button></div>}

      {mode === "overview" && <><AiOverviewPanel defaultOpen className="ai-overview-summary" title={locale === "tr" ? "AI izleme özeti" : "AI monitoring summary"}>
        <div className="ai-monitoring-toolbar">
          <div>
            <strong>{summaryWindowLabel(summaryWindowHours)}</strong>
            <small>Generated {formatDate(summary?.generatedAt)} | Window start {formatDate(summary?.windowStart)}</small>
          </div>
          <div className="segmented-control" role="group" aria-label="AI monitoring window">
            {[24, 168, 744].map((hours) => (
              <button className={summaryWindowHours === hours ? "active" : ""} key={hours} type="button" onClick={() => setSummaryWindowHours(hours)}>{summaryWindowLabel(hours)}</button>
            ))}
          </div>
        </div>
        <div className="metric-grid compact-grid">
          <MetricCard label={locale === "tr" ? "Toplam istek" : "Total requests"} value={formatValue(summary?.totalRequests)} hint={locale === "tr" ? "Seçili dönemdeki tüm AI istekleri" : "All controlled AI requests"} />
          <MetricCard label={locale === "tr" ? "Tüketilen kota" : "Quota consumed"} value={formatValue(summary?.quotaConsumedAmount)} hint={`${formatValue(summary?.quotaRefundedAmount)} refunded`} />
          <MetricCard label={locale === "tr" ? "Token kullanımı" : "Tokens"} value={formatValue(summary?.totalTokens)} hint={`${formatValue(summary?.promptTokens)} input / ${formatValue(summary?.completionTokens)} output`} />
          <MetricCard label={locale === "tr" ? "Başarı / ret oranı" : "Success / rejection"} value={`${formatFailureRate(summary?.successRate)} / ${formatFailureRate(summary?.rejectionRate)}`} hint={locale === "tr" ? "İncelenebilir çıktı ve kullanıcı ret oranları" : "Reviewable output and user rejection rates"} />
        </div>
      </AiOverviewPanel>

      {summary && <Suspense fallback={<div className="admin-chart-loading">{locale === "tr" ? "Maliyet analizi yükleniyor…" : "Loading cost analysis…"}</div>}><AiCostWorkbench summary={summary} policy={policy ?? null} /></Suspense>}

      <AiOverviewPanel title={locale === "tr" ? "Fotoğraf analizi" : "Photo analysis economics"} description={locale === "tr" ? "Fotoğraftan öğün analizinin sonuçları, token kullanımı ve etkin model fiyatları." : "Photo meal analysis outcomes, token usage and active model pricing."} className="ai-photo-panel">
        <div className="ai-photo-layout">
          <div className="ai-photo-visual">{summary && photoRequestCount > 0 ? <Suspense fallback={<div className="admin-chart-loading">Loading...</div>}><AiPhotoOutcomeChart summary={summary} /></Suspense> : <EmptyState title={locale === "tr" ? "Henüz fotoğraf isteği yok" : "No photo requests yet"} message={locale === "tr" ? "Seçili dönemde sonuç kaydedilmedi." : "No outcomes recorded in this window."} />}</div>
          <div className="ai-photo-facts">
        <div className="metric-grid compact-grid">
          <MetricCard label={locale === "tr" ? "Etkin fotoğraf sağlayıcısı" : "Active photo provider"} value={policy?.activePhotoProvider ?? policyDraft.activePhotoProvider} hint={policy?.activePhotoModel ?? policyDraft.activePhotoModel} />
          <MetricCard label={locale === "tr" ? "Fotoğraf token kullanımı" : "Photo tokens"} value={formatValue(photoTokenCount)} hint={locale === "tr" ? "Toplam girdi ve çıktı tokenları" : "Input and output tokens combined"} />
          <MetricCard label={locale === "tr" ? "Fotoğraf tahmini maliyeti" : "Photo estimated cost"} value={formatCurrencyBreakdown(photoCostByCurrency)} hint={locale === "tr" ? "Yalnızca fotoğraftan öğün analizi" : "Only PHOTO_MEAL_LOG requests"} />
          <MetricCard label={locale === "tr" ? "Girdi token fiyatı" : "Photo input rate"} value={formatAiCostAmount(policy?.photoInputTokenCostPer1m, policy?.photoCostCurrency)} hint={locale === "tr" ? "1 milyon girdi tokenı için" : "Per 1M input tokens"} />
          <MetricCard label={locale === "tr" ? "Çıktı token fiyatı" : "Photo output rate"} value={formatAiCostAmount(policy?.photoOutputTokenCostPer1m, policy?.photoCostCurrency)} hint={locale === "tr" ? "1 milyon çıktı tokenı için" : "Per 1M output tokens"} />
        </div>
          <a className="ghost-button" href={sectionPaths.aiRequests + "?requestType=PHOTO_MEAL_LOG"}>{locale === "tr" ? "Fotoğraf isteklerini incele" : "Review photo requests"} →</a>
          </div>
        </div>
      </AiOverviewPanel>

      <div className="ai-analytics-chart-grid">
        <AiOverviewPanel title={locale === "tr" ? "İstek sonuçları" : "Request outcomes"} description={locale === "tr" ? "Özellik bazında istek sonuçları." : "Controlled AI request results grouped by feature. Exact values remain available in the status table."}>
          {summary && hasAiOutcomes ? (
            <Suspense fallback={<div className="admin-chart-loading ai-operations-chart-loading">Loading outcome chart...</div>}>
              <AiOutcomeChart summary={summary} />
            </Suspense>
          ) : <EmptyState title={locale === "tr" ? "AI isteği bulunamadı" : "No AI request activity"} message={locale === "tr" ? "Seçili dönemde istek sonucu kaydedilmedi." : "No request outcome was recorded for this monitoring window."} />}
        </AiOverviewPanel>
        <AiOverviewPanel title={locale === "tr" ? "Sağlayıcı gecikmesi" : "Provider latency"} description={`Percentiles for completed provider calls. ${formatValue(summary?.timeoutCount)} timeout failure(s) in this window.`}>
          {summary && hasAiLatency ? (
            <Suspense fallback={<div className="admin-chart-loading ai-operations-chart-loading">Loading latency chart...</div>}>
              <AiLatencyChart summary={summary} />
            </Suspense>
          ) : <EmptyState title={locale === "tr" ? "Gecikme ölçümü yok" : "No latency samples"} message={locale === "tr" ? "Seçili dönemde sağlayıcı gecikme ölçümü bulunamadı." : "No usable provider latency sample was returned for this window."} />}
        </AiOverviewPanel>
        <AiOverviewPanel className="ai-economics-chart-panel" title={locale === "tr" ? "AI maliyeti ve abonelik geliri" : "AI cost and subscription revenue"} description={locale === "tr" ? "Maliyet ve gelir aynı para birimi içinde karşılaştırılır." : "Each currency is shown independently. The browser never combines currencies into one total."}>

          {summary && hasAiEconomics ? (
            <Suspense fallback={<div className="admin-chart-loading ai-operations-chart-loading">Loading economics chart...</div>}>
              <AiEconomicsChart summary={summary} />
            </Suspense>
          ) : <EmptyState title={locale === "tr" ? "Karşılaştırılabilir maliyet verisi yok" : "No comparable economics"} message={locale === "tr" ? "Bu dönemde AI maliyeti veya abonelik geliri bulunamadı." : "No currency-specific AI cost or processed subscription revenue was returned."} />}

        </AiOverviewPanel>
      </div>

      {(summary?.alerts ?? []).length > 0 && <AiOverviewPanel title="Operational alerts">
        <div className="operations-alert-list">
          {(summary?.alerts ?? []).map((alert) => <div className={`operations-alert ${alert.severity === "CRITICAL" ? "critical" : "warning"}`} key={`${alert.code}-${alert.requestType ?? "all"}-${alert.currency ?? "all"}`}>
            <Badge value={alert.severity ?? "WARNING"} tone={alert.severity === "CRITICAL" ? "danger" : "warn"} />
            <div><strong>{shortFeature(alert.code)}</strong><small>{alert.message}{alert.requestType ? ` | ${humanizeAiRequestType(alert.requestType)}` : ""}{alert.currency ? ` | ${alert.currency}` : ""}</small></div>
          </div>)}
        </div>
      </AiOverviewPanel>}</>}

      {mode === "policy" && <div className="ai-policy-page">
        <section className={`ai-policy-status-banner${policyDraft.circuitOpen ? " circuit-open" : ""}`}>
          <div><span>{locale === "tr" ? "CANLI POLİTİKA" : "LIVE POLICY"}</span><h3>{locale === "tr" ? "AI çalışma sınırları" : "AI operating guardrails"}</h3><p>{locale === "tr" ? "Dağıtım, maliyet ve hata davranışı bu politika sürümüyle kontrol ediliyor." : "Deployment, cost, and failure behaviour are controlled by this policy version."}</p></div>
          <Badge value={policyDraft.circuitOpen ? (locale === "tr" ? "DEVRE KESİCİ AÇIK" : "CIRCUIT OPEN") : (locale === "tr" ? "ÇALIŞIYOR" : "OPERATING")} tone={policyDraft.circuitOpen ? "danger" : "good"} />
        </section>

        <div className="ai-policy-summary-grid">
          <article><span>{locale === "tr" ? "Genel model" : "General model"}</span><strong>{policyDraft.activeModel || "—"}</strong><small>{policyDraft.activePromptVersion || (locale === "tr" ? "Prompt sürümü yok" : "No prompt version")}</small></article>
          <article><span>{locale === "tr" ? "Fotoğraf rotası" : "Photo route"}</span><strong>{policyDraft.activePhotoProvider || "—"}</strong><small>{policyDraft.activePhotoModel || "—"}</small></article>
          <article><span>{locale === "tr" ? "24 saatlik token sınırı" : "24-hour token cap"}</span><strong>{formatValue(Number(policyDraft.maxTokensPer24Hours))}</strong><small>{locale === "tr" ? "Tüm kontrollü AI istekleri" : "All controlled AI requests"}</small></article>
          <article><span>{locale === "tr" ? "24 saatlik maliyet sınırı" : "24-hour cost cap"}</span><strong>{formatAiCostAmount(Number(policyDraft.maxCostPer24Hours), policyDraft.costCurrency)}</strong><small>{policy?.rollbackAvailable ? (locale === "tr" ? "Geri alma hazır" : "Rollback available") : (locale === "tr" ? "Geri alma kaydı yok" : "No rollback snapshot")}</small></article>
        </div>

        <form className="ai-policy-workbench" onSubmit={saveOperationsPolicy}>
          <section className="ai-policy-card ai-policy-routing-card">
            <div className="ai-policy-card-heading"><div><span>01</span><h3>{locale === "tr" ? "Model yönlendirmesi" : "Model routing"}</h3><p>{locale === "tr" ? "Genel AI akışı ile fotoğraf analizini ayrı olarak yönetin." : "Route general AI workloads and photo analysis independently."}</p></div></div>
            <div className="ai-policy-field-grid">
              <label>{locale === "tr" ? "Genel model" : "General model"}<input value={policyDraft.activeModel} onChange={(event) => setPolicyDraft((current) => ({ ...current, activeModel: event.target.value }))} /><small>{locale === "tr" ? "Fotoğraf analizi dışındaki AI özellikleri." : "Used by AI features other than photo analysis."}</small></label>
              <label>{locale === "tr" ? "Prompt sürümü" : "Prompt version"}<input value={policyDraft.activePromptVersion} onChange={(event) => setPolicyDraft((current) => ({ ...current, activePromptVersion: event.target.value }))} /></label>
              <label>{locale === "tr" ? "Fotoğraf sağlayıcısı" : "Photo provider"}<select value={policyDraft.activePhotoProvider} onChange={(event) => setPolicyDraft((current) => ({ ...current, activePhotoProvider: event.target.value }))}><option value="OPENAI">OpenAI</option><option value="GEMINI">Gemini</option><option value="HTTP_JSON">HTTP JSON</option><option value="LOG">Log (non-production)</option></select><small>{locale === "tr" ? "Sağlayıcı model adından tahmin edilmez." : "The provider is explicit, not inferred from the model name."}</small></label>
              <label>{locale === "tr" ? "Fotoğraf analiz modeli" : "Photo analysis model"}<input value={policyDraft.activePhotoModel} onChange={(event) => setPolicyDraft((current) => ({ ...current, activePhotoModel: event.target.value }))} /><small>PHOTO_MEAL_LOG</small></label>
            </div>
          </section>

          <section className="ai-policy-card ai-policy-reliability-card">
            <div className="ai-policy-card-heading"><div><span>02</span><h3>{locale === "tr" ? "Güvenilirlik eşikleri" : "Reliability thresholds"}</h3><p>{locale === "tr" ? "Hata ve ret oranlarının ne zaman operasyonel uyarı oluşturacağını belirleyin." : "Define when failure and rejection rates become operational alerts."}</p></div></div>
            <label className={`ai-policy-circuit-toggle${policyDraft.circuitOpen ? " active" : ""}`}><input checked={policyDraft.circuitOpen} onChange={(event) => setPolicyDraft((current) => ({ ...current, circuitOpen: event.target.checked }))} type="checkbox" /><span><strong>{locale === "tr" ? "Devre kesici" : "Circuit breaker"}</strong><small>{policyDraft.circuitOpen ? (locale === "tr" ? "Yeni AI çağrıları durdurulur." : "New AI calls are stopped.") : (locale === "tr" ? "AI çağrıları politika sınırları içinde çalışır." : "AI calls operate within policy limits.")}</small></span><b>{policyDraft.circuitOpen ? (locale === "tr" ? "Açık" : "Open") : (locale === "tr" ? "Kapalı" : "Closed")}</b></label>
            <div className="ai-policy-threshold-grid">
              <label>{locale === "tr" ? "Hata uyarı eşiği" : "Failure alert threshold"}<span><input min="0.01" max="1" step="0.01" type="number" value={policyDraft.failureRateThreshold} onChange={(event) => setPolicyDraft((current) => ({ ...current, failureRateThreshold: event.target.value }))} /><b>{Math.round(Number(policyDraft.failureRateThreshold || 0) * 100)}%</b></span></label>
              <label>{locale === "tr" ? "Ret uyarı eşiği" : "Rejection alert threshold"}<span><input min="0.01" max="1" step="0.01" type="number" value={policyDraft.rejectionRateThreshold} onChange={(event) => setPolicyDraft((current) => ({ ...current, rejectionRateThreshold: event.target.value }))} /><b>{Math.round(Number(policyDraft.rejectionRateThreshold || 0) * 100)}%</b></span></label>
            </div>
          </section>

          <section className="ai-policy-card ai-policy-budget-card">
            <div className="ai-policy-card-heading"><div><span>03</span><h3>{locale === "tr" ? "Bütçe sınırları" : "Budget guardrails"}</h3><p>{locale === "tr" ? "Kontrollü AI trafiği için günlük token ve maliyet tavanını tanımlayın." : "Set daily token and cost ceilings for controlled AI traffic."}</p></div></div>
            <div className="ai-policy-field-grid compact">
              <label>{locale === "tr" ? "24 saatlik token bütçesi" : "24-hour token budget"}<input min="1000" type="number" value={policyDraft.maxTokensPer24Hours} onChange={(event) => setPolicyDraft((current) => ({ ...current, maxTokensPer24Hours: event.target.value }))} /></label>
              <label>{locale === "tr" ? "24 saatlik maliyet bütçesi" : "24-hour cost budget"}<input min="0.01" step="0.01" type="number" value={policyDraft.maxCostPer24Hours} onChange={(event) => setPolicyDraft((current) => ({ ...current, maxCostPer24Hours: event.target.value }))} /></label>
              <label>{locale === "tr" ? "Para birimi" : "Currency"}<input maxLength={12} value={policyDraft.costCurrency} onChange={(event) => setPolicyDraft((current) => ({ ...current, costCurrency: event.target.value.toUpperCase() }))} /></label>
            </div>
            <div className="ai-policy-pricing-note"><span>{locale === "tr" ? "Fotoğraf fiyat kaydı" : "Photo pricing record"}</span><strong>{formatAiCostAmount(policy?.photoInputTokenCostPer1m, policy?.photoCostCurrency)} / {formatAiCostAmount(policy?.photoOutputTokenCostPer1m, policy?.photoCostCurrency)}</strong><small>{locale === "tr" ? "1 milyon girdi / çıktı tokenı" : "Per 1M input / output tokens"}</small></div>
          </section>

          <section className="ai-policy-card ai-policy-verification-card">
            <div className="ai-policy-card-heading"><div><span>04</span><h3>{locale === "tr" ? "Sağlayıcı doğrulaması" : "Provider verification"}</h3><p>{locale === "tr" ? "Kaydetmeden önce etkin yönlendirmelerin yanıt verdiğini doğrulayın." : "Confirm active routes respond before applying a policy change."}</p></div></div>
            <div className="ai-policy-smoke-actions"><button className="ghost-button" type="button" disabled={smokeState === "loading"} onClick={() => void runProviderSmoke("VOICE_FOOD_LOG")}>{locale === "tr" ? "Ses modelini test et" : "Test voice model"}</button><button className="primary-button" type="button" disabled={smokeState === "loading"} onClick={() => void runProviderSmoke("PHOTO_MEAL_LOG")}>{locale === "tr" ? "Fotoğraf modelini test et" : "Test photo model"}</button></div>
            {smokeState === "loading" && <div className="ai-policy-smoke-state">{locale === "tr" ? "Sağlayıcı yanıtı bekleniyor…" : "Waiting for provider response…"}</div>}
            {smokeResult && <details className="ai-policy-smoke-result"><summary>{locale === "tr" ? "Son test sonucunu göster" : "Show latest test result"}</summary><pre className="audit-value-block">{smokeResult}</pre></details>}
          </section>

          <section className="ai-policy-apply-card">
            <div><span>{locale === "tr" ? "DEĞİŞİKLİK KAYDI" : "CHANGE RECORD"}</span><h3>{locale === "tr" ? "İncele ve uygula" : "Review and apply"}</h3><p>{locale === "tr" ? "Kaydetme ve geri alma işlemleri denetim kaydı oluşturur." : "Save and rollback operations create an audit record."}</p></div>
            <label>{locale === "tr" ? "Yönetici gerekçesi" : "Admin reason"}<textarea placeholder={locale === "tr" ? "Bu güvenilirlik veya dağıtım politikası neden değişiyor?" : "Why is this reliability or deployment policy changing?"} value={policyDraft.reason} onChange={(event) => setPolicyDraft((current) => ({ ...current, reason: event.target.value }))} /><small>{locale === "tr" ? "En az 8 karakter." : "At least 8 characters."}</small></label>
            <div className="ai-policy-apply-footer"><small>{locale === "tr" ? "Sürüm" : "Version"} {formatValue(policy?.version)} · {locale === "tr" ? "Son güncelleme" : "Updated"} {formatDate(policy?.updatedAt)} · {policy?.updatedBy ?? "-"}</small><div><button className="ghost-button danger-button" disabled={!policy?.rollbackAvailable || policyActionState === "loading"} onClick={() => setRollbackConfirmationOpen(true)} type="button">{locale === "tr" ? "Dağıtımı geri al" : "Rollback deployment"}</button><button className="primary-button" disabled={policyActionState === "loading" || policyDraft.reason.trim().length < 8} type="submit">{policyActionState === "loading" ? (locale === "tr" ? "Kaydediliyor…" : "Saving…") : (locale === "tr" ? "Politikayı kaydet" : "Save policy")}</button></div></div>
          </section>
        </form>
      </div>}

      {mode === "overview" && <><div className="ai-monitoring-grid">
        <AiOverviewPanel title={locale === "tr" ? "Sağlayıcı, model ve prompt sürümü" : "Provider, model, and prompt version"}>
          <DataTable
            columns={["Provider", "Model", "Prompt", "Requests", "Tokens", "Cost", "Quota"]}
            rows={(summary?.providerModels ?? []).map((item) => [
              item.provider ?? "-",
              item.model ?? "-",
              item.promptVersion ?? "-",
              formatValue(item.requestCount),
              `${formatValue(item.promptTokens)} in / ${formatValue(item.completionTokens)} out / ${formatValue(item.totalTokens)} total`,
              formatAiCostAmount(item.estimatedCost, item.costCurrency),
              `${formatValue(item.quotaConsumedAmount)} used / ${formatValue(item.quotaRefundedAmount)} refunded`
            ])}
            empty="No provider/model metrics returned for this window."
          />
        </AiOverviewPanel>
        <AiOverviewPanel title={locale === "tr" ? "İstek türü ve durum" : "Request type and status"}>
          <DataTable
            columns={["Request type", "Status", "Count", "Tokens", "Quota"]}
            rows={(summary?.requestStatuses ?? []).map((item) => [
              humanizeAiRequestType(item.requestType),
              <Badge value={item.status ?? "-"} tone={aiStatusTone(item.status)} />,
              formatValue(item.requestCount),
              formatValue(item.totalTokens),
              `${formatValue(item.quotaConsumedAmount)} used / ${formatValue(item.quotaRefundedAmount)} refunded`
            ])}
            empty="No request status metrics returned for this window."
          />
        </AiOverviewPanel>
      </div>

      <AiOverviewPanel title={locale === "tr" ? "Özellik ve kullanıcı segmenti maliyetleri" : "Feature and audience economics"}>
        <DataTable
          columns={["Feature", "Plan", "Region", "Language", "Requests", "Failed", "Rejected", "Cost"]}
          rows={(summary?.segments ?? []).map((item) => [
            humanizeAiRequestType(item.requestType), shortFeature(item.plan), shortFeature(item.region), shortFeature(item.language),
            formatValue(item.requestCount), formatValue(item.failedCount), formatValue(item.rejectedCount),
            formatAiCostAmount(item.estimatedCost, item.costCurrency)
          ])}
          empty="No segmented AI economics returned for this window."
        />
      </AiOverviewPanel></>}

      {mode === "requests" && <><CollapsiblePanel
        className="ai-request-filter-panel"
        title={locale === "tr" ? "İstek filtreleri" : "Request filters"}
        description={locale === "tr" ? "İstek türü, durum ve iade uygunluğuna göre kuyruğu daraltın." : "Narrow the queue by request type, status, and refund eligibility."}
        open={requestFiltersOpen}
        onToggle={() => setRequestFiltersOpen(value => !value)}
      >
        <div className="ai-request-filter-summary" aria-live="polite">
          <div><span>{locale === "tr" ? "Aktif görünüm" : "Active view"}</span><strong>{[requestType ? humanizeAiRequestType(requestType) : null, status ? shortFeature(status) : null, refundableOnly ? (locale === "tr" ? "Yalnızca iade edilebilir" : "Refundable only") : null].filter(Boolean).join(" · ") || (locale === "tr" ? "Tüm AI istekleri" : "All AI requests")}</strong></div>
          <span className="ai-request-filter-count">{[requestType, status, refundableOnly].filter(Boolean).length} {locale === "tr" ? "aktif filtre" : "active filters"}</span>
        </div>
        <div className="review-filter-grid ai-review-filter-grid">
          <label>
            {locale === "tr" ? "İstek türü" : "Request type"}
            <select value={requestType} onChange={(event) => { setRequestType(event.target.value); setPage(0); }}>
              <option value="">{locale === "tr" ? "Tüm istek türleri" : "All request types"}</option>
              {AI_REQUEST_TYPES.map((item) => <option key={item} value={item}>{humanizeAiRequestType(item)}</option>)}
            </select>
          </label>
          <label>
            {locale === "tr" ? "Durum" : "Status"}
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="">{locale === "tr" ? "Tüm durumlar" : "All statuses"}</option>
              {AI_REQUEST_STATUSES.map((item) => <option key={item} value={item}>{shortFeature(item)}</option>)}
            </select>
          </label>
          <label className="ai-request-refund-filter">
            <input checked={refundableOnly} onChange={(event) => { setRefundableOnly(event.target.checked); setPage(0); }} type="checkbox" />
            <span><strong>{locale === "tr" ? "İade inceleme kuyruğu" : "Refund review queue"}</strong><small>{locale === "tr" ? "Yalnızca iade edilebilir reddedilmiş istekleri göster." : "Show only rejected requests eligible for a quota refund."}</small></span>
          </label>
        </div>
        <div className="ai-request-filter-actions"><button className="ghost-button" type="button" disabled={!requestType && !status && !refundableOnly} onClick={resetFilters}>{locale === "tr" ? "Filtreleri temizle" : "Clear filters"}</button></div>
      </CollapsiblePanel>
      {refundApprovalNotice && <ApprovalSubmissionNotice {...refundApprovalNotice} isOwner={accessProfile?.role === "OWNER"} />}
      {refundResult && <Panel title="Last quota refund decision">
        <div className="ai-refund-result-grid">
          <DetailItem label="Request" value={refundResult.requestId} />
          <DetailItem label="Refunded now" value={refundResult.refundedNow} />
          <DetailItem label="Total refunded" value={refundResult.quotaRefundedAmount} />
          <DetailItem label="Refunded by" value={refundResult.quotaRefundedBy} />
          <DetailItem label="Decision" value={shortFeature(refundResult.quotaRefundDecision)} />
          <DetailItem label="Decision reason" value={refundResult.quotaRefundDecisionReason} />
          <DetailItem label="Decided by" value={refundResult.quotaRefundDecidedBy ?? refundResult.quotaRefundedBy} />
          <DetailItem label="Decided at" value={formatDate(refundResult.quotaRefundDecidedAt ?? refundResult.quotaRefundedAt)} />
          <DetailItem label="Subscription remaining" value={refundResult.subscription?.aiRemainingThisPeriod} />
        </div>
      </Panel>}
      <DataTable
        columns={["Request", "User", "Type", "Status", "Provider", "Performance", "Cost", "Quota", "Rejection", "Refund", "Actions"]}
        rows={rows.map((item) => {
          const refundableAmount = safeNumber(item.refundableAmount);
          const canRefund = canRefundAiRequest(item);
          return [
            <TargetAwareValue value={aiRequestId(item) ?? "-"} focused={isTargetMatch(focusedRequestId, aiRequestId(item))} />,
            <div className="entity-cell"><strong>{item.userEmail ?? "-"}</strong><small>User #{formatValue(item.userId)}</small></div>,
            humanizeAiRequestType(item.requestType),
            <Badge value={item.status} tone={aiStatusTone(item.status)} />,
            <div className="entity-cell"><strong>{formatValue(item.provider)} {formatValue(item.model)}</strong><small>{item.promptVersion ?? "No prompt version"}</small></div>,
            <div className="entity-cell"><strong>{item.latencyMs ? `${formatValue(item.latencyMs)} ms` : "-"}</strong><small>{formatValue(item.totalTokens)} tokens</small></div>,
            formatAiCost(item),
            `${formatValue(item.quotaConsumedAmount ?? item.quotaConsumed ?? 0)} used / ${formatValue(item.quotaRefundedAmount ?? item.quotaRefunded ?? 0)} refunded / ${formatValue(refundableAmount)} refundable`,
            <div className="ai-feedback-cell"><strong>{shortFeature(item.rejectionReason)}</strong><small>{item.rejectionFeedback ?? "-"}</small></div>,
            <div className="ai-feedback-cell">
              <strong>{shortFeature(item.quotaRefundDecision ?? (item.quotaRefundedAt ? "APPROVED" : "PENDING"))}</strong>
              <small>{item.quotaRefundDecisionReason ?? item.quotaRefundReason ?? "Awaiting admin decision"}</small>
            </div>,
            <div className="ai-refund-row-actions">
              <button className="ghost-button" type="button" disabled={inspectionState === "loading"} onClick={() => void inspectAiRequest(item)}>Inspect</button>
              <button className="ghost-button" type="button" disabled={!canRefund || refundState === "loading"} onClick={() => setRefundDraft({ item, amount: String(Math.max(1, refundableAmount)), reason: "" })}>Refund</button>
              <button className="ghost-button danger-button" type="button" disabled={!canRefund || refundState === "loading"} onClick={() => setRefundRejectDraft({ item, reason: "" })}>Reject</button>
            </div>
          ];
        })}
        empty="No AI requests returned."
      />
      <PaginationControls
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? rows.length}
        totalPages={data?.totalPages ?? 1}
        first={Boolean(data?.first)}
        last={Boolean(data?.last)}
        onPageChange={setPage}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
      />
      {inspection && <AiRequestInspectionModal inspection={inspection} onClose={closeInspection} isOwner={accessProfile?.role === "OWNER"} />}</>}
      {mode === "policy" && rollbackConfirmationOpen && <ConfirmDialog
        title="Rollback AI deployment?"
        message={`This will restore the previous model and prompt version. The circuit breaker and budget limits remain unchanged. Reason: ${policyDraft.reason || "missing"}`}
        confirmLabel="Rollback deployment"
        danger
        busy={policyActionState === "loading"}
        onCancel={() => setRollbackConfirmationOpen(false)}
        onConfirm={() => void rollbackOperationsDeployment()}
      />}
      {refundDraft && <AiQuotaRefundModal
        draft={refundDraft}
        busy={refundState === "loading"}
        onChange={setRefundDraft}
        onClose={() => setRefundDraft(null)}
        onSubmit={refundSelectedAiQuota}
      />}      {refundRejectDraft && <AiQuotaRefundRejectModal
        draft={refundRejectDraft}
        busy={refundState === "loading"}
        onChange={setRefundRejectDraft}
        onClose={() => setRefundRejectDraft(null)}
        onSubmit={rejectSelectedAiQuotaRefund}
      />}
    </div>
  );
}

export function AiRequestInspectionModal({ inspection, onClose, isOwner }: { inspection: AiRequestInspection; onClose: () => void; isOwner: boolean }) {
  const { locale } = useAdminLocale();
  const dialogRef = useDialogAccessibility(onClose);
  const resultRecord = inspectionRecord(inspection.result);
  const detectedItems = inspectionObjectArray(resultRecord.items);
  const ingredients = inspectionObjectArray(resultRecord.ingredients);
  const suggestedIngredients = inspectionObjectArray(resultRecord.suggestedIngredients);
  const visibleIngredients = detectedItems.length ? detectedItems : ingredients.length ? ingredients : suggestedIngredients;
  const ingredientTitle = detectedItems.length ? "Detected ingredients" : ingredients.length ? "Ingredients" : "Suggested ingredients";
  const perServingNutrition = inspectionRecord(resultRecord.estimatedNutritionPerServing);
  const extractedResultKeys = new Set(["items", "ingredients", "suggestedIngredients", "estimatedNutritionPerServing", "estimatedNutritionTotal"]);
  const resultOverview = Object.fromEntries(Object.entries(resultRecord).filter(([key]) => !extractedResultKeys.has(key)));
  const diagnostics = [
    ["User rejection", shortFeature(inspection.rejectionReason)],
    ["User feedback", inspection.rejectionFeedback],
    ["Refund decision", shortFeature(inspection.quotaRefundDecision)],
    ["Decision reason", inspection.quotaRefundDecisionReason],
    ["Correction summary", inspection.correctionSummary],
    ["Failure summary", inspection.failureSummary]
  ].filter(([, value]) => meaningfulInspectionValue(value));
  const hasUserDecision = meaningfulInspectionValue(inspection.confirmation) || meaningfulInspectionValue(inspection.rejectionReason) || meaningfulInspectionValue(inspection.correctionSummary);
  const extractionLabel = inspection.requestType === "PHOTO_MEAL_LOG" ? (locale === "tr" ? "OCR / AI çıkarımı" : "OCR / AI extraction") : (locale === "tr" ? "AI çıkarımı" : "AI extraction");
  const flowSteps = [
    [locale === "tr" ? "1 · Kaynak" : "1 · Source", meaningfulInspectionValue(inspection.requestContext) ? (locale === "tr" ? "İstek bağlamı kaydedildi" : "Request context recorded") : (locale === "tr" ? "Bağlam yok" : "No context")],
    [`2 · ${extractionLabel}`, meaningfulInspectionValue(inspection.result) ? (locale === "tr" ? "Çıkarım üretildi" : "Extraction produced") : (locale === "tr" ? "Sonuç yok" : "No result")],
    [locale === "tr" ? "3 · Kullanıcı kararı" : "3 · User decision", hasUserDecision ? (locale === "tr" ? "Onay, ret veya düzeltme kaydedildi" : "Confirmation, rejection or correction recorded") : (locale === "tr" ? "Karar bekleniyor" : "Awaiting decision")],
    [locale === "tr" ? "4 · Nihai durum" : "4 · Final state", shortFeature(inspection.status)]
  ];

  return (
    <div className="modal-backdrop ai-inspection-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="modal-card ai-inspection-modal" role="dialog" aria-modal="true" aria-labelledby="ai-inspection-title" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header ai-inspection-header">
          <div>
            <span>AI REQUEST</span>
            <h2 id="ai-inspection-title">Request #{inspection.requestId ?? "-"}</h2>
            <p>{inspection.userEmail ?? `User #${formatValue(inspection.userId)}`}</p>
          </div>
          <div className="ai-inspection-header-actions">
            <span className="ai-inspection-readonly">Read only</span>
            <button className="modal-icon-close" type="button" onClick={onClose} aria-label="Close AI request inspection">x</button>
          </div>
        </header>
        <div className="ai-inspection-body">
          <div className="ai-inspection-flow" aria-label={locale === "tr" ? "AI istek yaşam döngüsü" : "AI request lifecycle"}>
            {flowSteps.map(([label, detail]) => <div key={label}><span>{label}</span><strong>{detail}</strong></div>)}
          </div>
          <div className="ai-inspection-summary">
            <DetailItem label="Type" value={humanizeAiRequestType(inspection.requestType)} />
            <DetailItem label="Status" value={shortFeature(inspection.status)} />
            <DetailItem label="Provider" value={`${inspection.provider ?? "-"} / ${inspection.model ?? "-"}`} />
            <DetailItem label="Created" value={formatDate(inspection.createdAt)} />
            <DetailItem label="Correlation ID" value={inspection.correlationId ?? "-"} />
            <DetailItem label="Performance" value={inspection.latencyMs ? `${formatValue(inspection.latencyMs)} ms / ${formatValue(inspection.totalTokens)} tokens` : `${formatValue(inspection.totalTokens)} tokens`} />
            <DetailItem label="Quota" value={`${formatValue(inspection.quotaConsumedAmount)} used / ${formatValue(inspection.quotaRefundedAmount)} refunded`} />
          </div>
          <div className="ai-inspection-grid">
            <InspectionSection title={locale === "tr" ? "Kaynak ve istek bağlamı" : "Source and request context"} data={inspection.requestContext} omitKeys={["requestType"]} compact />
            <InspectionSection title={extractionLabel} data={resultOverview} omitKeys={["requestId", "requestType", "provider", "model", "status", "schemaVersion"]} />
            {visibleIngredients.length > 0 && <MealIngredientInspection title={ingredientTitle} items={visibleIngredients} />}
            {Object.keys(perServingNutrition).length > 0 && <NutritionPerServingInspection nutrition={perServingNutrition} />}
            <InspectionSection title={locale === "tr" ? "Kullanıcı onayı ve nihai kayıt" : "User confirmation and final record"} data={inspection.confirmation} />
            {diagnostics.length > 0 && (
              <section className="ai-inspection-section ai-inspection-diagnostics">
                <div className="ai-inspection-section-heading"><h3>Review and diagnostics</h3></div>
                <div className="ai-inspection-review-list">
                  {diagnostics.map(([label, value]) => (
                    <div className="ai-inspection-diagnostic" key={String(label)}>
                      <span>{label}</span>
                      <InspectionValue value={parseInspectionText(value)} />
                    </div>
                  ))}
                </div>
              </section>
            )}
          </div>
        </div>
        <footer className="modal-actions padded-actions">
          {isOwner && inspection.correlationId && <a className="ghost-button" href={`${sectionPaths.errors}?correlationId=${encodeURIComponent(inspection.correlationId)}`}>{locale === "tr" ? "Hata Merkezi'nde aç" : "Open in Error Center"}</a>}
          <button className="primary-button" type="button" onClick={onClose}>Close</button>
        </footer>
      </section>
    </div>
  );
}

export function MealIngredientInspection({ title, items }: { title: string; items: Array<Record<string, unknown>> }) {
  return (
    <section className="ai-inspection-section ai-inspection-ingredients">
      <div className="ai-inspection-section-heading"><h3>{title}</h3><span>{items.length} item{items.length === 1 ? "" : "s"}</span></div>
      <div className="ai-inspection-ingredient-list">
        {items.map((item, index) => {
          const nutrition = inspectionRecord(item.estimatedNutrition);
          const macros = {
            calories: item.estimatedCalories ?? nutrition.calories,
            protein: item.estimatedProtein ?? nutrition.protein,
            carbs: item.estimatedCarbs ?? nutrition.carbs,
            fat: item.estimatedFat ?? nutrition.fat
          };
          const hasMacros = Object.values(macros).some(meaningfulInspectionValue);
          return (
            <article className={`ai-inspection-ingredient${hasMacros ? "" : " no-macros"}`} key={`${String(item.name ?? "item")}-${index}`}>
              <div className="ai-inspection-ingredient-main">
                <div><span>Ingredient {index + 1}</span><strong>{String(item.name ?? "Unnamed ingredient")}</strong></div>
                <div className="ai-inspection-portion"><strong>{formatInspectionScalar(item.quantity ?? item.portionSize)}</strong><span>{String(item.unit ?? item.portionUnit ?? "")}</span></div>
                {meaningfulInspectionValue(item.confidence) && <div className="ai-inspection-confidence"><span>Confidence</span><strong>{Math.round(Number(item.confidence) * 100)}%</strong></div>}
              </div>
              {hasMacros && <div className="ai-inspection-macros">
                <MacroValue label="Calories" value={macros.calories} unit="kcal" />
                <MacroValue label="Protein" value={macros.protein} unit="g" />
                <MacroValue label="Carbs" value={macros.carbs} unit="g" />
                <MacroValue label="Fat" value={macros.fat} unit="g" />
              </div>}
            </article>
          );
        })}
      </div>
    </section>
  );
}

export function NutritionPerServingInspection({ nutrition }: { nutrition: Record<string, unknown> }) {
  const units: Record<string, string> = {
    calories: "kcal", protein: "g", carbs: "g", fat: "g", fiber: "g", sugar: "g", saturatedFat: "g",
    sodium: "mg", potassium: "mg", cholesterol: "mg", calcium: "mg", iron: "mg", magnesium: "mg", zinc: "mg",
    vitaminA: "mcg", vitaminB12: "mcg", vitaminC: "mg", vitaminD: "mcg", vitaminE: "mg"
  };
  const entries = Object.entries(nutrition).filter(([, value]) => meaningfulInspectionValue(value));
  if (!entries.length) return null;
  return (
    <section className="ai-inspection-section ai-inspection-nutrition-serving">
      <div className="ai-inspection-section-heading"><h3>Estimated nutrition per serving</h3></div>
      <div className="ai-inspection-nutrition-grid">
        {entries.map(([key, value]) => <MacroValue key={key} label={inspectionLabel(key)} value={value} unit={units[key] ?? ""} />)}
      </div>
    </section>
  );
}

export function inspectionObjectArray(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? value.map(inspectionRecord).filter((item) => Object.keys(item).length > 0) : [];
}

export function MacroValue({ label, value, unit }: { label: string; value: unknown; unit: string }) {
  const available = meaningfulInspectionValue(value);
  return <div className={available ? "" : "unavailable"}><span>{label}</span><strong>{available ? `${formatInspectionScalar(value)} ${unit}` : "Not available"}</strong></div>;
}

export function inspectionRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

export function InspectionSection({ title, data, omitKeys = [], featured = false, compact = false }: { title: string; data?: unknown; omitKeys?: string[]; featured?: boolean; compact?: boolean }) {
  const normalized = filterInspectionData(data, new Set(omitKeys));
  if (!meaningfulInspectionValue(normalized)) return null;
  return (
    <section className={`ai-inspection-section${featured ? " ai-inspection-featured" : ""}${compact ? " ai-inspection-compact" : ""}`}>
      <div className="ai-inspection-section-heading"><h3>{title}</h3></div>
      <div className="ai-inspection-content"><InspectionValue value={normalized} /></div>
    </section>
  );
}

export function InspectionValue({ value, label }: { value: unknown; label?: string }) {
  if (!meaningfulInspectionValue(value)) return null;
  if (Array.isArray(value)) {
    const primitive = value.every((item) => item === null || ["string", "number", "boolean"].includes(typeof item));
    if (primitive) return <ul className="ai-inspection-list">{value.map((item, index) => <li key={index}>{formatInspectionScalar(item)}</li>)}</ul>;
    return <div className="ai-inspection-item-list">{value.map((item, index) => <div className="ai-inspection-item" key={index}><span className="ai-inspection-item-index">{label ? `${label} ${index + 1}` : `Item ${index + 1}`}</span><InspectionValue value={item} /></div>)}</div>;
  }
  if (typeof value === "object" && value !== null) {
    return <div className="ai-inspection-facts">{Object.entries(value as Record<string, unknown>).filter(([, item]) => meaningfulInspectionValue(item)).map(([key, item]) => (
      <div className={typeof item === "object" && item !== null ? "ai-inspection-fact wide" : "ai-inspection-fact"} key={key}>
        <span>{inspectionLabel(key)}</span>
        <InspectionValue value={item} label={inspectionLabel(key)} />
      </div>
    ))}</div>;
  }
  return <strong className="ai-inspection-scalar">{formatInspectionScalar(value)}</strong>;
}

export function filterInspectionData(value: unknown, omitted: Set<string>): unknown {
  if (Array.isArray(value)) return value.map((item) => filterInspectionData(item, new Set())).filter(meaningfulInspectionValue);
  if (typeof value !== "object" || value === null) return value;
  return Object.fromEntries(Object.entries(value as Record<string, unknown>)
    .filter(([key, item]) => !omitted.has(key) && meaningfulInspectionValue(item))
    .map(([key, item]) => [key, filterInspectionData(item, new Set())]));
}

export function meaningfulInspectionValue(value: unknown): boolean {
  if (value === null || value === undefined || value === "" || value === "-") return false;
  if (Array.isArray(value)) return value.some(meaningfulInspectionValue);
  if (typeof value === "object") return Object.values(value as Record<string, unknown>).some(meaningfulInspectionValue);
  return true;
}

export function formatInspectionScalar(value: unknown): string {
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number") return Number.isInteger(value) ? String(value) : value.toLocaleString(undefined, { maximumFractionDigits: 3 });
  return String(value ?? "-");
}

export function inspectionLabel(value: string): string {
  return value.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ").replace(/^./, (letter) => letter.toUpperCase());
}

export function parseInspectionText(value: unknown): unknown {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return value;
  try { return JSON.parse(trimmed); } catch { return value; }
}

export function AiQuotaRefundModal({
  busy,
  draft,
  onChange,
  onClose,
  onSubmit
}: {
  busy: boolean;
  draft: { item: AiMealDraft; amount: string; reason: string };
  onChange: (draft: { item: AiMealDraft; amount: string; reason: string }) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
}) {
  const dialogRef = useDialogAccessibility<HTMLFormElement>(onClose);
  const item = draft.item;
  const refundableAmount = safeNumber(item.refundableAmount);
  const amount = Number(draft.amount);
  const invalid = !Number.isFinite(amount) || amount <= 0 || amount > refundableAmount || !draft.reason.trim();
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <form ref={dialogRef} tabIndex={-1} className="modal-card compact ai-refund-modal" role="dialog" aria-modal="true" aria-label="AI quota refund" onSubmit={onSubmit} onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span>AI QUOTA REFUND</span>
            <h2>Request #{aiRequestId(item) ?? "-"}</h2>
            <p>{item.userEmail ?? `User #${formatValue(item.userId)}`}</p>
          </div>
          <button className="modal-icon-close" type="button" onClick={onClose} aria-label="Close refund modal">x</button>
        </div>
        <div className="modal-body ai-refund-body">
          <div className="ai-refund-overview">
            <div className="ai-refund-summary">
              <DetailItem label="Status" value={shortFeature(item.status)} />
              <DetailItem label="Consumed" value={formatValue(item.quotaConsumedAmount ?? item.quotaConsumed ?? 0)} />
              <DetailItem label="Already refunded" value={item.quotaRefundedAmount ?? item.quotaRefunded ?? 0} />
              <DetailItem label="Refundable" value={refundableAmount} />
            </div>
            <label className="ai-refund-amount-field">
              Refund amount
              <input min="1" max={refundableAmount || undefined} type="number" value={draft.amount} onChange={(event) => onChange({ ...draft, amount: event.target.value })} required />
              <small>Available refundable quota: {formatValue(refundableAmount)}</small>
            </label>
          </div>
          <div className="ai-refund-user-rejection">
            <span>User rejection</span>
            <strong>{shortFeature(item.rejectionReason) || "No reason selected"}</strong>
            <p>{item.rejectionFeedback || "No user feedback submitted."}</p>
          </div>
          <div className="ai-refund-form-grid">
            <label className="ai-refund-reason-field">
              Refund reason
              <textarea maxLength={500} value={draft.reason} onChange={(event) => onChange({ ...draft, reason: event.target.value })} placeholder="Why this AI result deserves quota refund" required />
            </label>
            <aside className="ai-refund-warning">Backend enforces request-level refund limits. If this request is stale, the action returns a safe validation error and the queue refreshes.</aside>
          </div>
        </div>
        <div className="modal-actions padded-actions ai-refund-actions">
          <button className="ghost-button" type="button" onClick={onClose}>Cancel</button>
          <button className="primary-button" type="submit" disabled={busy || invalid}>Refund quota</button>
        </div>
      </form>
    </div>
  );
}

export function AiQuotaRefundRejectModal({
  busy,
  draft,
  onChange,
  onClose,
  onSubmit
}: {
  busy: boolean;
  draft: { item: AiMealDraft; reason: string };
  onChange: (draft: { item: AiMealDraft; reason: string }) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
}) {
  const dialogRef = useDialogAccessibility<HTMLFormElement>(onClose);
  const item = draft.item;
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <form ref={dialogRef} tabIndex={-1} className="modal-card compact ai-refund-modal" role="dialog" aria-modal="true" aria-label="AI quota refund rejection" onSubmit={onSubmit} onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span>AI QUOTA REFUND DECISION</span>
            <h2>Reject request #{aiRequestId(item) ?? "-"}</h2>
            <p>{item.userEmail ?? `User #${formatValue(item.userId)}`}</p>
          </div>
          <button className="modal-icon-close" type="button" onClick={onClose} aria-label="Close refund rejection modal">x</button>
        </div>
        <div className="modal-body ai-refund-body">
          <div className="ai-refund-user-rejection">
            <span>User rejection</span>
            <strong>{shortFeature(item.rejectionReason) || "No reason selected"}</strong>
            <p>{item.rejectionFeedback || "No user feedback submitted."}</p>
          </div>
          <label className="ai-refund-reason-field">
            Reason shown to the user
            <textarea
              autoFocus
              maxLength={500}
              value={draft.reason}
              onChange={(event) => onChange({ ...draft, reason: event.target.value })}
              placeholder="Explain why quota cannot be refunded"
              required
            />
          </label>
        </div>
        <div className="modal-actions padded-actions ai-refund-actions">
          <button className="ghost-button" type="button" onClick={onClose}>Cancel</button>
          <button className="primary-button danger-button" type="submit" disabled={busy || !draft.reason.trim()}>Reject refund</button>
        </div>
      </form>
    </div>
  );
}

export function buildAiOperationsPath(filters: {
  requestType: string;
  status: string;
  refundableOnly: boolean;
  page: number;
  size: number;
  userId?: number;
}): string {
  const params = new URLSearchParams();
  if (filters.requestType) params.set("requestType", filters.requestType);
  if (filters.status) params.set("status", filters.status);
  if (filters.userId) params.set("userId", String(filters.userId));
  params.set("refundableOnly", String(filters.refundableOnly));
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/ai/requests?${params.toString()}`;
}

export function readAiRequestRouteState(search = window.location.search): AiRequestRouteState {
  const params = new URLSearchParams(search);
  const requestType = params.get("requestType") ?? "";
  const status = params.get("status") ?? "";
  const page = Number.parseInt(params.get("page") ?? "", 10);
  const size = Number.parseInt(params.get("size") ?? "", 10);
  const requestId = Number.parseInt(params.get("requestId") ?? "", 10);
  return {
    requestType: AI_REQUEST_TYPES.includes(requestType) ? requestType : "",
    status: AI_REQUEST_STATUSES.includes(status) ? status : "",
    refundableOnly: params.get("refundableOnly") === "true",
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    size: [10, 25, 50, 100].includes(size) ? size : 25,
    requestId: Number.isSafeInteger(requestId) && requestId > 0 ? requestId : null,
    userContext: readUserRouteContext(search)
  };
}

export function humanizeAiRequestType(value?: string): string {
  switch (value) {
    case "VOICE_FOOD_LOG":
      return "Voice meal draft";
    case "PHOTO_MEAL_LOG":
      return "Photo meal draft";
    case "AI_RECIPE_GENERATION":
      return "Recipe assistant";
    case "AI_WORKOUT_PLAN":
      return "Workout planner";
    case "AI_DAILY_INSIGHT":
      return "Daily insight";
    case "AI_WEEKLY_INSIGHT":
      return "Weekly insight";
    default:
      return shortFeature(value);
  }
}

export function aiStatusTone(value?: string): "default" | "good" | "warn" | "danger" | "neutral" {
  switch (value) {
    case "CONFIRMED":
      return "good";
    case "REJECTED":
      return "warn";
    case "FAILED":
      return "danger";
    case "DRAFT_CREATED":
    case "PENDING":
      return "neutral";
    default:
      return "default";
  }
}

export function safeNumber(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

export function summaryWindowLabel(hours: number): string {
  if (hours === 24) return "24h";
  if (hours === 168) return "7d";
  if (hours === 744) return "31d";
  return `${hours}h`;
}

export function formatFailureRate(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "-";
  return `${(value * 100).toFixed(1)}%`;
}

export function formatCurrencyBreakdown(value?: Record<string, number>): string {
  const entries = Object.entries(value ?? {}).filter(([, amount]) => typeof amount === "number" && Number.isFinite(amount));
  if (!entries.length) return "-";
  return entries.map(([currency, amount]) => formatAiCostAmount(amount, currency)).join(" / ");
}

export function formatRatioBreakdown(value?: Record<string, number>): string {
  const entries = Object.entries(value ?? {}).filter(([, ratio]) => typeof ratio === "number" && Number.isFinite(ratio));
  if (!entries.length) return "-";
  return entries.map(([currency, ratio]) => `${currency} ${(ratio * 100).toFixed(1)}%`).join(" / ");
}

export function formatAiCostAmount(amount?: number, currency?: string): string {
  if (typeof amount !== "number" || !Number.isFinite(amount)) return "-";
  return `${amount.toFixed(4)} ${currency ?? ""}`.trim();
}

export function aiRequestId(item: AiMealDraft): number | undefined {
  return item.requestId ?? item.id;
}

export function canRefundAiRequest(item: AiMealDraft): boolean {
  return item.status === "REJECTED"
    && Boolean(item.quotaConsumed)
    && safeNumber(item.refundableAmount) > 0
    && item.quotaRefundDecision !== "APPROVED"
    && item.quotaRefundDecision !== "REJECTED";
}

export function formatAiCost(item: AiMealDraft): string {
  if (typeof item.estimatedCost !== "number") return "-";
  return `${item.estimatedCost.toFixed(4)} ${item.costCurrency ?? ""}`.trim();
}
