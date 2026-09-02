import { useCallback, useEffect, useState } from "react";
import { formatRequestError, request } from "../../api";
import { DataTable, LoadState, MetricCard, Panel, SectionToolbar } from "../../AdminPrimitives";

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

export function SubscriptionNotificationOperationsView({ onError }: { onError: (message: string | null) => void }) {
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [ledger, setLedger] = useState<LedgerPage | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [reason, setReason] = useState("");
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const [previewType, setPreviewType] = useState("subscription_renewed");
  const [previewLanguage, setPreviewLanguage] = useState("EN");
  const [preview, setPreview] = useState<Preview | null>(null);

  const load = useCallback(async () => {
    setState("loading");
    try {
      const [nextPolicy, nextMetrics, nextLedger] = await Promise.all([
        request<Policy>("/api/v1/admin/subscription-notifications/policy"),
        request<Metrics>("/api/v1/admin/subscription-notifications/metrics"),
        request<LedgerPage>(`/api/v1/admin/subscription-notifications/ledger?page=${page}&size=25`)
      ]);
      setPolicy(nextPolicy); setMetrics(nextMetrics); setLedger(nextLedger); setState("ready"); onError(null);
    } catch (error) { const message = formatRequestError(error); setState("error"); onError(message); }
  }, [onError, page]);
  useEffect(() => { void load(); }, [load]);

  async function requestPolicy(enabled: boolean) {
    if (!policy || !reason.trim()) return;
    setActionState("loading");
    try {
      await request("/api/v1/admin/subscription-notifications/policy/publish-request", { method: "POST", body: { version: policy.version, requestedDeliveryEnabled: enabled, reason: reason.trim() } });
      setNotice("Policy publication is pending maker-checker approval. Effective delivery has not changed."); setReason(""); setActionState("ready");
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
  return <div className="stack">
    <SectionToolbar title="Subscription notification operations" description="Govern transactional account copy and delivery without bypassing deployment gates or maker-checker approval." state={state} onReload={() => void load()} />
    <div className="metric-grid">
      <MetricCard label="Effective state" value={policy?.effectiveDeliveryEnabled ? "ELIGIBLE" : "BLOCKED"} hint={policy?.effectiveReason ?? "Loading"} />
      <MetricCard label="Release stage" value={policy?.releaseStage ?? "OFF"} hint={policy?.releaseStage === "LIVE" ? `${policy.livePercentage}% stable cohort` : `${policy?.testAccountCount ?? 0} test · ${policy?.pilotAccountCount ?? 0} pilot`} />
      <MetricCard label="Occurrences" value={String(metrics?.totalOccurrences ?? 0)} hint="Transactional account events" />
      <MetricCard label="Delivered" value={String(delivered)} hint="Provider-confirmed attempts" />
      <MetricCard label="Failed / unknown" value={String(failed)} hint="Needs operational review" />
      <MetricCard label="Opened" value={String(metrics?.opened ?? 0)} hint="Unique notification opens" />
      <MetricCard label="Clicked" value={String(metrics?.clicked ?? 0)} hint="Unique route clicks" />
    </div>
    <Panel title="Layered delivery gates" description="Admin intent cannot override deployment stage, cohort, producer migration, or provider kill switches.">
      {policy && <div className="form-notice"><strong>{policy.effectiveDeliveryEnabled ? "Delivery eligible" : "Delivery blocked"}</strong><br />Deployment: {policy.deploymentDeliveryEnabled ? "ON" : "OFF"} · Stage: {policy.releaseStage} · Push provider: {policy.pushProviderEnabled ? "ON" : "OFF"} · Approved policy: {policy.requestedDeliveryEnabled ? "ON" : "OFF"} · Emergency stop: {policy.emergencyStopped ? "ON" : "OFF"}<br />Cohort: {policy.testAccountCount} test · {policy.pilotAccountCount} pilot · LIVE {policy.livePercentage}%<br />Producers: water {policy.waterProducerMigrated ? "COMMON" : "LEGACY"} · step {policy.stepProducerMigrated ? "COMMON" : "LEGACY"} · fasting {policy.basicFastingProducerMigrated ? "COMMON" : "LEGACY"}<br />{policy.stopReason}</div>}
      {notice && <div className={`form-notice ${actionState === "error" ? "warning" : ""}`}>{notice}</div>}
      <label className="wide-field">Required audit / approval reason<textarea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      <div className="inline-actions">
        <button className="danger-button" type="button" disabled={!reason.trim() || actionState === "loading" || Boolean(policy?.emergencyStopped)} onClick={() => void emergencyStop()}>Emergency stop now</button>
        <button className="ghost-button" type="button" disabled={!reason.trim() || actionState === "loading"} onClick={() => void requestPolicy(false)}>Request OFF publication</button>
        <button className="primary-button" type="button" disabled={!reason.trim() || actionState === "loading"} onClick={() => void requestPolicy(true)}>Request ON publication</button>
      </div>
    </Panel>
    <Panel title="Localized copy preview" description="Typed sample parameters only; no user or provider data is loaded.">
      <div className="inline-actions"><select value={previewType} onChange={(e) => setPreviewType(e.target.value)}>{["subscription_started","subscription_renewed","subscription_cancelled","subscription_resumed","subscription_billing_issue","subscription_expired","subscription_plan_changed","subscription_paused","subscription_refunded","ai_addon_purchased"].map(value => <option key={value}>{value}</option>)}</select><select value={previewLanguage} onChange={(e) => setPreviewLanguage(e.target.value)}><option value="EN">English</option><option value="TR">Türkçe</option></select><button className="primary-button" type="button" onClick={() => void renderPreview()}>Render preview</button></div>
      {preview && <div className="form-notice"><strong>{preview.title}</strong><p>{preview.message}</p><small>{preview.channel} · {preview.severity} · {preview.targetRoute}</small></div>}
    </Panel>
    <Panel title="Reason and delivery ledger" description="One row per transactional occurrence; raw provider payloads and customer identifiers are intentionally excluded.">
      <DataTable columns={["Event", "User", "Occurrence", "Reason", "Outbox", "Dispatch", "Created"]} rows={(ledger?.content ?? []).map(item => [item.eventType, `#${item.userId}`, item.occurrenceStatus, item.reasonCode ?? "-", item.outboxStatus ?? "IN_APP_ONLY", `${item.dispatchCount}${item.lastErrorCode ? ` · ${item.lastErrorCode}` : ""}`, new Date(item.createdAt).toLocaleString()])} empty="No transactional notification occurrences recorded." />
      <div className="inline-actions"><button className="ghost-button" disabled={ledger?.first ?? true} onClick={() => setPage(value => Math.max(0, value - 1))}>Previous</button><span>Page {(ledger?.page ?? 0) + 1} / {Math.max(1, ledger?.totalPages ?? 1)}</span><button className="ghost-button" disabled={ledger?.last ?? true} onClick={() => setPage(value => value + 1)}>Next</button></div>
    </Panel>
  </div>;
}
