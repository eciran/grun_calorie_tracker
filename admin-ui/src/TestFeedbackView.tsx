import { FormEvent, useCallback, useEffect, useState } from "react";
import { formatRequestError, request, requestBlob } from "./api";
import { DataTable, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import type { AdminTestFeedback, AdminTestFeedbackAnalytics, AdminTestFeedbackPage, TestFeedbackStatus } from "./types";
import "./test-feedback.css";

const STATUSES = ["NEW", "REVIEWING", "FIX_PLANNED", "FIXED", "RETEST_NEEDED", "CLOSED"] as const;
const TYPES = ["WORKS_WELL", "PROBLEM", "IMPROVEMENT"] as const;
const PLATFORMS = ["ANDROID", "IOS"] as const;
const formatBytes = (value?: number | null) => value == null ? "-" : value < 1024 * 1024
  ? `${Math.round(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(2)} MB`;

export function TestFeedbackView({ onError }: { onError: (message: string | null) => void }) {
  const [data, setData] = useState<AdminTestFeedbackPage>({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, first: true, last: true });
  const [analytics, setAnalytics] = useState<AdminTestFeedbackAnalytics | null>(null);
  const [page, setPage] = useState(0); const [size, setSize] = useState(25);
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

  return <div className="view-stack test-feedback-view">
    <SectionToolbar title="Test feedback" description="Android and iOS preview build findings, triage and release evidence." state={state} onReload={load}>
      <button className="ghost-button" type="button" onClick={() => void exportCsv()}>Export CSV</button>
    </SectionToolbar>
    <div className="metrics-grid test-feedback-metrics">
      <MetricCard label="Total feedback" value={String(analytics?.total ?? 0)} hint="All internal test builds" />
      <MetricCard label="Last 7 days" value={String(analytics?.lastSevenDays ?? 0)} hint="Recent tester activity" />
      <MetricCard label="Problems" value={String(analytics?.byType?.PROBLEM ?? 0)} hint="Reported defects" />
      <MetricCard label="Open triage" value={String((analytics?.byStatus?.NEW ?? 0) + (analytics?.byStatus?.REVIEWING ?? 0))} hint="New or under review" />
      <MetricCard label="HTTP failures" value={String(analytics?.httpFailures ?? 0)} hint="Last request returned 4xx/5xx" />
      <MetricCard label="Slow requests" value={String(analytics?.slowRequests ?? 0)} hint="Last request took at least 2 seconds" />
    </div>
    <div className="test-feedback-breakdowns">
      <Panel title="Top affected routes" description="Pages receiving the most internal test feedback.">
        <div className="feedback-ranking">{Object.entries(analytics?.byRoute ?? {}).map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}{!Object.keys(analytics?.byRoute ?? {}).length && <p>No route data yet.</p>}</div>
      </Panel>
      <Panel title="Build coverage" description="Feedback distribution across preview builds.">
        <div className="feedback-ranking">{Object.entries(analytics?.byBuild ?? {}).map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}{!Object.keys(analytics?.byBuild ?? {}).length && <p>No build data yet.</p>}</div>
      </Panel>
    </div>
    <Panel title="Feedback inbox" description="Filter by triage state, result, platform or application route.">
      <form className="test-feedback-filters" onSubmit={(event) => { event.preventDefault(); setPage(0); setRoute(routeDraft.trim()); }}>
        <label>Status<select value={status} onChange={(event) => { setPage(0); setStatus(event.target.value); }}><option value="">All statuses</option>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label>Result<select value={type} onChange={(event) => { setPage(0); setType(event.target.value); }}><option value="">All results</option>{TYPES.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label>Platform<select value={platform} onChange={(event) => { setPage(0); setPlatform(event.target.value); }}><option value="">All platforms</option>{PLATFORMS.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label>Route<input value={routeDraft} onChange={(event) => setRouteDraft(event.target.value)} placeholder="/progress" /></label>
        <button className="primary-button" type="submit">Apply filters</button>
      </form>
      <DataTable columns={["Result", "Route", "Tester", "Build", "HTTP", "Status", "Submitted", "Action"]} empty="No feedback matches these filters." rows={(data.content ?? []).map((item) => [
        <span className={`status-pill feedback-${item.feedbackType.toLowerCase()}`}>{item.feedbackType.replaceAll("_", " ")}</span>,
        <div className="table-stack"><strong>{item.route}</strong><small>from {item.previousRoute || "-"}</small></div>,
        <div className="table-stack"><span>{item.userEmail}</span><small>{item.platform} · {item.deviceModel || "Unknown device"}</small></div>,
        <div className="table-stack"><span>{item.appVersion || "-"} ({item.buildNumber || "-"})</span><small>{item.easBuildId || item.commitSha || "No build reference"}</small></div>,
        item.lastHttpStatus ? `${item.lastHttpStatus} · ${item.lastHttpDurationMs ?? "-"} ms` : "-",
        <span className="status-pill">{item.status.replaceAll("_", " ")}</span>, new Date(item.createdAt).toLocaleString(),
        <button className="ghost-button" type="button" onClick={() => void inspect(item.id)}>Inspect</button>
      ])} />
      <PaginationControls page={data.page ?? page} pageSize={data.size ?? size} totalElements={data.totalElements ?? 0} totalPages={data.totalPages ?? 0} first={data.first ?? page === 0} last={data.last ?? true} onPageChange={setPage} onPageSizeChange={(value) => { setPage(0); setSize(value); }} />
    </Panel>
    {selected && <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
      <form className="modal-card test-feedback-modal" role="dialog" aria-modal="true" aria-label="Test feedback detail" onSubmit={save} onClick={(event) => event.stopPropagation()}>
        <header className="modal-header test-feedback-modal-header"><div><span>TEST FEEDBACK #{selected.id}</span><div className="test-feedback-modal-title"><h2>{selected.route || "Unknown route"}</h2><span className={`status-pill feedback-${selected.feedbackType.toLowerCase()}`}>{selected.feedbackType.replaceAll("_", " ")}</span></div><p>{selected.platform} · Submitted {new Date(selected.createdAt).toLocaleString()}</p></div><button className="modal-icon-close" type="button" aria-label="Close feedback detail" onClick={() => setSelected(null)}>x</button></header>
        <div className="modal-body test-feedback-detail-grid">
          <section className="test-feedback-report"><div className="test-feedback-section-heading"><span>TESTER INPUT</span><h3>Report details</h3></div><p className="feedback-description">{selected.description || "No written explanation was provided."}</p></section>
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
          <section className="test-feedback-review"><h3>Admin review</h3><label>Status<select value={reviewStatus} onChange={(event) => setReviewStatus(event.target.value as TestFeedbackStatus)}>{STATUSES.map((item) => <option key={item}>{item}</option>)}</select></label><label>Internal note<textarea rows={5} maxLength={2000} value={note} onChange={(event) => setNote(event.target.value)} /></label>{selected.reviewedByEmail && <small>Last reviewed by {selected.reviewedByEmail}</small>}</section>
        </div>
        <footer className="modal-actions padded-actions"><button className="ghost-button" type="button" onClick={() => setSelected(null)}>Cancel</button><button className="primary-button" disabled={saving} type="submit">{saving ? "Saving..." : "Save review"}</button></footer>
      </form>
    </div>}
  </div>;
}
