import { FormEvent, lazy, Suspense, useEffect, useState } from "react";

import { formatRequestError, request } from "../api";

import { AdminApprovalRequest, AdminPromotion, AdminPromotionMetrics, AdminPromotionOperationsAnalytics, AdminPromotionPage, AdminPromotionPreview, AdminPromotionRedemptionPage } from "../types";

import { DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { ApprovalSubmissionNotice, formatDate, formatValue, humanizeFeature, submitAdminApproval } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

export const PromotionLifecycleChart = lazy(() => import("../PromotionOperationsCharts").then((module) => ({ default: module.PromotionLifecycleChart })));

export const PromotionTypeChart = lazy(() => import("../PromotionOperationsCharts").then((module) => ({ default: module.PromotionTypeChart })));

export const RedemptionOutcomeChart = lazy(() => import("../PromotionOperationsCharts").then((module) => ({ default: module.RedemptionOutcomeChart })));

export const PromotionRejectionChart = lazy(() => import("../PromotionOperationsCharts").then((module) => ({ default: module.PromotionRejectionChart })));

export const PromotionRedemptionTrendChart = lazy(() => import("../PromotionOperationsCharts").then((module) => ({ default: module.PromotionRedemptionTrendChart })));

export type PromotionDraft = {
  code: string; name: string; description: string; discountPercent: string;
  promoType: string; targetStore: string; targetPlan: string; targetRegion: string;
  targetProductId: string; currency: string; eligibilityRule: string;
  perUserLimit: string; globalLimit: string; campaignKey: string;
  providerOfferId: string; providerProductId: string; startAt: string; endAt: string;
  storeOfferCodeRequired: boolean;
};

export const EMPTY_PROMOTION: PromotionDraft = {
  code: "", name: "", description: "", discountPercent: "0", promoType: "CAMPAIGN",
  targetStore: "ALL", targetPlan: "", targetRegion: "", targetProductId: "",
  currency: "EUR", eligibilityRule: "ALL_USERS", perUserLimit: "1", globalLimit: "",
  campaignKey: "", providerOfferId: "", providerProductId: "", startAt: "", endAt: "",
  storeOfferCodeRequired: true
};

export function PromotionsView({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (english: string, turkish: string) => tr ? turkish : english;
  const [state, setState] = useState<LoadState>("idle");
  const [pageData, setPageData] = useState<AdminPromotionPage>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  const [metrics, setMetrics] = useState<AdminPromotionMetrics | null>(null);
  const [analytics, setAnalytics] = useState<AdminPromotionOperationsAnalytics | null>(null);
  const [analyticsWindowDays, setAnalyticsWindowDays] = useState(30);
  const [redemptions, setRedemptions] = useState<AdminPromotionRedemptionPage>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true });
  const [redemptionPage, setRedemptionPage] = useState(0);
  const [redemptionStatus, setRedemptionStatus] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState("");
  const [appliedSearch, setAppliedSearch] = useState("");
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [store, setStore] = useState("");
  const [draft, setDraft] = useState<PromotionDraft>(EMPTY_PROMOTION);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selected, setSelected] = useState<AdminPromotion | null>(null);
  const [preview, setPreview] = useState<AdminPromotionPreview | null>(null);
  const [deactivating, setDeactivating] = useState<AdminPromotion | null>(null);
  const [deactivationReason, setDeactivationReason] = useState("");
  const [approvalReason, setApprovalReason] = useState("");
  const [approvalNotice, setApprovalNotice] = useState<{ request: AdminApprovalRequest; message: string } | null>(null);
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [draftOpen, setDraftOpen] = useState(false);
  const [analyticsOpen, setAnalyticsOpen] = useState(true);
  const previewDialogRef = useDialogAccessibility(() => setSelected(null), Boolean(selected));
  const deactivateDialogRef = useDialogAccessibility<HTMLFormElement>(() => setDeactivating(null), Boolean(deactivating));

  async function load() {
    setState("loading");
    onError(null);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (appliedSearch) params.set("search", appliedSearch);
      if (status) params.set("status", status);
      if (type) params.set("type", type);
      if (store) params.set("store", store);
      const redemptionParams = new URLSearchParams({ page: String(redemptionPage), size: "10" });
      if (redemptionStatus) redemptionParams.set("status", redemptionStatus);
      const [promotions, summary, redemptionPageData, analyticsData] = await Promise.all([
        request<AdminPromotionPage>(`/api/v1/admin/promotions?${params}`),
        request<AdminPromotionMetrics>("/api/v1/admin/promotions/metrics"),
request<AdminPromotionRedemptionPage>(`/api/v1/admin/promotions/redemptions?${redemptionParams}`),
        request<AdminPromotionOperationsAnalytics>(`/api/v1/admin/promotions/analytics?windowDays=${analyticsWindowDays}`)
      ]);
      setPageData(promotions);
      setMetrics(summary);
      setRedemptions(redemptionPageData);
      setAnalytics(analyticsData);
      setState("ready");
    } catch (error) {
      setState("error");
      onError(formatRequestError(error));
    }
  }

  useEffect(() => { void load(); }, [page, size, appliedSearch, status, type, store, redemptionPage, redemptionStatus, analyticsWindowDays]);

  function resetDraft() {
    setDraft(EMPTY_PROMOTION);
    setEditingId(null);
  }

  function editPromotion(item: AdminPromotion) {
    if (item.status !== "DRAFT") return;
    setEditingId(item.id ?? null);
    setDraft({
      code: item.code ?? "", name: item.name ?? "", description: item.description ?? "",
      discountPercent: String(item.discountPercent ?? 0), promoType: item.promoType ?? "CAMPAIGN",
      targetStore: item.targetStore ?? "ALL", targetPlan: item.targetPlan ?? "",
      targetRegion: item.targetRegion ?? "", targetProductId: item.targetProductId ?? "",
      currency: item.currency ?? "EUR", eligibilityRule: item.eligibilityRule ?? "",
      perUserLimit: String(item.perUserLimit ?? 1), globalLimit: item.globalLimit == null ? "" : String(item.globalLimit),
      campaignKey: item.campaignKey ?? "", providerOfferId: item.providerOfferId ?? "",
      providerProductId: item.providerProductId ?? "", startAt: item.startAt?.slice(0, 16) ?? "",
      endAt: item.endAt?.slice(0, 16) ?? "",
      storeOfferCodeRequired: item.storeOfferCodeRequired ?? false
    });
    setDraftOpen(true);
  }

  async function savePromotion(event: FormEvent) {
    event.preventDefault();
    if (!approvalReason.trim()) return;
    setActionState("loading");
    onError(null);
    try {
      const body = {
        ...draft,
        discountPercent: Number(draft.discountPercent),
        perUserLimit: Number(draft.perUserLimit),
        globalLimit: draft.globalLimit ? Number(draft.globalLimit) : null,
        targetPlan: draft.targetPlan || null,
        targetRegion: draft.targetRegion || null,
        targetProductId: draft.targetProductId || null,
        eligibilityRule: draft.eligibilityRule,
        campaignKey: draft.campaignKey || null,
        providerOfferId: draft.providerOfferId || null,
        providerProductId: draft.providerProductId || null,
        startAt: draft.startAt || null,
        endAt: draft.endAt || null
      };
      const approval = await submitAdminApproval(
        editingId ? "PROMOTION_UPDATE" : "PROMOTION_CREATE",
        editingId ? String(editingId) : "NEW",
        body,
        approvalReason.trim()
      );
      setApprovalNotice({ request: approval, message: editingId ? "Promotion update is waiting for owner approval." : "Promotion creation is waiting for owner approval." });
      resetDraft();
      setDraftOpen(false);
      setApprovalReason("");
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function openPreview(item: AdminPromotion) {
    if (!item.id) return;
    setSelected(item);
    setPreview(null);
    try {
      setPreview(await request<AdminPromotionPreview>(`/api/v1/admin/promotions/${item.id}/preview`, { method: "POST" }));
    } catch (error) {
      onError(formatRequestError(error));
    }
  }

  async function requestPromotionAction(actionType: string, promotionId: number, message: string, reason: string) {
    if (!reason.trim()) return;
    setActionState("loading");
    try {
      const approval = await submitAdminApproval(actionType, String(promotionId), {}, reason.trim());
      setApprovalNotice({ request: approval, message });
      setSelected(null);
      setPreview(null);
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function reconcileSelected() {
    if (!selected?.id || !approvalReason.trim()) return;
    await requestPromotionAction("PROMOTION_RECONCILE", selected.id, "Promotion reconciliation is waiting for owner approval.", approvalReason);
    setApprovalReason("");
  }
  async function deactivate(event: FormEvent) {
    event.preventDefault();
    if (!deactivating?.id) return;
    setActionState("loading");
    try {
      const approval = await submitAdminApproval("PROMOTION_DEACTIVATE", String(deactivating.id), {}, deactivationReason.trim());
      setApprovalNotice({ request: approval, message: "Promotion deactivation is waiting for owner approval." });
      setDeactivating(null);
      setDeactivationReason("");
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  const items = pageData.content ?? [];
  const revenueLabel = (metrics?.revenueByCurrency ?? []).length
    ? (metrics?.revenueByCurrency ?? []).map((item) => `${((item.amountMinor ?? 0) / 100).toFixed(2)} ${item.currency ?? ""}`).join(" ? ")
    : "0.00";
  const hasPromotionLifecycle = (analytics?.promotionStatuses ?? []).some((item) => Number(item.count ?? 0) > 0);
  const hasPromotionTypes = (analytics?.promotionTypes ?? []).some((item) => Number(item.count ?? 0) > 0);
  const hasRedemptionOutcomes = (analytics?.redemptionStatuses ?? []).some((item) => Number(item.count ?? 0) > 0);
  const hasRejectionCategories = (analytics?.rejectionCategories ?? []).some((item) => Number(item.count ?? 0) > 0);
  const hasRedemptionActivity = (analytics?.redemptionTrend ?? []).some(
    (item) => Number(item.attempts ?? 0) + Number(item.duplicateAttempts ?? 0) > 0
  );  return (
    <div className="view-stack commercial-ops-view">
      <SectionToolbar title={tx("Promotion operations", "Promosyon operasyonları")} description={tx("Store-safe campaign lifecycle, targeting, provider mapping and conversion health.", "Mağaza güvenli kampanya yaşam döngüsü, hedefleme, sağlayıcı eşleştirme ve dönüşüm sağlığı.")} state={state} onReload={load} />
      {approvalNotice && <ApprovalSubmissionNotice request={approvalNotice.request} message={approvalNotice.message} isOwner={false} />}

      <section className="promotion-command-hero">
        <div><span>{tx("COMMERCIAL CONTROL", "TİCARİ KONTROL")}</span><h2>{tx("Manage offers from draft to verified conversion.", "Teklifleri taslaktan doğrulanmış dönüşüme kadar yönetin.")}</h2><p>{tx("Owner approval, provider verification and abuse controls remain visible in one operational flow.", "Owner onayı, sağlayıcı doğrulaması ve kötüye kullanım kontrolleri tek operasyon akışında görünür kalır.")}</p></div>
        <div className="promotion-hero-actions"><button className="primary-button" type="button" onClick={() => setDraftOpen(true)}>{tx("Create promotion", "Promosyon oluştur")}</button><button className="ghost-button" type="button" onClick={() => setAnalyticsOpen((current) => !current)}>{analyticsOpen ? tx("Hide analytics", "Grafikleri gizle") : tx("Show analytics", "Grafikleri göster")}</button></div>
      </section>

      <div className="metrics-grid commercial-metrics">
        <MetricCard label={tx("Active promotions", "Aktif promosyonlar")} value={formatValue(metrics?.activePromos)} hint={tx("Currently enabled commercial rules", "Şu anda etkin ticari kurallar")} />
        <MetricCard label={tx("Redemptions", "Kullanımlar")} value={formatValue(metrics?.totalRedemptions)} hint={`${formatValue(metrics?.uniqueUsers)} ${tx("unique users", "benzersiz kullanıcı")}`} />
        <MetricCard label={tx("Conversion", "Dönüşüm")} value={`${formatValue(metrics?.conversionRate)}%`} hint={`${formatValue(metrics?.convertedRedemptions)} ${tx("provider-confirmed", "sağlayıcı onaylı")}`} />
        <MetricCard label={tx("Rejected", "Reddedilen")} value={`${formatValue(metrics?.rejectionRate)}%`} hint={tx("Provider or eligibility rejection rate", "Sağlayıcı veya uygunluk ret oranı")} />
        <MetricCard label={tx("Converted revenue", "Dönüşen gelir")} value={revenueLabel} hint={tx("Separated by provider currency", "Sağlayıcı para birimine göre ayrıldı")} />
        <MetricCard label={tx("Abuse signals", "Kötüye kullanım sinyalleri")} value={formatValue(metrics?.abuseSignals)} hint={`${formatValue(metrics?.duplicateAttempts)} ${tx("duplicate", "tekrar")} / ${formatValue(metrics?.limitRejections)} ${tx("limit", "limit")}`} />
      </div>

      <div className="promotion-operations-analytics-head">
        <div>
          <strong>{tx("Promotion performance", "Promosyon performansı")}</strong>
          <span>{tx("Aggregate conversion, rejection and abuse signals without user or provider identifiers.", "Kullanıcı veya sağlayıcı kimliği olmadan toplu dönüşüm, ret ve kötüye kullanım sinyalleri.")}</span>
        </div>
        <label>
          {tx("Analytics window", "Analiz aralığı")}
          <select value={analyticsWindowDays} onChange={(event) => setAnalyticsWindowDays(Number(event.target.value))}>
            <option value={7}>{tx("7 days", "7 gün")}</option>
            <option value={30}>{tx("30 days", "30 gün")}</option>
            <option value={90}>{tx("90 days", "90 gün")}</option>
          </select>
        </label>
      </div>

      {analytics && analyticsOpen && (
        <Suspense fallback={<div className="chart-loading">{tx("Loading promotion analytics...", "Promosyon analizleri yükleniyor...")}</div>}>
          <div className="promotion-operations-chart-grid">
            <Panel title={tx("Promotion lifecycle", "Promosyon yaşam döngüsü")} description={tx("Current commercial rule status distribution.", "Mevcut ticari kural durumlarının dağılımı.")} className={`promotion-operations-chart-panel promotion-lifecycle-panel ${hasPromotionLifecycle ? "" : "is-empty"}`}>
              {hasPromotionLifecycle ? <PromotionLifecycleChart analytics={analytics} /> : <div className="promotion-chart-empty">{tx("No lifecycle data yet.", "Henüz yaşam döngüsü verisi yok.")}</div>}
            </Panel>
            <Panel title={tx("Offer mix", "Teklif dağılımı")} description={tx("Campaign, introductory, win-back and support offer distribution.", "Kampanya, başlangıç, geri kazanım ve destek tekliflerinin dağılımı.")} className={`promotion-operations-chart-panel promotion-mix-panel ${hasPromotionTypes ? "" : "is-empty"}`}>
              {hasPromotionTypes ? <PromotionTypeChart analytics={analytics} /> : <div className="promotion-chart-empty">{tx("No offer type data yet.", "Henüz teklif türü verisi yok.")}</div>}
            </Panel>
            <Panel title={tx("Redemption outcomes", "Kullanım sonuçları")} description={tx("Provider-confirmed conversion and rejection states.", "Sağlayıcı onaylı dönüşüm ve ret durumları.")} className={`promotion-operations-chart-panel promotion-outcome-panel ${hasRedemptionOutcomes ? "" : "is-empty"}`}>
              {hasRedemptionOutcomes ? <RedemptionOutcomeChart analytics={analytics} /> : <div className="promotion-chart-empty">{tx("No redemption outcomes yet.", "Henüz kullanım sonucu yok.")}</div>}
            </Panel>
            <Panel title={tx("Rejection concentration", "Ret yoğunluğu")} description={tx("Sanitized rejection categories; raw reasons remain outside analytics.", "Temizlenmiş ret kategorileri; ham nedenler analiz dışında kalır.")} className={`promotion-operations-chart-panel promotion-rejection-panel ${hasRejectionCategories ? "" : "is-empty"}`}>
              {hasRejectionCategories ? <PromotionRejectionChart analytics={analytics} /> : <div className="promotion-chart-empty">{tx("No rejection categories yet.", "Henüz ret kategorisi yok.")}</div>}
            </Panel>
            <Panel title={tx("Redemption flow", "Kullanım akışı")} description={tx(`Attempts, conversions, rejections and duplicate signals across ${analytics.windowDays ?? analyticsWindowDays} days.`, `${analytics.windowDays ?? analyticsWindowDays} günlük deneme, dönüşüm, ret ve tekrar sinyalleri.`)} className={`promotion-operations-chart-panel promotion-redemption-trend-panel ${hasRedemptionActivity ? "" : "is-empty"}`}>
              {hasRedemptionActivity ? <PromotionRedemptionTrendChart analytics={analytics} /> : <div className="promotion-chart-empty promotion-trend-empty">{tx("No redemption activity in this window.", "Bu aralıkta kullanım etkinliği yok.")}</div>}
            </Panel>
          </div>
        </Suspense>
      )}
      {draftOpen && <div className="modal-backdrop promotion-create-backdrop" role="presentation" onClick={() => setDraftOpen(false)}>
        <section className="modal-card promotion-create-modal" role="dialog" aria-modal="true" aria-label={editingId ? tx("Edit promotion", "Promosyonu düzenle") : tx("Create promotion", "Promosyon oluştur")} onClick={(event) => event.stopPropagation()}>
          <header className="modal-header promotion-create-header"><div><span>{tx("COMMERCIAL SETUP", "TİCARİ KURULUM")}</span><h2>{editingId ? tx("Edit draft promotion", "Promosyon taslağını düzenle") : tx("Create promotion", "Promosyon oluştur")}</h2><p>{tx("Activation remains a separate owner-approved action.", "Etkinleştirme ayrı ve owner onaylı bir işlem olarak kalır.")}</p></div><button className="modal-icon-close" type="button" aria-label={tx("Close", "Kapat")} onClick={() => setDraftOpen(false)}>×</button></header>
          <div className="modal-body promotion-create-body">
          <form className="commercial-form" onSubmit={savePromotion}>
            <div className="commercial-form-grid">
              <label>{tx("Code", "Kod")}<input required maxLength={80} value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value.toUpperCase() })} /></label>
              <label>{tx("Name", "Ad")}<input required maxLength={160} value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
              <label>{tx("Offer type", "Teklif türü")}<select value={draft.promoType} onChange={(event) => setDraft({ ...draft, promoType: event.target.value })}><option>CAMPAIGN</option><option>INTRO_OFFER</option><option>WIN_BACK</option><option>SUPPORT_GRANT</option></select></label>
              <label>{tx("Discount %", "İndirim %")}<input required min="0" max="100" step="0.01" type="number" value={draft.discountPercent} onChange={(event) => setDraft({ ...draft, discountPercent: event.target.value })} /></label>
              <label>{tx("Store", "Mağaza")}<select value={draft.targetStore} onChange={(event) => setDraft({ ...draft, targetStore: event.target.value })}><option>ALL</option><option>REVENUECAT</option><option>APPLE_APP_STORE</option><option>GOOGLE_PLAY</option></select></label>
              <label>{tx("Plan", "Plan")}<select value={draft.targetPlan} onChange={(event) => setDraft({ ...draft, targetPlan: event.target.value })}><option value="">{tx("All plans", "Tüm planlar")}</option><option>FREE</option><option>PLUS</option><option>PRO</option></select></label>
              <label>{tx("Region", "Bölge")}<select value={draft.targetRegion} onChange={(event) => setDraft({ ...draft, targetRegion: event.target.value })}><option value="">{tx("All regions", "Tüm bölgeler")}</option><option>GLOBAL</option><option>TR</option><option>UK_IE</option><option>EU</option></select></label>
              <label>{tx("Currency", "Para birimi")}<input required maxLength={3} value={draft.currency} onChange={(event) => setDraft({ ...draft, currency: event.target.value.toUpperCase() })} /></label>
              <label>{tx("Per-user limit", "Kullanıcı başına limit")}<input required min="1" type="number" value={draft.perUserLimit} onChange={(event) => setDraft({ ...draft, perUserLimit: event.target.value })} /></label>
              <label>{tx("Global limit", "Global limit")}<input min="1" type="number" value={draft.globalLimit} onChange={(event) => setDraft({ ...draft, globalLimit: event.target.value })} placeholder={tx("Unlimited", "Sınırsız")} /></label>
              <label>{tx("Start time", "Başlangıç zamanı")}<input type="datetime-local" value={draft.startAt} onChange={(event) => setDraft({ ...draft, startAt: event.target.value })} /></label>
              <label>{tx("End time", "Bitiş zamanı")}<input type="datetime-local" value={draft.endAt} onChange={(event) => setDraft({ ...draft, endAt: event.target.value })} /></label>
              <label>RevenueCat offering ID (optional for native code)<input maxLength={160} value={draft.providerOfferId} onChange={(event) => setDraft({ ...draft, providerOfferId: event.target.value })} /></label>
              <label>Store product ID<input required={draft.storeOfferCodeRequired} maxLength={160} value={draft.providerProductId} onChange={(event) => setDraft({ ...draft, providerProductId: event.target.value })} /></label>
              <label>Campaign key<input maxLength={120} value={draft.campaignKey} onChange={(event) => setDraft({ ...draft, campaignKey: event.target.value })} /></label>
              <label>Eligibility rule<select required value={draft.eligibilityRule} onChange={(event) => setDraft({ ...draft, eligibilityRule: event.target.value })}><option value="ALL_USERS">All users</option><option value="NO_PRIOR_PROMO_REDEMPTION">No prior promo</option><option value="FIRST_PAID_PURCHASE">First paid purchase</option><option value="LAPSED_SUBSCRIBER">Lapsed subscriber</option><option value="ADMIN_SUPPORT_ONLY">Admin support only</option></select></label>
            </div>
            <div className="promotion-modal-guardrail"><strong>{tx("Provider verification required", "Sağlayıcı doğrulaması gerekli")}</strong><span>{tx("Paid access is granted only after a verified RevenueCat, App Store or Google Play event.", "Ücretli erişim yalnızca doğrulanmış RevenueCat, App Store veya Google Play olayından sonra verilir.")}</span></div>
            <label className="checkbox-row"><input type="checkbox" checked={draft.storeOfferCodeRequired} onChange={(event) => setDraft({ ...draft, storeOfferCodeRequired: event.target.checked })} /><span>{tx("Require a verified App Store / Play offer code. The admin code must exactly match the store offer code.", "Doğrulanmış App Store / Play teklif kodu gerektir. Admin kodu mağaza teklif koduyla tamamen eşleşmelidir.")}</span></label>
            <label>{tx("Description", "Açıklama")}<textarea maxLength={600} rows={2} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
            <label>{tx("Required approval reason", "Zorunlu onay nedeni")}<textarea required maxLength={500} rows={3} value={approvalReason} onChange={(event) => setApprovalReason(event.target.value)} placeholder={tx("Explain the commercial need and expected impact.", "Ticari ihtiyacı ve beklenen etkiyi açıklayın.")} /></label>
            <div className="inline-actions commercial-form-actions"><button className="primary-button" disabled={!approvalReason.trim() || actionState === "loading"} type="submit">{editingId ? tx("Request draft update", "Taslak güncellemesi iste") : tx("Request promotion creation", "Promosyon oluşturma iste")}</button><button className="ghost-button" onClick={() => { resetDraft(); setDraftOpen(false); }} type="button">{tx("Cancel", "İptal")}</button></div>
          </form>
          </div>
        </section>
      </div>}

      <Panel title={tx("Promotion inventory", "Promosyon envanteri")} description={tx("Server-side paginated commercial rules and current lifecycle state.", "Sunucu tarafında sayfalanan ticari kurallar ve mevcut yaşam döngüsü durumu.")}>
        <form className="commercial-filter-grid" onSubmit={(event) => { event.preventDefault(); setPage(0); setAppliedSearch(search.trim()); }}>
          <label>{tx("Search", "Ara")}<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={tx("Code, name or campaign", "Kod, ad veya kampanya")} /></label>
          <label>{tx("Status", "Durum")}<select value={status} onChange={(event) => { setPage(0); setStatus(event.target.value); }}><option value="">{tx("All statuses", "Tüm durumlar")}</option><option>DRAFT</option><option>ACTIVE</option><option>DEACTIVATED</option><option>EXPIRED</option></select></label>
          <label>{tx("Type", "Tür")}<select value={type} onChange={(event) => { setPage(0); setType(event.target.value); }}><option value="">{tx("All types", "Tüm türler")}</option><option>CAMPAIGN</option><option>INTRO_OFFER</option><option>WIN_BACK</option><option>SUPPORT_GRANT</option></select></label>
          <label>{tx("Store", "Mağaza")}<select value={store} onChange={(event) => { setPage(0); setStore(event.target.value); }}><option value="">{tx("All stores", "Tüm mağazalar")}</option><option>ALL</option><option>REVENUECAT</option><option>APPLE_APP_STORE</option><option>GOOGLE_PLAY</option></select></label>
          <button className="primary-button" type="submit">{tx("Apply", "Uygula")}</button>
        </form>
        <DataTable columns={[tx("Promotion", "Promosyon"), tx("Type", "Tür"), tx("Target", "Hedef"), tx("Window", "Zaman aralığı"), tx("Usage", "Kullanım"), tx("Mapping", "Eşleştirme"), tx("Status", "Durum"), tx("Actions", "İşlemler")]} empty={tx("No promotions match these filters.", "Bu filtrelere uyan promosyon yok.")} rows={items.map((item) => [
          <div className="table-stack"><strong>{item.name ?? "-"}</strong><small>{item.code ?? "-"} ? {formatValue(item.discountPercent)}%</small></div>,
          humanizeFeature(item.promoType),
          <div className="table-stack"><span>{item.targetPlan ?? "All plans"}</span><small>{item.targetStore ?? "ALL"} ? {item.targetRegion ?? "All regions"}</small></div>,
          <div className="table-stack"><span>{formatDate(item.startAt)}</span><small>to {formatDate(item.endAt)}</small></div>,
          `${formatValue(item.usedCount)} / ${item.globalLimit == null ? "?" : formatValue(item.globalLimit)}`,
          <span className={`status-pill ${item.providerMappingReady ? "live" : ""}`}>{item.providerMappingReady ? "Ready" : "Missing"}</span>,
          <span className={`status-pill ${item.status === "ACTIVE" ? "live" : ""}`}>{item.status ?? "-"}</span>,
          <div className="inline-actions commercial-row-actions"><button className="ghost-button" onClick={() => void openPreview(item)} type="button">{tx("Inspect", "İncele")}</button>{item.status === "DRAFT" && <button className="ghost-button" onClick={() => editPromotion(item)} type="button">{tx("Edit", "Düzenle")}</button>}{item.status === "ACTIVE" && <button className="ghost-button danger-button" onClick={() => setDeactivating(item)} type="button">{tx("Deactivate", "Devre dışı bırak")}</button>}</div>
        ])} />
        <PaginationControls page={pageData.page ?? page} pageSize={pageData.size ?? size} totalElements={pageData.totalElements ?? 0} totalPages={pageData.totalPages ?? 0} first={pageData.first ?? page === 0} last={pageData.last ?? true} onPageChange={setPage} onPageSizeChange={(value) => { setPage(0); setSize(value); }} />
      </Panel>

      <Panel title={tx("Provider redemption ledger", "Sağlayıcı kullanım kaydı")} description={tx("Sanitized provider attribution, eligibility rejections and duplicate signals.", "Temizlenmiş sağlayıcı ilişkilendirmesi, uygunluk retleri ve tekrar sinyalleri.")}>
        <div className="commercial-ledger-toolbar"><label>{tx("Status", "Durum")}<select value={redemptionStatus} onChange={(event) => { setRedemptionPage(0); setRedemptionStatus(event.target.value); }}><option value="">{tx("All statuses", "Tüm durumlar")}</option><option>CONVERTED</option><option>REJECTED</option><option>PROVIDER_VERIFIED</option><option>RESERVED</option></select></label></div>
        <DataTable columns={[tx("Promotion", "Promosyon"), tx("User", "Kullanıcı"), tx("Result", "Sonuç"), tx("Provider event", "Sağlayıcı olayı"), tx("Value", "Değer"), tx("Abuse", "Kötüye kullanım"), tx("Time", "Zaman")]} empty={tx("No provider redemption records returned.", "Sağlayıcı kullanım kaydı bulunamadı.")} rows={(redemptions.content ?? []).map((item) => [
          item.promoCode ?? `#${item.promoId ?? "-"}`,
          <div className="table-stack"><strong>{item.maskedUserEmail ?? "hidden"}</strong><small>User #{item.userId ?? "-"}</small></div>,
          <div className="table-stack"><span className={`status-pill ${item.status === "CONVERTED" ? "live" : ""}`}>{item.status ?? "-"}</span><small>{item.rejectionReason ?? "Eligible"}</small></div>,
          item.providerEventReference ?? "-",
          item.amountMinor == null ? "-" : `${(item.amountMinor / 100).toFixed(2)} ${item.currency ?? ""}`,
          <div className="table-stack"><strong>{formatValue(item.duplicateHits)}</strong><small>{item.lastDuplicateAt ? tx(`Last ${formatDate(item.lastDuplicateAt)}`, `Son ${formatDate(item.lastDuplicateAt)}`) : tx("No duplicate", "Tekrar yok")}</small></div>,
          formatDate(item.convertedAt ?? item.appliedAt)
        ])} />
        <PaginationControls page={redemptions.page ?? redemptionPage} pageSize={redemptions.size ?? 10} totalElements={redemptions.totalElements ?? 0} totalPages={redemptions.totalPages ?? 0} first={redemptions.first ?? redemptionPage === 0} last={redemptions.last ?? true} onPageChange={setRedemptionPage} onPageSizeChange={() => undefined} />
      </Panel>
      {selected && <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
        <section ref={previewDialogRef} tabIndex={-1} className="modal-card compact commercial-preview-modal" role="dialog" aria-modal="true" aria-label={tx("Promotion validation", "Promosyon doğrulaması")} onClick={(event) => event.stopPropagation()}>
          <header className="modal-header"><div><span>{tx("COMMERCIAL VALIDATION", "TİCARİ DOĞRULAMA")}</span><h2>{selected.name}</h2><p>{selected.code} · {selected.promoType}</p></div><button className="modal-icon-close" onClick={() => setSelected(null)} type="button">x</button></header>
          <div className="modal-body">
            <div className="commercial-preview-summary"><div><span>{tx("Estimated audience", "Tahmini kitle")}</span><strong>{formatValue(preview?.estimatedAudience)}</strong></div><div><span>{tx("Provider mapping", "Sağlayıcı eşleştirmesi")}</span><strong>{preview?.providerMappingReady ? tx("Ready", "Hazır") : tx("Incomplete", "Eksik")}</strong></div><div><span>{tx("Activation", "Etkinleştirme")}</span><strong>{preview?.activationReady ? tx("Ready", "Hazır") : tx("Blocked", "Engelli")}</strong></div></div>
            <div className="commercial-validation-list">{preview?.validationIssues?.length ? preview.validationIssues.map((issue) => <p key={issue}>{issue}</p>) : <p className="success-note">No activation blockers detected.</p>}</div>
            <p className="commercial-entitlement-warning">Activating this promotion does not grant entitlement. Store/provider verification remains mandatory.</p>
            <label>Required approval reason<textarea required maxLength={500} rows={3} value={approvalReason} onChange={(event) => setApprovalReason(event.target.value)} placeholder="Explain why owner approval is required." /></label>
          </div>
          <footer className="modal-actions padded-actions"><button className="ghost-button" disabled={!approvalReason.trim() || actionState === "loading"} onClick={() => void reconcileSelected()} type="button">Request reconciliation</button>{selected.status !== "ACTIVE" && <button className="primary-button" disabled={!preview?.activationReady || !approvalReason.trim() || actionState === "loading"} onClick={() => { if (selected.id) { void requestPromotionAction("PROMOTION_ACTIVATE", selected.id, "Promotion activation is waiting for owner approval.", approvalReason); setApprovalReason(""); } }} type="button">Request activation</button>}</footer>
        </section>
      </div>}

      {deactivating && <div className="modal-backdrop" role="presentation" onClick={() => setDeactivating(null)}><form ref={deactivateDialogRef} tabIndex={-1} className="modal-card compact commercial-deactivate-modal" role="dialog" aria-modal="true" aria-label="Deactivate promotion" onSubmit={deactivate} onClick={(event) => event.stopPropagation()}><header className="modal-header"><div><span>DEACTIVATE PROMOTION</span><h2>{deactivating.name}</h2></div><button className="modal-icon-close" onClick={() => setDeactivating(null)} type="button" aria-label="Close promotion deactivation">×</button></header><div className="modal-body"><label>Required audit reason<textarea required maxLength={500} rows={4} value={deactivationReason} onChange={(event) => setDeactivationReason(event.target.value)} placeholder="Why must this promotion stop?" /></label></div><footer className="modal-actions padded-actions"><button className="ghost-button" onClick={() => setDeactivating(null)} type="button">Cancel</button><button className="primary-button danger-button" disabled={!deactivationReason.trim() || actionState === "loading"} type="submit">Deactivate</button></footer></form></div>}
    </div>
  );
}
