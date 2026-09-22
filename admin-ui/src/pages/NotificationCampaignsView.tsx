import { FormEvent, lazy, Suspense, useEffect, useRef, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminAccessProfile, AdminApprovalRequest, AdminBrevoTemplate, NotificationCampaign, NotificationCampaignSummary, NotificationCampaignPreview, NotificationCampaignRecipient } from "../types";

import { DataTable, EmptyState, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";
import { CommunicationHero } from "../CommunicationsPrimitives";
import { useAdminLocale } from "../admin/locale";

import { SectionKey } from "./../admin/navigation";

import { ApprovalSubmissionNotice, Badge, ConfirmDialog, DatePickerButton, MARKET_REGIONS, PLAN_ORDER, PREFERRED_LANGUAGES, combineStates, formatDate, formatValue, submitAdminApproval, toDateInputValue, useEndpoint } from "./../admin/shared";

export const CampaignDeliveryChart = lazy(() => import("../CampaignOperationsCharts").then((module) => ({ default: module.CampaignDeliveryChart })));

export const CampaignEngagementFunnel = lazy(() => import("../CampaignOperationsCharts").then((module) => ({ default: module.CampaignEngagementFunnel })));

export const CampaignStatusChart = lazy(() => import("../CampaignOperationsCharts").then((module) => ({ default: module.CampaignStatusChart })));

export function NotificationCampaignsView({ onError, onNavigate, accessProfile }: { onError: (message: string | null) => void; onNavigate: (section: SectionKey) => void; accessProfile: AdminAccessProfile | null }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const emptyDraft = { name: "", title: "", message: "", category: "SYSTEM", channel: "IN_APP_AND_PUSH", emailTemplateId: "", targetRoute: "", targetPlan: "", targetRegion: "", targetLanguage: "", frequencyCapHours: 24, frequencyCapMax: 3 };
  const [statusFilter, setStatusFilter] = useState("");
  const [campaignWindowDays, setCampaignWindowDays] = useState(31);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [draft, setDraft] = useState(emptyDraft);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [scheduledAt, setScheduledAt] = useState("");
  const [preview, setPreview] = useState<NotificationCampaignPreview | null>(null);
  const audiencePreviewRequest = useRef(0);
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [notice, setNotice] = useState<string | null>(null);
  const [approvalNotice, setApprovalNotice] = useState<{ request: AdminApprovalRequest; message: string } | null>(null);
  const [confirmSchedule, setConfirmSchedule] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [historyPreview, setHistoryPreview] = useState<{ campaign: NotificationCampaign; preview: NotificationCampaignPreview } | null>(null);
  const composerRef = useRef<HTMLFormElement | null>(null);
  const campaignNameRef = useRef<HTMLInputElement | null>(null);
  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (statusFilter) params.set("status", statusFilter);
  const { data, state, reload } = useEndpoint<PageResponse<NotificationCampaign>>("/api/v1/admin/notification-campaigns?" + params.toString(), onError);
  const summaryPath = "/api/v1/admin/notification-campaigns/summary?windowDays=" + campaignWindowDays;
  const { data: campaignSummary, state: summaryState, reload: reloadSummary } = useEndpoint<NotificationCampaignSummary>(summaryPath, onError);
  const { data: emailTemplates, state: templateState, reload: reloadTemplates } = useEndpoint<AdminBrevoTemplate[]>("/api/v1/admin/notification-campaigns/email-templates", onError);
  const rows = data?.content ?? [];
  const active = (campaignSummary?.campaignStatuses ?? []).filter((item) => item.name === "SCHEDULED" || item.name === "PROCESSING").reduce((sum, item) => sum + Number(item.count ?? 0), 0);
  const hasDeliveryMetrics = [campaignSummary?.deliveredCount, campaignSummary?.suppressedCount, campaignSummary?.failedRecipientCount]
    .some((value) => Number(value ?? 0) > 0);
  const hasEngagementMetrics = [campaignSummary?.deliveredCount, campaignSummary?.openedCount, campaignSummary?.clickedCount, campaignSummary?.convertedCount]
    .some((value) => Number(value ?? 0) > 0);
  const hasCampaignStatuses = (campaignSummary?.campaignStatuses ?? []).some((item) => Number(item.count ?? 0) > 0);
  useEffect(() => { if (notice) setApprovalNotice(null); }, [notice]);

  function resetDraft() {
    setDraft(emptyDraft);
    setSelectedId(null);
    setScheduledAt("");
    setPreview(null);
    setNotice(null);
  }

  function startNewDraft() {
    resetDraft();
    setComposerOpen(true);
    setActionState("idle");
    setNotice("New campaign draft opened. Complete the form below and select Save draft.");
    window.requestAnimationFrame(() => {
      composerRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      campaignNameRef.current?.focus({ preventScroll: true });
    });
  }

  function campaignPayload(source = draft) {
    return {
      ...source,
      targetRoute: source.targetRoute || null,
      emailTemplateId: source.channel.includes("EMAIL") ? Number(source.emailTemplateId) || null : null,
      targetPlan: source.targetPlan || null,
      targetRegion: source.targetRegion || null,
      targetLanguage: source.targetLanguage || null
    };
  }

  async function updateAudienceFilter(field: "targetPlan" | "targetRegion" | "targetLanguage", value: string) {
    const nextDraft = { ...draft, [field]: value };
    setDraft(nextDraft);
    setPreview(null);
    if (!selectedId) {
      setNotice("Save the draft before calculating its filtered audience.");
      return;
    }
    const requestId = ++audiencePreviewRequest.current;
    setActionState("loading");
    try {
      await request<NotificationCampaign>("/api/v1/admin/notification-campaigns/" + selectedId, {
        method: "PUT",
        body: campaignPayload(nextDraft)
      });
      const result = await request<NotificationCampaignPreview>("/api/v1/admin/notification-campaigns/" + selectedId + "/preview", { method: "POST" });
      if (requestId !== audiencePreviewRequest.current) return;
      setPreview(result);
      setNotice("Estimated audience updated for the current filters.");
      setActionState("ready");
      await reload();
    } catch (error) {
      if (requestId !== audiencePreviewRequest.current) return;
      setActionState("error");
      onError(formatRequestError(error));
    }
  }
  function campaignDateTimeValue(date: Date) {
    const hours = String(date.getHours()).padStart(2, "0");
    const minutes = String(date.getMinutes()).padStart(2, "0");
    return toDateInputValue(date) + "T" + hours + ":" + minutes;
  }

  function setDeliveryOffset(minutes: number) {
    const next = new Date(Date.now() + minutes * 60_000);
    next.setSeconds(0, 0);
    setScheduledAt(campaignDateTimeValue(next));
  }

  function setDeliveryDate(value: string) {
    const currentTime = scheduledAt.slice(11, 16);
    const fallback = new Date(Date.now() + 15 * 60_000);
    const time = currentTime || String(fallback.getHours()).padStart(2, "0") + ":" + String(fallback.getMinutes()).padStart(2, "0");
    setScheduledAt(value + "T" + time);
  }

  function setDeliveryTime(value: string) {
    if (!value) {
      setScheduledAt("");
      return;
    }
    const date = scheduledAt.slice(0, 10) || toDateInputValue(new Date());
    setScheduledAt(date + "T" + value);
  }
  function editCampaign(item: NotificationCampaign) {
    if (item.status !== "DRAFT" || !item.id) return;
    setSelectedId(item.id);
    setComposerOpen(true);
    setDraft({
      name: item.name ?? "",
      title: item.title ?? "",
      message: item.message ?? "",
      category: item.category ?? "SYSTEM",
      channel: item.channel ?? "IN_APP_AND_PUSH",
      emailTemplateId: item.emailTemplateId ? String(item.emailTemplateId) : "",
      targetRoute: item.targetRoute ?? "",
      targetPlan: item.targetPlan ?? "",
      targetRegion: item.targetRegion ?? "",
      targetLanguage: item.targetLanguage ?? "",
      frequencyCapHours: item.frequencyCapHours ?? 24,
      frequencyCapMax: item.frequencyCapMax ?? 3
    });
    setPreview(null);
    setNotice("Editing saved draft #" + item.id + ". Update the form and select Save draft.");
    window.requestAnimationFrame(() => {
      composerRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      campaignNameRef.current?.focus({ preventScroll: true });
    });
  }

  async function saveDraft(event: FormEvent) {
    event.preventDefault();
    setActionState("loading");
    setNotice(null);
    try {
      const payload = campaignPayload();
      const saved = await request<NotificationCampaign>(
        selectedId ? "/api/v1/admin/notification-campaigns/" + selectedId : "/api/v1/admin/notification-campaigns",
        { method: selectedId ? "PUT" : "POST", body: payload }
      );
      setSelectedId(saved.id ?? selectedId);
      setNotice("Campaign draft saved. Delivery is not queued until Schedule delivery is confirmed.");
      setActionState("ready");
      await reload();
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function previewAudience() {
    if (!selectedId) {
      setNotice("Save the draft before previewing its audience.");
      return;
    }
    setActionState("loading");
    try {
      await request<NotificationCampaign>("/api/v1/admin/notification-campaigns/" + selectedId, {
        method: "PUT",
        body: campaignPayload()
      });
      const result = await request<NotificationCampaignPreview>("/api/v1/admin/notification-campaigns/" + selectedId + "/preview", { method: "POST" });
      setPreview(result);
      setNotice("Audience preview refreshed from the current filters.");
      setActionState("ready");
      await reload();
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function openHistoryPreview(item: NotificationCampaign) {
    if (!item.id) return;
    setActionState("loading");
    try {
      const result = await request<NotificationCampaignPreview>("/api/v1/admin/notification-campaigns/" + item.id + "/preview", { method: "POST" });
      setHistoryPreview({ campaign: item, preview: result });
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function scheduleCampaign() {
    if (!selectedId) return;
    setActionState("loading");
    try {
      const approval = await submitAdminApproval(
        "NOTIFICATION_CAMPAIGN_SCHEDULE",
        String(selectedId),
        { scheduledAt: scheduledAt ? scheduledAt + ":00" : null },
        `Schedule notification campaign ${selectedId}`
      );
      setConfirmSchedule(false);
      setNotice(null);
      setApprovalNotice({ request: approval, message: "Campaign schedule is pending approval. No notifications have been sent yet." });
      setActionState("ready");
    } catch (error) {
      setConfirmSchedule(false);
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function cancelCampaign(item: NotificationCampaign) {
    if (!item.id) return;
    setActionState("loading");
    try {
      await request<NotificationCampaign>("/api/v1/admin/notification-campaigns/" + item.id + "/cancel", { method: "POST" });
      setNotice("Campaign cancelled. Already processed recipients are not duplicated or removed.");
      setActionState("ready");
      await reload();
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  return (
    <div className="stack communications-ops-view notification-campaign-page">
      <SectionToolbar title={tx("Notification campaigns", "Bildirim kampanyaları")} state={combineStates([state, summaryState, templateState, actionState === "idle" ? "ready" : actionState])} onReload={() => { void reload(); void reloadSummary(); void reloadTemplates(); }}>
        <button className="ghost-button" type="button" onClick={() => onNavigate("notificationDefinitions")}>{tx("Manage system rules", "Sistem kurallarını yönet")}</button>
      </SectionToolbar>
      <CommunicationHero eyebrow={tx("Audience delivery", "Kitle teslimatı")} title={tx("Plan campaigns, verify consent and follow engagement in one workspace.", "Kampanyaları planlayın, onayı doğrulayın ve etkileşimi tek çalışma alanından izleyin.")} description={tx("Campaign creation remains separate from system notifications while delivery and conversion stay measurable.", "Kampanya oluşturma sistem bildirimlerinden ayrı kalırken teslimat ve dönüşüm ölçülebilir olur.")} status={<span className="communication-status-pill good">{active} {tx("ACTIVE", "AKTİF")}</span>} actions={<button className="primary-button" type="button" onClick={startNewDraft}>{tx("Create campaign", "Kampanya oluştur")}</button>} />
      {approvalNotice && <ApprovalSubmissionNotice {...approvalNotice} isOwner={accessProfile?.role === "OWNER"} />}
      {notice && <div className="form-notice">{notice}</div>}
      <div className="metric-grid compact-grid">
        <MetricCard label={tx("Campaigns", "Kampanyalar")} value={formatValue(campaignSummary?.campaignCount)} hint={`${campaignWindowDays} ${tx("day aggregate", "günlük toplam")}`} />
        <MetricCard label={tx("Active delivery", "Aktif teslimat")} value={formatValue(active)} hint={`${campaignWindowDays} ${tx("day aggregate", "günlük toplam")}`} />
        <MetricCard label={tx("Recipients processed", "İşlenen alıcılar")} value={formatValue(campaignSummary?.processedCount)} hint={tx("Privacy-safe aggregate", "Gizlilik güvenli toplam")} />
        <MetricCard label={tx("Push failures", "Push hataları")} value={formatValue(campaignSummary?.pushFailedCount)} hint={tx("Provider delivery failures", "Sağlayıcı teslimat hataları")} />
      </div>

      <Panel title={tx("Campaign performance", "Kampanya performansı")}>
        <div className="campaign-performance-toolbar">
          <p>{tx("Aggregate delivery and engagement metrics. Recipient identities and message payloads are not included.", "Toplu teslimat ve etkileşim metrikleri; alıcı kimlikleri ve mesaj içerikleri dahil edilmez.")}</p>
          <div className="segmented-control compact" role="group" aria-label={tx("Campaign performance window", "Kampanya performans aralığı")}>
            {[7, 31, 90].map((days) => <button className={campaignWindowDays === days ? "active" : ""} key={days} type="button" onClick={() => setCampaignWindowDays(days)}>{days}{tx("d", "g")}</button>)}
          </div>
        </div>
        <div className="campaign-analytics-chart-grid">
          <section className="campaign-chart-panel">
            <header><div><h3>{tx("Delivery health", "Teslimat sağlığı")}</h3><p>{tx("Delivered, suppressed, and failed recipient outcomes.", "Teslim edilen, engellenen ve başarısız alıcı sonuçları.")}</p></div></header>
            {summaryState === "loading" ? <div className="chart-loading">{tx("Loading delivery metrics...", "Teslimat metrikleri yükleniyor...")}</div> : hasDeliveryMetrics && campaignSummary ? <Suspense fallback={<div className="chart-loading">{tx("Loading chart...", "Grafik yükleniyor...")}</div>}><CampaignDeliveryChart summary={campaignSummary} /></Suspense> : <EmptyState title={tx("No delivery data", "Teslimat verisi yok")} message={tx(`No recipient outcomes were recorded in the last ${campaignWindowDays} days.`, `Son ${campaignWindowDays} günde alıcı sonucu kaydedilmedi.`)} />}
          </section>
          <section className="campaign-chart-panel">
            <header><div><h3>{tx("Engagement funnel", "Etkileşim hunisi")}</h3><p>{tx("From successful delivery through recorded conversion.", "Başarılı teslimattan kaydedilen dönüşüme kadar.")}</p></div></header>
            {summaryState === "loading" ? <div className="chart-loading">{tx("Loading engagement metrics...", "Etkileşim metrikleri yükleniyor...")}</div> : hasEngagementMetrics && campaignSummary ? <Suspense fallback={<div className="chart-loading">{tx("Loading chart...", "Grafik yükleniyor...")}</div>}><CampaignEngagementFunnel summary={campaignSummary} /></Suspense> : <EmptyState title={tx("No engagement data", "Etkileşim verisi yok")} message={tx(`No campaign engagement was recorded in the last ${campaignWindowDays} days.`, `Son ${campaignWindowDays} günde kampanya etkileşimi kaydedilmedi.`)} />}
          </section>
          <section className="campaign-chart-panel campaign-status-chart-panel">
            <header><div><h3>{tx("Campaign lifecycle", "Kampanya yaşam döngüsü")}</h3><p>{tx("Campaign count grouped by controlled backend status.", "Kontrollü backend durumuna göre kampanya sayısı.")}</p></div></header>
            {summaryState === "loading" ? <div className="chart-loading">{tx("Loading campaign statuses...", "Kampanya durumları yükleniyor...")}</div> : hasCampaignStatuses && campaignSummary ? <Suspense fallback={<div className="chart-loading">{tx("Loading chart...", "Grafik yükleniyor...")}</div>}><CampaignStatusChart summary={campaignSummary} /></Suspense> : <EmptyState title={tx("No campaign history", "Kampanya geçmişi yok")} message={tx(`No campaigns were created in the last ${campaignWindowDays} days.`, `Son ${campaignWindowDays} günde kampanya oluşturulmadı.`)} />}
          </section>
        </div>
      </Panel>

      <section className={`campaign-composer-launcher ${composerOpen ? "is-open" : ""}`}>
        <div><p className="eyebrow">{tx("Controlled broadcast", "Kontrollü yayın")}</p><h3>{selectedId ? tx("Editing campaign draft", "Kampanya taslağı düzenleniyor") : tx("Campaign composer", "Kampanya oluşturucu")}</h3><p>{tx("Build the audience, consent policy and delivery time in one focused workspace.", "Kitleyi, onay politikasını ve teslimat zamanını tek odaklı alanda hazırlayın.")}</p></div>
        <button className={composerOpen ? "ghost-button" : "primary-button"} type="button" onClick={() => { if (composerOpen) { setComposerOpen(false); resetDraft(); } else { startNewDraft(); } }}>{composerOpen ? tx("Close composer", "Oluşturucuyu kapat") : tx("Create campaign", "Kampanya oluştur")}</button>
      </section>
      {composerOpen && <form className="panel campaign-composer" ref={composerRef} onSubmit={saveDraft}>
        <div className="campaign-composer-header">
          <div><p className="eyebrow">{tx("Controlled broadcast", "Kontrollü yayın")}</p><h3>{selectedId ? tx("Edit draft #", "Taslağı düzenle #") + selectedId : tx("Create campaign draft", "Kampanya taslağı oluştur")}</h3><p>{tx("Define the message, eligible audience and delivery plan before requesting approval.", "Onay istemeden önce mesajı, uygun kitleyi ve teslimat planını tanımlayın.")}</p></div>
          <div className="campaign-composer-status"><span>{selectedId ? tx("Saved draft", "Kayıtlı taslak") : tx("Unsaved draft", "Kaydedilmemiş taslak")}</span><button className="icon-button campaign-guide-button" type="button" aria-label={tx("Open campaign management guide", "Kampanya yönetim rehberini aç")} title={tx("Campaign management guide", "Kampanya yönetim rehberi")} onClick={() => setGuideOpen(true)}>i</button></div>
        </div>
        <div className="campaign-builder-layout">
          <div className="campaign-builder-main">
            <section className="campaign-builder-section">
              <header><span>1</span><div><h4>{tx("Campaign identity", "Kampanya kimliği")}</h4><p>{tx("Name the operation and select how users will receive it.", "Operasyonu adlandırın ve kullanıcılara nasıl ulaşacağını seçin.")}</p></div></header>
              <div className="campaign-form-grid">
                <label className="span-2">{tx("Internal campaign name", "Dahili kampanya adı")}<input ref={campaignNameRef} required maxLength={160} value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder={tx("July service update", "Temmuz hizmet güncellemesi")} /></label>
                <label>{tx("Category", "Kategori")}<select value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })} disabled={draft.channel.includes("EMAIL")}><option value="SYSTEM">{tx("System information", "Sistem bilgisi")}</option><option value="MARKETING">{tx("Marketing / promotion", "Pazarlama / promosyon")}</option></select></label>
                <label>{tx("Channel", "Kanal")}<select value={draft.channel} onChange={(event) => { const channel=event.target.value; setDraft({ ...draft, channel, category: channel.includes("EMAIL") ? "MARKETING" : draft.category }); }}><option value="IN_APP">{tx("In-app only", "Yalnızca uygulama içi")}</option><option value="PUSH">{tx("Push only", "Yalnızca push")}</option><option value="IN_APP_AND_PUSH">{tx("In-app and push", "Uygulama içi ve push")}</option><option value="EMAIL">{tx("Email only", "Yalnızca e-posta")}</option><option value="EMAIL_AND_IN_APP">{tx("Email and in-app", "E-posta ve uygulama içi")}</option><option value="EMAIL_PUSH_IN_APP">{tx("Email, push and in-app", "E-posta, push ve uygulama içi")}</option></select></label>
                {draft.channel.includes("EMAIL")&&<label className="span-4">{tx("Brevo email template", "Brevo e-posta şablonu")}<select required value={draft.emailTemplateId} onChange={(event)=>setDraft({...draft,emailTemplateId:event.target.value})}><option value="">{tx("Select an active template", "Aktif bir şablon seçin")}</option>{(emailTemplates??[]).map(template=><option key={template.id} value={template.id}>{template.name} · {template.subject||`#${template.id}`}</option>)}</select><small>{(emailTemplates??[]).length?tx("Sender and subject are managed in Brevo.", "Gönderen ve konu Brevo üzerinden yönetilir."):tx("No active template returned. Check the Brevo connection.", "Aktif şablon bulunamadı. Brevo bağlantısını kontrol edin.")}</small></label>}
              </div>
            </section>
            <section className="campaign-builder-section">
              <header><span>2</span><div><h4>{tx("Message and destination", "Mesaj ve hedef")}</h4><p>{tx("Write the content users see and where the action should lead.", "Kullanıcının göreceği içeriği ve eylemin yönleneceği alanı belirleyin.")}</p></div></header>
              <div className="campaign-form-grid">
                <label className="span-2">{tx("User-facing title", "Kullanıcı başlığı")}<input required maxLength={120} value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} placeholder={tx("Planned maintenance", "Planlı bakım")} /></label>
                <label className="span-2">{tx("Target route", "Hedef rota")}<input maxLength={255} value={draft.targetRoute} onChange={(event) => setDraft({ ...draft, targetRoute: event.target.value })} placeholder="/settings/subscription" /></label>
                <label className="span-4">{tx("Message", "Mesaj")}<textarea required maxLength={1000} value={draft.message} onChange={(event) => setDraft({ ...draft, message: event.target.value })} placeholder={tx("Write a concise user-facing message.", "Kısa ve anlaşılır bir kullanıcı mesajı yazın.")} /><small>{draft.message.length}/1000</small></label>
              </div>
            </section>
            <section className="campaign-builder-section">
              <header><span>3</span><div><h4>{tx("Audience and contact policy", "Kitle ve iletişim politikası")}</h4><p>{tx("Narrow eligibility and control marketing contact pressure.", "Uygun kitleyi daraltın ve pazarlama iletişim sıklığını yönetin.")}</p></div></header>
              <div className="campaign-audience-strip">
                <label className="campaign-audience-filter">{tx("Plan", "Plan")}<select value={draft.targetPlan} onChange={(event) => void updateAudienceFilter("targetPlan", event.target.value)}><option value="">{tx("All plans", "Tüm planlar")}</option>{PLAN_ORDER.map((plan) => <option value={plan} key={plan}>{plan}</option>)}</select></label>
                <label className="campaign-audience-filter">{tx("Region", "Bölge")}<select value={draft.targetRegion} onChange={(event) => void updateAudienceFilter("targetRegion", event.target.value)}><option value="">{tx("All regions", "Tüm bölgeler")}</option>{MARKET_REGIONS.map((region) => <option value={region} key={region}>{region}</option>)}</select></label>
                <label className="campaign-audience-filter">{tx("Language", "Dil")}<select value={draft.targetLanguage} onChange={(event) => void updateAudienceFilter("targetLanguage", event.target.value)}><option value="">{tx("All languages", "Tüm diller")}</option>{PREFERRED_LANGUAGES.map((language) => <option value={language} key={language}>{language}</option>)}</select></label>
                <div className="campaign-pressure-controls"><label>{tx("Window (hours)", "Aralık (saat)")}<input type="number" min={1} max={168} value={draft.frequencyCapHours} onChange={(event) => setDraft({ ...draft, frequencyCapHours: Number(event.target.value) })} /></label><label>{tx("Maximum messages", "En fazla mesaj")}<input type="number" min={1} max={20} value={draft.frequencyCapMax} onChange={(event) => setDraft({ ...draft, frequencyCapMax: Number(event.target.value) })} /></label></div>
              </div>
              <div className="campaign-pressure-note"><strong>{tx("Contact pressure", "İletişim sıklığı")}</strong><span>{tx("System notices bypass this cap. Marketing recipients above the limit are suppressed and recorded in delivery diagnostics.", "Sistem bildirimleri bu sınırdan etkilenmez. Sınırı aşan pazarlama alıcıları engellenir ve teslimat tanılarına kaydedilir.")}</span></div>
            </section>
            <section className="campaign-builder-section">
              <header><span>4</span><div><h4>{tx("Delivery plan", "Teslimat planı")}</h4><p>{tx("Save first, then send immediately or request a scheduled delivery.", "Önce taslağı kaydedin, ardından hemen gönderin veya zamanlanmış teslimat isteyin.")}</p></div></header>
              <div className="campaign-delivery-control">
            <span className="campaign-field-label">{tx("Delivery time", "Teslimat zamanı")}</span>
            <div className="campaign-delivery-fields">
              <DatePickerButton label={tx("Delivery date", "Teslimat tarihi")} min={toDateInputValue(new Date())} value={scheduledAt.slice(0, 10)} onChange={setDeliveryDate} />
              <label className="campaign-time-control"><span>{tx("Time", "Saat")}</span><input aria-label={tx("Delivery time", "Teslimat zamanı")} type="time" value={scheduledAt.slice(11, 16)} onChange={(event) => setDeliveryTime(event.target.value)} /></label>
            </div>
            <div className="campaign-quick-times" aria-label={tx("Quick delivery time", "Hızlı teslimat zamanı")}>
              <button type="button" onClick={() => setDeliveryOffset(5)}>{tx("In 5 min", "5 dk sonra")}</button>
              <button type="button" onClick={() => setDeliveryOffset(15)}>{tx("In 15 min", "15 dk sonra")}</button>
              {scheduledAt && <button type="button" onClick={() => setScheduledAt("")}>{tx("Clear", "Temizle")}</button>}
            </div>
            <small className="campaign-delivery-help">{tx("Saving keeps the campaign as a draft. Delivery starts only after the approval request is accepted.", "Kaydetme işlemi kampanyayı taslakta tutar. Teslimat yalnızca onay isteği kabul edildikten sonra başlar.")}</small>
              </div>
            </section>
          </div>
          <aside className="campaign-builder-summary">
            <div className="campaign-summary-heading"><p className="eyebrow">{tx("Live summary", "Canlı özet")}</p><h4>{draft.name || tx("Untitled campaign", "Adsız kampanya")}</h4><span>{draft.category === "MARKETING" ? tx("Consent-controlled marketing", "İzin kontrollü pazarlama") : tx("System communication", "Sistem iletişimi")}</span></div>
            <div className="campaign-message-card"><small>{tx("User preview", "Kullanıcı önizlemesi")}</small><strong>{draft.title || tx("Campaign title", "Kampanya başlığı")}</strong><p>{draft.message || tx("Your message will appear here as you type.", "Mesajınız yazdıkça burada görünecek.")}</p>{draft.targetRoute && <span>{draft.targetRoute}</span>}</div>
            <dl><div><dt>{tx("Channel", "Kanal")}</dt><dd>{draft.channel.replaceAll("_", " ")}</dd></div><div><dt>{tx("Audience", "Kitle")}</dt><dd>{preview ? formatValue(preview.estimatedAudience) : tx("Preview after save", "Kaydettikten sonra önizleyin")}</dd></div><div><dt>{tx("Filters", "Filtreler")}</dt><dd>{[draft.targetPlan, draft.targetRegion, draft.targetLanguage].filter(Boolean).join(" · ") || tx("All eligible users", "Tüm uygun kullanıcılar")}</dd></div><div><dt>{tx("Delivery", "Teslimat")}</dt><dd>{scheduledAt ? formatDate(scheduledAt) : tx("Send after approval", "Onaydan sonra gönder")}</dd></div></dl>
            {draft.category === "MARKETING" && <div className="campaign-consent-card"><strong>{tx("Marketing consent enforced", "Pazarlama izni uygulanır")}</strong><span>{tx("Only users with active marketing permission are eligible.", "Yalnızca aktif pazarlama izni olan kullanıcılar uygundur.")}</span></div>}
          </aside>
        </div>
        {preview && <div className="campaign-preview-result"><div><small>Estimated audience</small><strong>{formatValue(preview.estimatedAudience)}</strong></div><div><small>Consent policy</small><strong>{preview.marketingConsentRequired ? "Marketing opt-in required" : "Active users"}</strong></div></div>}
        <div className="inline-actions campaign-actions">
          <div><strong>{selectedId ? tx(`Draft #${selectedId} is ready for review`, `#${selectedId} taslağı incelemeye hazır`) : tx("Save the draft to unlock audience preview and delivery", "Kitle önizlemesi ve teslimatı açmak için taslağı kaydedin")}</strong><small>{tx("No message is sent while you edit this workspace.", "Bu çalışma alanını düzenlerken hiçbir mesaj gönderilmez.")}</small></div>
          <button className="ghost-button" disabled={!selectedId || actionState === "loading"} type="button" onClick={() => void previewAudience()}>{tx("Preview audience", "Kitleyi önizle")}</button>
          <button className="primary-button" disabled={actionState === "loading"} type="submit">{tx("Save draft", "Taslağı kaydet")}</button>
          <button className="ghost-button campaign-delivery-button" disabled={!selectedId || actionState === "loading"} type="button" onClick={() => setConfirmSchedule(true)}>{scheduledAt ? tx("Schedule delivery", "Teslimatı zamanla") : tx("Send now", "Şimdi gönder")}</button>
        </div>
      </form>}

      <Panel title={tx("Campaign history", "Kampanya geçmişi")}>
        <div className="review-filter-grid campaign-filter-row">
          <label>Status<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(0); }}><option value="">All statuses</option>{["DRAFT", "SCHEDULED", "PROCESSING", "COMPLETED", "CANCELLED", "FAILED"].map((status) => <option value={status} key={status}>{status}</option>)}</select></label>
        </div>
        <DataTable
          columns={["Campaign", "Audience", "Channel", "Status", "Delivery", "Scheduled", "Actions"]}
          rows={rows.map((item) => [
            <div className="table-stack"><strong>{item.name ?? "-"}</strong><small>{item.title ?? "-"}</small></div>,
            <div className="table-stack"><strong>{formatValue(item.estimatedAudience)}</strong><small>{[item.targetPlan, item.targetRegion, item.targetLanguage].filter(Boolean).join(" / ") || "All active users"}</small></div>,
            <div className="badge-stack"><Badge value={item.category} tone={item.category === "MARKETING" ? "warn" : "neutral"} /><Badge value={item.channel} /></div>,
            <Badge value={item.status} tone={item.status === "COMPLETED" ? "good" : item.status === "FAILED" ? "danger" : item.status === "PROCESSING" ? "warn" : "neutral"} />,
            <div className="table-stack"><strong>{formatValue(item.processedCount)} processed</strong><small>{formatValue(item.pushSentCount)} push · {formatValue(item.emailSentCount)} email / {formatValue((item.pushFailedCount??0)+(item.emailFailedCount??0))} failed</small></div>,
            formatDate(item.scheduledAt ?? item.createdAt),
            <div className="inline-actions">
              {item.status === "DRAFT" && <button className="ghost-button" type="button" onClick={() => editCampaign(item)}>Edit</button>}
              {item.id && <button className="ghost-button" type="button" onClick={() => void openHistoryPreview(item)}>Preview</button>}
              {(item.status === "SCHEDULED" || item.status === "PROCESSING") && <button className="ghost-button danger-text" type="button" onClick={() => void cancelCampaign(item)}>Cancel</button>}
            </div>
          ])}
          empty="No notification campaigns returned."
        />
        <PaginationControls page={data?.page ?? page} pageSize={data?.size ?? pageSize} totalElements={data?.totalElements ?? rows.length} totalPages={data?.totalPages ?? 1} first={Boolean(data?.first)} last={Boolean(data?.last)} onPageChange={setPage} onPageSizeChange={(size) => { setPageSize(size); setPage(0); }} />
      </Panel>
      {confirmSchedule && <ConfirmDialog title={scheduledAt ? "Schedule this campaign?" : "Send this campaign now?"} message={"This will target approximately " + formatValue(preview?.estimatedAudience) + " eligible users. Backend audience and consent rules will be enforced."} confirmLabel={scheduledAt ? "Schedule campaign" : "Queue delivery"} danger busy={actionState === "loading"} onCancel={() => setConfirmSchedule(false)} onConfirm={() => void scheduleCampaign()} />}
      {guideOpen && <NotificationCampaignGuideModal onClose={() => setGuideOpen(false)} />}
      {historyPreview && <NotificationCampaignPreviewModal campaign={historyPreview.campaign} preview={historyPreview.preview} onClose={() => setHistoryPreview(null)} />}
    </div>
  );
}

export function NotificationCampaignPreviewModal({
  campaign,
  preview,
  onClose
}: {
  campaign: NotificationCampaign;
  preview: NotificationCampaignPreview;
  onClose: () => void;
}) {
  const audience = [campaign.targetPlan, campaign.targetRegion, campaign.targetLanguage].filter(Boolean);
  const dialogRef = useDialogAccessibility(onClose);
  return (
    <div className="modal-backdrop campaign-preview-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="modal-card campaign-history-modal" role="dialog" aria-modal="true" aria-labelledby="campaign-preview-title" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">Campaign preview</p>
            <h2 id="campaign-preview-title">{campaign.name ?? "Notification campaign"}</h2>
            <span>{campaign.title ?? "-"}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close campaign preview">x</button>
        </header>
        <div className="campaign-history-body">
          <div className="campaign-history-summary">
            <div><small>Estimated audience</small><strong>{formatValue(preview.estimatedAudience)}</strong></div>
            <div><small>Status</small><Badge value={campaign.status} tone={campaign.status === "COMPLETED" ? "good" : campaign.status === "FAILED" ? "danger" : "neutral"} /></div>
            <div><small>Category</small><Badge value={campaign.category} tone={campaign.category === "MARKETING" ? "warn" : "neutral"} /></div>
            <div><small>Channel</small><Badge value={campaign.channel} /></div>
          </div>
          <section className="campaign-history-section">
            <h3>Audience and consent</h3>
            <div className="campaign-history-facts">
              <div><small>Plan</small><strong>{campaign.targetPlan || "All plans"}</strong></div>
              <div><small>Region</small><strong>{campaign.targetRegion || "All regions"}</strong></div>
              <div><small>Language</small><strong>{campaign.targetLanguage || "All languages"}</strong></div>
              <div><small>Consent</small><strong>{preview.marketingConsentRequired ? "Marketing opt-in required" : "Active eligible users"}</strong></div>
            </div>
            {!audience.length && <p className="campaign-history-hint">No audience filters were set for this campaign.</p>}
          </section>
          <section className="campaign-history-section">
            <h3>User-facing content</h3>
            <div className="campaign-message-preview"><strong>{campaign.title ?? "-"}</strong><p>{campaign.message ?? "-"}</p><small>Route: {campaign.targetRoute || "No destination route"}</small></div>
          </section>
          <section className="campaign-history-section">
            <h3>Delivery result</h3>
            <div className="campaign-history-facts">
              <div><small>Processed</small><strong>{formatValue(campaign.processedCount)}</strong></div>
              <div><small>In-app</small><strong>{formatValue(campaign.inAppCount)}</strong></div>
              <div><small>Push sent</small><strong>{formatValue(campaign.pushSentCount)}</strong></div>
              <div><small>Push failed</small><strong>{formatValue(campaign.pushFailedCount)}</strong></div>
              <div><small>Suppressed</small><strong>{formatValue(campaign.suppressedCount)}</strong></div>
              <div><small>Opened</small><strong>{formatValue(campaign.openedCount)}</strong></div>
              <div><small>Clicked</small><strong>{formatValue(campaign.clickedCount)}</strong></div>
              <div><small>Dismissed</small><strong>{formatValue(campaign.dismissedCount)}</strong></div>
              <div><small>Converted</small><strong>{formatValue(campaign.convertedCount)}</strong></div>
              <div><small>Pressure cap</small><strong>{campaign.frequencyCapMax ?? 3} / {campaign.frequencyCapHours ?? 24}h</strong></div>
              <div><small>Scheduled</small><strong>{formatDate(campaign.scheduledAt)}</strong></div>
              <div><small>Completed</small><strong>{formatDate(campaign.completedAt)}</strong></div>
            </div>
            {campaign.failureMessage && <div className="form-notice warning">{campaign.failureMessage}</div>}
          </section>
          {campaign.id && <CampaignRecipientLedger campaignId={campaign.id} />}
        </div>
        <div className="modal-actions"><button className="primary-button" type="button" onClick={onClose}>Close preview</button></div>
      </section>
    </div>
  );
}

export function CampaignRecipientLedger({ campaignId }: { campaignId: number }) {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const params = new URLSearchParams({ page: String(page), size: "10" });
  if (status) params.set("status", status);
  const { data, state, reload } = useEndpoint<PageResponse<NotificationCampaignRecipient>>(
    "/api/v1/admin/notification-campaigns/" + campaignId + "/recipients?" + params.toString(),
    () => undefined
  );
  return (
    <section className="campaign-history-section campaign-recipient-ledger">
      <div className="campaign-ledger-heading">
        <div><h3>Delivery and engagement</h3><p>Privacy-safe recipient references; no full profile or message payload is exposed.</p></div>
        <label>Status<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
          <option value="">All</option><option value="DELIVERED">Delivered</option><option value="SUPPRESSED">Suppressed</option><option value="FAILED">Failed</option>
        </select></label>
      </div>
      <DataTable
        columns={["Recipient", "Delivery", "Engagement", "Processed"]}
        rows={(data?.content ?? []).map((row) => [
          row.userReference ?? "user",
          <div className="table-stack"><Badge value={row.status} tone={row.status === "FAILED" ? "danger" : row.status === "SUPPRESSED" ? "warn" : "good"} /><small>{row.suppressionReason || ((row.pushSent ?? 0) + " push sent")}</small></div>,
          <div className="campaign-engagement-badges"><span className={row.openedAt ? "is-active" : ""}>Open</span><span className={row.clickedAt ? "is-active" : ""}>Click</span><span className={row.convertedAt ? "is-active" : ""}>Convert</span><span className={row.dismissedAt ? "is-dismissed" : ""}>Dismiss</span></div>,
          formatDate(row.processedAt)
        ])}
        empty={state === "loading" ? "Loading delivery diagnostics..." : "No recipient rows match this filter."}
      />
      <PaginationControls page={data?.page ?? page} pageSize={data?.size ?? 10} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 1} first={Boolean(data?.first)} last={Boolean(data?.last)} onPageChange={setPage} onPageSizeChange={() => {}} />
      {state === "error" && <button className="ghost-button" type="button" onClick={() => void reload()}>Retry diagnostics</button>}
    </section>
  );
}

export function NotificationCampaignGuideModal({ onClose }: { onClose: () => void }) {
  const dialogRef = useDialogAccessibility(onClose);
  return (
    <div className="modal-backdrop campaign-guide-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="modal-card campaign-guide-modal" role="dialog" aria-modal="true" aria-labelledby="campaign-guide-title" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div><p className="eyebrow">Notification operations</p><h2 id="campaign-guide-title">Campaign management guide</h2><span>Prepare, verify, and deliver announcements without bypassing consent controls.</span></div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close campaign guide">x</button>
        </header>
        <div className="campaign-guide-body">
          <div className="campaign-guide-step"><strong>1. Choose the category</strong><p>Use System for service, security, or account information. Use Marketing only for promotions; recipients must have explicitly enabled marketing notifications.</p></div>
          <div className="campaign-guide-step"><strong>2. Select the channel</strong><p>In-app stores the message in the notification centre. Push contacts registered devices. Combined delivery uses both while still respecting push preferences.</p></div>
          <div className="campaign-guide-step"><strong>3. Limit the audience</strong><p>Plan, region, and language filters are optional. Empty filters target all eligible active users. Admin accounts, disabled accounts, and locked accounts are excluded.</p></div>
          <div className="campaign-guide-step"><strong>4. Save and preview</strong><p>Save the campaign as a draft, then preview the audience before delivery. The preview is an estimate; backend eligibility rules are evaluated again during processing.</p></div>
          <div className="campaign-guide-step"><strong>5. Send or schedule</strong><p>Leave delivery time empty to queue immediately, or choose a future time. Delivery runs in batches and records each recipient to prevent duplicate sends.</p></div>
          <div className="campaign-guide-note"><strong>Before confirming</strong><span>Check the user-facing title, message, destination route, consent category, estimated audience, and delivery time. Cancellation stops remaining batches but does not retract messages already delivered.</span></div>
        </div>
        <div className="modal-actions"><button className="primary-button" type="button" onClick={onClose}>Understood</button></div>
      </section>
    </div>
  );
}
