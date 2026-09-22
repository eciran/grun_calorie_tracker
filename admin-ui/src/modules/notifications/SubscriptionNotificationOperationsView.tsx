import { useCallback, useEffect, useState } from "react";
import { formatRequestError, request } from "../../api";
import { DataTable, LoadState, MetricCard, Panel, SectionToolbar } from "../../AdminPrimitives";
import { CommunicationBars, CommunicationHero } from "../../CommunicationsPrimitives";
import { useAdminLocale } from "../../admin/locale";
import { AdminAccessProfile, AdminApprovalRequest } from "../../types";
import { ApprovalSubmissionNotice } from "../../admin/shared";

type Policy = {
  version: number; requestedDeliveryEnabled: boolean; emergencyStopped: boolean; stopReason?: string;
  deploymentDeliveryEnabled: boolean; releaseStage: "OFF" | "DRY_RUN" | "TEST_ACCOUNTS" | "PILOT" | "LIVE";
  testAccountCount: number; pilotAccountCount: number; livePercentage: number;
  waterProducerMigrated: boolean; stepProducerMigrated: boolean; basicFastingProducerMigrated: boolean;
  pushProviderEnabled: boolean; effectiveDeliveryEnabled: boolean;
  effectiveReason: string; updatedBy: string; updatedAt: string;
};
type Metrics = { totalOccurrences: number; occurrenceStatuses: Record<string, number>; outboxStatuses: Record<string, number>; attemptStatuses: Record<string, number>; opened: number; clicked: number; dismissed: number };
type LedgerItem = { occurrenceId: number; userId: number; eventType: string; definitionKey: string; source: string; occurrenceStatus: string; reasonCode?: string; notificationId?: number; outboxId?: number; outboxStatus?: string; dispatchCount: number; lastErrorCode?: string; createdAt: string };
type LedgerPage = { content: LedgerItem[]; page: number; totalPages: number; totalElements: number; first: boolean; last: boolean };
type Preview = { title: string; message: string; severity: string; targetRoute: string; channel: string; definitionEnabled: boolean };

export function SubscriptionNotificationOperationsView({ onError, accessProfile }: { onError: (message: string | null) => void; accessProfile: AdminAccessProfile | null }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [ledger, setLedger] = useState<LedgerPage | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [reason, setReason] = useState("");
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const [approvalNotice, setApprovalNotice] = useState<{ request: AdminApprovalRequest; message: string } | null>(null);
  const [previewType, setPreviewType] = useState("subscription_renewed");
  const [previewLanguage, setPreviewLanguage] = useState("EN");
  const [preview, setPreview] = useState<Preview | null>(null);
  useEffect(() => { if (notice) setApprovalNotice(null); }, [notice]);

  const load = useCallback(async () => {
    setState("loading");
    try {
      const [nextPolicy, nextMetrics, nextLedger] = await Promise.all([
        request<Policy>("/api/v1/admin/subscription-notifications/policy"),
        request<Metrics>("/api/v1/admin/subscription-notifications/metrics"),
        request<LedgerPage>(`/api/v1/admin/subscription-notifications/ledger?page=${page}&size=10`)
      ]);
      setPolicy(nextPolicy); setMetrics(nextMetrics); setLedger(nextLedger); setState("ready"); onError(null);
    } catch (error) { const message = formatRequestError(error); setState("error"); onError(message); }
  }, [onError, page]);
  useEffect(() => { void load(); }, [load]);

  async function requestPolicy(enabled: boolean) {
    if (!policy || !reason.trim()) return;
    setActionState("loading");
    try {
      const approval = await request<AdminApprovalRequest>("/api/v1/admin/subscription-notifications/policy/publish-request", { method: "POST", body: { version: policy.version, requestedDeliveryEnabled: enabled, reason: reason.trim() } });
      setNotice(null); setApprovalNotice({ request: approval, message: "Policy publication is pending maker-checker approval. Effective delivery has not changed." }); setReason(""); setActionState("ready");
    } catch (error) { const message = formatRequestError(error); setNotice(message); setActionState("error"); onError(message); }
  }
  async function emergencyStop() {
    if (!reason.trim()) return;
    setActionState("loading");
    try {
      const next = await request<Policy>("/api/v1/admin/subscription-notifications/emergency-stop", { method: "POST", body: { reason: reason.trim() } });
      setPolicy(next); setNotice("Emergency stop applied immediately and audited."); setReason(""); setActionState("ready");
    } catch (error) { const message = formatRequestError(error); setNotice(message); setActionState("error"); onError(message); }
  }
  async function renderPreview() {
    const dateKey = previewType === "subscription_cancelled" ? "accessUntilDate" : previewType === "subscription_expired" ? "expiredAt" : previewType.includes("changed") || previewType.includes("paused") || previewType.includes("refunded") ? "effectiveDate" : "periodEndDate";
    const parameters = previewType === "ai_addon_purchased" ? { creditAmount: "50", validUntilDate: "30 September 2026" } : { planName: "Pro", [dateKey]: "30 September 2026" };
    try { setPreview(await request<Preview>("/api/v1/admin/subscription-notifications/preview", { method: "POST", body: { definitionKey: previewType, language: previewLanguage, parameters } })); }
    catch (error) { onError(formatRequestError(error)); }
  }

  const delivered = metrics?.attemptStatuses?.DELIVERED ?? 0;
  const failed = (metrics?.attemptStatuses?.FAILED_FINAL ?? 0) + (metrics?.attemptStatuses?.UNKNOWN ?? 0);
  const occurrenceFlow = Object.entries(metrics?.occurrenceStatuses ?? {}).map(([label, value]) => ({ label, value }));
  const outboxFlow = Object.entries(metrics?.outboxStatuses ?? {}).map(([label, value]) => ({ label, value, tone: label.includes("FAIL") ? "danger" as const : label.includes("DELIVER") || label.includes("SENT") ? "accent" as const : undefined }));
  return <div className="stack communications-ops-view subscription-notification-page">
    <SectionToolbar title={tx("Subscription notification operations", "Abonelik bildirimi işlemleri")} description={tx("Govern transactional account copy and delivery without bypassing deployment gates or maker-checker approval.", "Yayın kapılarını veya çift kontrol onayını aşmadan işlemsel hesap metinlerini ve teslimatını yönetin.")} state={state} onReload={() => void load()} />
    <CommunicationHero eyebrow={tx("Account lifecycle", "Hesap yaşam döngüsü")} title={tx("Control critical subscription messages from policy to delivery.", "Kritik abonelik mesajlarını politikadan teslimata kadar yönetin.")} description={tx("Delivery gates, localized copy and the privacy-safe ledger stay connected in one operational flow.", "Teslimat kapıları, yerelleştirilmiş metin ve gizlilik güvenli kayıt defteri tek operasyon akışında bağlı kalır.")} status={<span className={`communication-status-pill ${policy?.effectiveDeliveryEnabled ? "good" : "blocked"}`}>{policy?.effectiveDeliveryEnabled ? tx("ELIGIBLE", "UYGUN") : tx("BLOCKED", "ENGELLİ")}</span>} />
    <div className="metric-grid">
      <MetricCard label={tx("Effective state", "Etkin durum")} value={policy?.effectiveDeliveryEnabled ? tx("ELIGIBLE", "UYGUN") : tx("BLOCKED", "ENGELLİ")} hint={policy?.effectiveReason ?? tx("Loading", "Yükleniyor")} />
      <MetricCard label={tx("Release stage", "Yayın aşaması")} value={policy?.releaseStage ?? "OFF"} hint={policy?.releaseStage === "LIVE" ? `${policy.livePercentage}% ${tx("stable cohort", "kararlı grup")}` : `${policy?.testAccountCount ?? 0} test · ${policy?.pilotAccountCount ?? 0} pilot`} />
      <MetricCard label={tx("Occurrences", "Olaylar")} value={String(metrics?.totalOccurrences ?? 0)} hint={tx("Transactional account events", "İşlemsel hesap olayları")} />
    </div>
    <section className="subscription-notification-insights">
      <article className="communication-insight-strip"><div><p className="eyebrow">{tx("Delivery outcomes", "Teslimat sonuçları")}</p><h3>{tx("Provider and engagement signals", "Sağlayıcı ve etkileşim sinyalleri")}</h3><p>{tx("Delivery, failure and engagement are compared without exposing customer data.", "Teslimat, hata ve etkileşim müşteri verisi gösterilmeden karşılaştırılır.")}</p></div><CommunicationBars items={[{ label: tx("Delivered", "Teslim edildi"), value: delivered, tone: "accent" }, { label: tx("Failed / unknown", "Başarısız / bilinmiyor"), value: failed, tone: "danger" }, { label: tx("Opened", "Açıldı"), value: metrics?.opened ?? 0 }, { label: tx("Clicked", "Tıklandı"), value: metrics?.clicked ?? 0 }]} /></article>
      <article className="communication-insight-strip"><div><p className="eyebrow">{tx("Occurrence state", "Olay durumu")}</p><h3>{tx("Transactional event processing", "İşlemsel olay işleme")}</h3><p>{tx("Each event appears once in the lifecycle before delivery fan-out.", "Her olay teslimat dağıtımından önce yaşam döngüsünde bir kez görünür.")}</p></div><CommunicationBars items={occurrenceFlow} empty={tx("No occurrence data recorded.", "Olay verisi kaydedilmemiş.")} /></article>
      <article className="communication-insight-strip subscription-outbox-insight"><div><p className="eyebrow">Outbox</p><h3>{tx("Queue health", "Kuyruk sağlığı")}</h3><p>{tx("Pending and terminal delivery states are kept separate.", "Bekleyen ve tamamlanmış teslimat durumları ayrı tutulur.")}</p></div><CommunicationBars items={outboxFlow} empty={tx("No outbox activity recorded.", "Outbox hareketi kaydedilmemiş.")} /></article>
    </section>
    <Panel title={tx("Layered delivery gates", "Katmanlı teslimat kapıları")} description={tx("Admin intent cannot override deployment stage, cohort, producer migration, or provider kill switches.", "Yönetici tercihi yayın aşamasını, grubu, üretici geçişini veya sağlayıcı kapatma anahtarlarını geçersiz kılamaz.")}>
      {policy && <div className="form-notice"><strong>{policy.effectiveDeliveryEnabled ? tx("Delivery eligible", "Teslimata uygun") : tx("Delivery blocked", "Teslimat engelli")}</strong><br />{tx("Deployment", "Yayın")}: {policy.deploymentDeliveryEnabled ? "ON" : "OFF"} · {tx("Stage", "Aşama")}: {policy.releaseStage} · {tx("Push provider", "Push sağlayıcısı")}: {policy.pushProviderEnabled ? "ON" : "OFF"} · {tx("Approved policy", "Onaylı politika")}: {policy.requestedDeliveryEnabled ? "ON" : "OFF"} · {tx("Emergency stop", "Acil durdurma")}: {policy.emergencyStopped ? "ON" : "OFF"}<br />{tx("Cohort", "Grup")}: {policy.testAccountCount} test · {policy.pilotAccountCount} pilot · LIVE {policy.livePercentage}%<br />{tx("Producers", "Üreticiler")}: water {policy.waterProducerMigrated ? "COMMON" : "LEGACY"} · step {policy.stepProducerMigrated ? "COMMON" : "LEGACY"} · fasting {policy.basicFastingProducerMigrated ? "COMMON" : "LEGACY"}<br />{policy.stopReason}</div>}
      {approvalNotice && <ApprovalSubmissionNotice {...approvalNotice} isOwner={accessProfile?.role === "OWNER"} />}
      {notice && <div className={`form-notice ${actionState === "error" ? "warning" : ""}`}>{notice}</div>}
      <label className="wide-field">{tx("Required audit / approval reason", "Zorunlu denetim / onay nedeni")}<textarea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      <div className="inline-actions">
        <button className="danger-button" type="button" disabled={!reason.trim() || actionState === "loading" || Boolean(policy?.emergencyStopped)} onClick={() => void emergencyStop()}>{tx("Emergency stop now", "Şimdi acil durdur")}</button>
        <button className="ghost-button" type="button" disabled={!reason.trim() || actionState === "loading"} onClick={() => void requestPolicy(false)}>{tx("Request OFF publication", "KAPALI yayını talep et")}</button>
        <button className="primary-button" type="button" disabled={!reason.trim() || actionState === "loading"} onClick={() => void requestPolicy(true)}>{tx("Request ON publication", "AÇIK yayını talep et")}</button>
      </div>
    </Panel>
    <Panel title={tx("Localized copy preview", "Yerelleştirilmiş metin önizlemesi")} description={tx("Typed sample parameters only; no user or provider data is loaded.", "Yalnızca türü belirlenmiş örnek parametreler kullanılır; kullanıcı veya sağlayıcı verisi yüklenmez.")}>
      <div className="inline-actions"><select aria-label={tx("Notification type", "Bildirim türü")} value={previewType} onChange={(e) => setPreviewType(e.target.value)}>{["subscription_started","subscription_renewed","subscription_cancelled","subscription_resumed","subscription_billing_issue","subscription_expired","subscription_plan_changed","subscription_paused","subscription_refunded","ai_addon_purchased"].map(value => <option key={value}>{value}</option>)}</select><select aria-label={tx("Preview language", "Önizleme dili")} value={previewLanguage} onChange={(e) => setPreviewLanguage(e.target.value)}><option value="EN">English</option><option value="TR">Türkçe</option></select><button className="primary-button" type="button" onClick={() => void renderPreview()}>{tx("Render preview", "Önizlemeyi oluştur")}</button></div>
      {preview && <div className="form-notice"><strong>{preview.title}</strong><p>{preview.message}</p><small>{preview.channel} · {preview.severity} · {preview.targetRoute}</small></div>}
    </Panel>
    <Panel title={tx("Reason and delivery ledger", "Neden ve teslimat kayıtları")} description={tx("One row per transactional occurrence; raw provider payloads and customer identifiers are intentionally excluded.", "Her işlemsel olay için tek satır gösterilir; ham sağlayıcı verileri ve müşteri kimlikleri özellikle hariç tutulur.")}>
      <DataTable columns={[tx("Event", "Olay"), tx("User", "Kullanıcı"), tx("Occurrence", "Gerçekleşme"), tx("Reason", "Neden"), "Outbox", tx("Dispatch", "Gönderim"), tx("Created", "Oluşturulma")]} rows={(ledger?.content ?? []).map(item => [item.eventType, `#${item.userId}`, item.occurrenceStatus, item.reasonCode ?? "-", item.outboxStatus ?? "IN_APP_ONLY", `${item.dispatchCount}${item.lastErrorCode ? ` · ${item.lastErrorCode}` : ""}`, new Date(item.createdAt).toLocaleString()])} empty={tx("No transactional notification occurrences recorded.", "İşlemsel bildirim olayı kaydedilmemiş.")} />
      <div className="inline-actions"><button className="ghost-button" disabled={ledger?.first ?? true} onClick={() => setPage(value => Math.max(0, value - 1))}>{tx("Previous", "Önceki")}</button><span>{tx("Page", "Sayfa")} {(ledger?.page ?? 0) + 1} / {Math.max(1, ledger?.totalPages ?? 1)}</span><button className="ghost-button" disabled={ledger?.last ?? true} onClick={() => setPage(value => value + 1)}>{tx("Next", "Sonraki")}</button></div>
    </Panel>
  </div>;
}
