import { FormEvent, useCallback, useEffect, useState } from "react";
import { formatRequestError, request, requestBlob } from "./api";
import { CollapsiblePanel, DataTable, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import type { AdminTestFeedback, AdminTestFeedbackAnalytics, AdminTestFeedbackPage, TestFeedbackStatus } from "./types";
import "./test-feedback.css";
import { useAdminLocale } from "./admin/locale";

const STATUSES = ["NEW", "REVIEWING", "FIX_PLANNED", "FIXED", "RETEST_NEEDED", "CLOSED"] as const;
const TYPES = ["WORKS_WELL", "PROBLEM", "IMPROVEMENT"] as const;
const PLATFORMS = ["ANDROID", "IOS"] as const;
const formatBytes = (value?: number | null) => value == null ? "-" : value < 1024 * 1024
  ? `${Math.round(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(2)} MB`;

export function TestFeedbackView({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, trText: string) => tr ? trText : en;
  const localDate = (value: string) => new Date(value).toLocaleString(tr ? "tr-TR" : "en-GB");
  const [data, setData] = useState<AdminTestFeedbackPage>({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true });
  const [analytics, setAnalytics] = useState<AdminTestFeedbackAnalytics | null>(null);
  const [page, setPage] = useState(0); const [size, setSize] = useState(10);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [status, setStatus] = useState(""); const [type, setType] = useState(""); const [platform, setPlatform] = useState("");
  const [routeDraft, setRouteDraft] = useState(""); const [route, setRoute] = useState("");
  const [selected, setSelected] = useState<AdminTestFeedback | null>(null);
  const [screenshotUrl, setScreenshotUrl] = useState<string>();
  const [screenshotState, setScreenshotState] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [reviewStatus, setReviewStatus] = useState<TestFeedbackStatus>("NEW"); const [note, setNote] = useState("");
  const [state, setState] = useState<"idle" | "loading" | "ready" | "error">("idle"); const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setState("loading"); onError(null);
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status); if (type) params.set("type", type);
    if (platform) params.set("platform", platform); if (route) params.set("route", route);
    try {
      const [items, summary] = await Promise.all([
        request<AdminTestFeedbackPage>(`/api/v1/admin/test-feedback?${params}`),
        request<AdminTestFeedbackAnalytics>("/api/v1/admin/test-feedback/analytics")
      ]);
      setData(items); setAnalytics(summary); setState("ready");
    } catch (error) { setState("error"); onError(formatRequestError(error)); }
  }, [onError, page, platform, route, size, status, type]);

  useEffect(() => { void load(); }, [load]);

  async function inspect(id: number) {
    try {
      const item = await request<AdminTestFeedback>(`/api/v1/admin/test-feedback/${id}`);
      setSelected(item); setReviewStatus(item.status); setNote(item.adminNote ?? ""); setScreenshotUrl(undefined); setScreenshotState("idle");
      if (item.screenshotAvailable) await loadScreenshot(id);
    } catch (error) { onError(formatRequestError(error)); }
  }

  async function loadScreenshot(id: number) {
    setScreenshotState("loading");
    try {
      const screenshot = await request<{ url: string }>(`/api/v1/admin/test-feedback/${id}/screenshot`);
      setScreenshotUrl(screenshot.url); setScreenshotState("ready");
    } catch (error) {
      setScreenshotUrl(undefined); setScreenshotState("error"); onError(formatRequestError(error));
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault(); if (!selected) return; setSaving(true); onError(null);
    try {
      await request(`/api/v1/admin/test-feedback/${selected.id}`, { method: "PATCH", body: { status: reviewStatus, internalNote: note.trim() || null } });
      setSelected(null); await load();
    } catch (error) { onError(formatRequestError(error)); } finally { setSaving(false); }
  }

  async function exportCsv() {
    const params = new URLSearchParams();
    if (status) params.set("status", status); if (type) params.set("type", type);
    if (platform) params.set("platform", platform); if (route) params.set("route", route);
    try {
      const blob = await requestBlob(`/api/v1/admin/test-feedback/export?${params}`);
      const url = URL.createObjectURL(blob); const anchor = document.createElement("a");
      anchor.href = url; anchor.download = `test-feedback-${new Date().toISOString().slice(0, 10)}.csv`; anchor.click(); URL.revokeObjectURL(url);
    } catch (error) { onError(formatRequestError(error)); }
  }

  const hasDistributionData = Object.keys(analytics?.byRoute ?? {}).length > 0
    || Object.keys(analytics?.byBuild ?? {}).length > 0;

  return <div className="view-stack test-feedback-view">
    <SectionToolbar title={tx("Test feedback", "Test geri bildirimleri")} description={tx("Android and iOS preview build findings, triage and release evidence.", "Android ve iOS önizleme sürümü bulguları, önceliklendirme ve yayın kanıtları.")} state={state} onReload={load}>
      <button className="ghost-button" type="button" onClick={() => void exportCsv()}>{tx("Export CSV", "CSV dışa aktar")}</button>
    </SectionToolbar>
    <section className="test-feedback-overview" aria-labelledby="test-feedback-overview-title">
      <header className="test-feedback-overview-heading">
        <div><span>{tx("TRIAGE OVERVIEW", "İNCELEME ÖZETİ")}</span><h2 id="test-feedback-overview-title">{tx("Release feedback pulse", "Yayın geri bildirim görünümü")}</h2></div>
        <p>{tx("A focused view of tester activity, open work and request health.", "Test kullanıcı etkinliği, açık işler ve istek sağlığı tek görünümde.")}</p>
      </header>
      <div className="test-feedback-metrics">
        <MetricCard label={tx("Total feedback", "Toplam geri bildirim")} value={String(analytics?.total ?? 0)} hint={tx("All internal test builds", "Tüm dahili test sürümleri")} />
        <MetricCard label={tx("Last 7 days", "Son 7 gün")} value={String(analytics?.lastSevenDays ?? 0)} hint={tx("Recent tester activity", "Son test kullanıcı etkinliği")} />
        <MetricCard label={tx("Needs action", "İşlem gerekli")} value={String((analytics?.byStatus?.NEW ?? 0) + (analytics?.byStatus?.REVIEWING ?? 0))} hint={`${analytics?.byType?.PROBLEM ?? 0} ${tx("reported problem(s)", "bildirilen sorun")}`} />
        <MetricCard label={tx("Request signals", "İstek sinyalleri")} value={String((analytics?.httpFailures ?? 0) + (analytics?.slowRequests ?? 0))} hint={`${analytics?.httpFailures ?? 0} HTTP · ${analytics?.slowRequests ?? 0} ${tx("slow", "yavaş")}`} />
      </div>
    </section>
    {hasDistributionData ? <div className="test-feedback-breakdowns">
      <Panel title={tx("Top affected routes", "En çok etkilenen yollar")} description={tx("Pages receiving the most internal test feedback.", "En fazla dahili test geri bildirimi alan sayfalar.")}><FeedbackRanking items={analytics?.byRoute ?? {}} empty={tx("No route data yet.", "Henüz sayfa yolu verisi yok.")} /></Panel>
      <Panel title={tx("Build coverage", "Sürüm kapsamı")} description={tx("Feedback distribution across preview builds.", "Geri bildirimlerin önizleme sürümlerine dağılımı.")}><FeedbackRanking items={analytics?.byBuild ?? {}} empty={tx("No build data yet.", "Henüz sürüm verisi yok.")} /></Panel>
    </div> : <section className="test-feedback-analytics-empty">
      <div className="test-feedback-empty-mark" aria-hidden="true">↗</div>
      <div><span>{tx("DISTRIBUTION ANALYTICS", "DAĞILIM ANALİZİ")}</span><h3>{tx("Charts will form with the first reports", "İlk raporlarla grafikler oluşacak")}</h3><p>{tx("Route and preview build distributions stay compact until feedback arrives.", "Sayfa yolu ve önizleme sürümü dağılımları geri bildirim gelene kadar kompakt kalır.")}</p></div>
      <div className="test-feedback-empty-signals"><span>{tx("Routes", "Yollar")}<strong>0</strong></span><span>{tx("Builds", "Sürümler")}<strong>0</strong></span></div>
    </section>}
    <CollapsiblePanel className="test-feedback-filter-panel" title={tx("Feedback filters", "Geri bildirim filtreleri")} description={feedbackFilterSummary({ status, type, platform, route }, tx)} open={filtersOpen} onToggle={() => setFiltersOpen(value => !value)}>
      <form className="test-feedback-filters" onSubmit={(event) => { event.preventDefault(); setPage(0); setRoute(routeDraft.trim()); }}>
        <label>{tx("Status", "Durum")}<select value={status} onChange={(event) => { setPage(0); setStatus(event.target.value); }}><option value="">{tx("All statuses", "Tüm durumlar")}</option>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label>{tx("Result", "Sonuç")}<select value={type} onChange={(event) => { setPage(0); setType(event.target.value); }}><option value="">{tx("All results", "Tüm sonuçlar")}</option>{TYPES.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label>{tx("Platform", "Platform")}<select value={platform} onChange={(event) => { setPage(0); setPlatform(event.target.value); }}><option value="">{tx("All platforms", "Tüm platformlar")}</option>{PLATFORMS.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label>{tx("Route", "Sayfa yolu")}<input value={routeDraft} onChange={(event) => setRouteDraft(event.target.value)} placeholder="/progress" /></label>
        <button className="primary-button" type="submit">{tx("Apply filters", "Filtreleri uygula")}</button>
      </form>
    </CollapsiblePanel>
    <Panel className="test-feedback-inbox" title={tx("Feedback inbox", "Geri bildirim gelen kutusu")} description={`${data.totalElements ?? 0} ${tx("matching report(s)", "eşleşen rapor")}`}>
      <DataTable columns={[tx("Result", "Sonuç"), tx("Route", "Sayfa yolu"), tx("Tester", "Test kullanıcısı"), tx("Build", "Sürüm"), "HTTP", tx("Status", "Durum"), tx("Submitted", "Gönderim"), tx("Action", "İşlem")]} empty={tx("No feedback matches these filters.", "Bu filtrelerle eşleşen geri bildirim yok.")} rows={(data.content ?? []).map((item) => [
        <span className={`status-pill feedback-${item.feedbackType.toLowerCase()}`}>{item.feedbackType.replaceAll("_", " ")}</span>,
        <div className="table-stack"><strong>{item.route}</strong><small>{tx("from", "önceki")} {item.previousRoute || "-"}</small></div>,
        <div className="table-stack"><span>{item.userEmail}</span><small>{item.platform} · {item.deviceModel || tx("Unknown device", "Bilinmeyen cihaz")}</small></div>,
        <div className="table-stack"><span>{item.appVersion || "-"} ({item.buildNumber || "-"})</span><small>{item.easBuildId || item.commitSha || tx("No build reference", "Sürüm referansı yok")}</small></div>,
        item.lastHttpStatus ? `${item.lastHttpStatus} · ${item.lastHttpDurationMs ?? "-"} ms` : "-",
        <span className="status-pill">{item.status.replaceAll("_", " ")}</span>, localDate(item.createdAt),
        <button className="ghost-button" type="button" onClick={() => void inspect(item.id)}>{tx("Inspect", "İncele")}</button>
      ])} />
      <PaginationControls page={data.page ?? page} pageSize={data.size ?? size} totalElements={data.totalElements ?? 0} totalPages={data.totalPages ?? 0} first={data.first ?? page === 0} last={data.last ?? true} onPageChange={setPage} onPageSizeChange={(value) => { setPage(0); setSize(value); }} />
    </Panel>
    {selected && <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
      <form className="modal-card test-feedback-modal" role="dialog" aria-modal="true" aria-label="Test feedback detail" onSubmit={save} onClick={(event) => event.stopPropagation()}>
        <header className="modal-header test-feedback-modal-header"><div><span>{tx("TEST FEEDBACK", "TEST GERİ BİLDİRİMİ")} #{selected.id}</span><div className="test-feedback-modal-title"><h2>{selected.route || tx("Unknown route", "Bilinmeyen yol")}</h2><span className={`status-pill feedback-${selected.feedbackType.toLowerCase()}`}>{selected.feedbackType.replaceAll("_", " ")}</span></div><p>{selected.platform} · {tx("Submitted", "Gönderim")} {localDate(selected.createdAt)}</p></div><button className="modal-icon-close" type="button" aria-label={tx("Close feedback detail", "Geri bildirim detayını kapat")} onClick={() => setSelected(null)}>x</button></header>
        <div className="modal-body test-feedback-detail-grid">
          <section className="test-feedback-report"><div className="test-feedback-section-heading"><span>{tx("TESTER INPUT", "TEST KULLANICISI GİRDİSİ")}</span><h3>{tx("Report details", "Rapor detayları")}</h3></div><p className="feedback-description">{selected.description || tx("No written explanation was provided.", "Yazılı açıklama girilmedi.")}</p></section>
          <section className="test-feedback-submission"><div className="test-feedback-section-heading"><span>SUBMISSION</span><h3>Who, where and when</h3></div><div className="test-feedback-meta-grid">
            <div><span>Sender</span><strong>{selected.userEmail || "Unknown tester"}</strong></div>
            <div><span>Route</span><strong>{selected.route || "-"}</strong></div>
            <div><span>Previous route</span><strong>{selected.previousRoute || "-"}</strong></div>
            <div><span>Created</span><strong>{new Date(selected.createdAt).toLocaleString()}</strong></div>
          </div></section>
          <section className="test-feedback-technical"><div className="test-feedback-section-heading"><span>ENVIRONMENT</span><h3>Technical context</h3></div><dl><dt>Build</dt><dd>{selected.appVersion || "-"} ({selected.buildNumber || "-"})</dd><dt>Device</dt><dd>{selected.deviceModel || "-"} · {selected.osVersion || "-"}</dd><dt>Locale</dt><dd>{selected.languageTag || "-"} · {selected.marketRegion || "-"}</dd><dt>Network</dt><dd>{selected.networkState || "-"}</dd><dt>Last HTTP</dt><dd>{selected.lastHttpStatus || "-"} · {selected.lastHttpDurationMs ?? "-"} ms</dd><dt>Correlation ID</dt><dd>{selected.lastCorrelationId || "-"}</dd></dl></section>
          <section className="test-feedback-evidence"><div className="test-feedback-section-heading"><span>EVIDENCE</span><h3>Screenshot</h3></div>
            <div className="screenshot-state-row"><span>Screenshot</span><strong className={`screenshot-state screenshot-state-${(selected.screenshotState ?? "NOT_PROVIDED").toLowerCase()}`}>{(selected.screenshotState ?? "NOT_PROVIDED").replaceAll("_", " ")}</strong></div>
            {selected.screenshotAvailable && <div className="test-feedback-screenshot-panel">
              {screenshotState === "loading" && <p className="test-feedback-screenshot-status">Loading screenshot...</p>}
              {screenshotUrl && <a href={screenshotUrl} target="_blank" rel="noreferrer" title="Open screenshot in a new tab"><img className="test-feedback-screenshot" src={screenshotUrl} alt="Tester supplied screenshot" onError={() => setScreenshotState("error")} /></a>}
              {screenshotState === "error" && <div className="test-feedback-screenshot-status"><span>The screenshot could not be displayed.</span><button className="ghost-button" type="button" onClick={() => void loadScreenshot(selected.id)}>Try again</button></div>}
              {screenshotState === "ready" && screenshotUrl && <a className="test-feedback-screenshot-open" href={screenshotUrl} target="_blank" rel="noreferrer">Open full size</a>}
            </div>}
            {!selected.screenshotAvailable && <p className="test-feedback-screenshot-status">No screenshot was attached to this report.</p>}
          </section>
          <section className="test-feedback-upload-events"><h3>Screenshot processing</h3>
            {(selected.screenshotEvents ?? []).length ? <ol>{selected.screenshotEvents!.map((event, index) => <li key={`${event.createdAt}-${index}`} className={`upload-event upload-event-${event.outcome.toLowerCase()}`}><div><strong>{event.eventType.replaceAll("_", " ")}</strong><time>{new Date(event.createdAt).toLocaleString()}</time></div><p>{event.errorCode || event.detail || event.outcome}</p><small>Reported: {formatBytes(event.reportedSizeBytes)} · S3 actual: {formatBytes(event.actualSizeBytes)} · {event.contentType || "unknown type"}</small></li>)}</ol>
              : <p className="test-feedback-empty-events">No screenshot upload was started for this feedback.</p>}
          </section>
          <section className="test-feedback-review"><h3>{tx("Admin review", "Yönetici incelemesi")}</h3><label>{tx("Status", "Durum")}<select value={reviewStatus} onChange={(event) => setReviewStatus(event.target.value as TestFeedbackStatus)}>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select></label><label>{tx("Internal note", "Dahili not")}<textarea rows={5} maxLength={2000} value={note} onChange={(event) => setNote(event.target.value)} /></label>{selected.reviewedByEmail && <small>{tx("Last reviewed by", "Son inceleyen")} {selected.reviewedByEmail}</small>}</section>
        </div>
        <footer className="modal-actions padded-actions"><button className="ghost-button" type="button" onClick={() => setSelected(null)}>{tx("Cancel", "İptal")}</button><button className="primary-button" disabled={saving} type="submit">{saving ? tx("Saving...", "Kaydediliyor...") : tx("Save review", "İncelemeyi kaydet")}</button></footer>
      </form>
    </div>}
  </div>;
}

function FeedbackRanking({ items, empty }: { items: Record<string, number>; empty: string }) {
  const entries = Object.entries(items).sort((left, right) => right[1] - left[1]).slice(0, 6);
  const maximum = Math.max(...entries.map(([, value]) => value), 1);
  if (!entries.length) return <p className="feedback-ranking-empty">{empty}</p>;
  return <div className="feedback-ranking">{entries.map(([label, value]) => <div key={label}>
    <div><span>{label}</span><strong>{value}</strong></div>
    <i><b style={{ width: `${Math.max(5, value / maximum * 100)}%` }} /></i>
  </div>)}</div>;
}

function feedbackFilterSummary(filters: { status: string; type: string; platform: string; route: string }, tx: (en: string, tr: string) => string) {
  const count = Object.values(filters).filter(Boolean).length;
  return count ? `${count} ${tx("active filter(s)", "aktif filtre")}` : tx("Status, result, platform or application route", "Durum, sonuç, platform veya uygulama yolu");
}
