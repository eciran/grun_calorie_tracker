import { FormEvent, lazy, Suspense, useEffect, useMemo, useState } from "react";
import { formatRequestError, PageResponse, request } from "./api";
import { DataTable, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import {
  RuntimeApiMetrics,
  RuntimeOperationRecord,
  RuntimeOperationsPolicy,
  SystemReliabilityAnalytics
} from "./types";

const ApiReliabilityChart = lazy(() => import("./SystemReliabilityCharts").then((module) => ({ default: module.ApiReliabilityChart })));
const InfrastructureReliabilityChart = lazy(() => import("./SystemReliabilityCharts").then((module) => ({ default: module.InfrastructureReliabilityChart })));
const ProviderReliabilityChart = lazy(() => import("./SystemReliabilityCharts").then((module) => ({ default: module.ProviderReliabilityChart })));
const OperationReliabilityChart = lazy(() => import("./SystemReliabilityCharts").then((module) => ({ default: module.OperationReliabilityChart })));
const FEATURES = [
  "BARCODE_SCANNER", "MANUAL_FOOD_LOGGING", "FOOD_DIARY", "WEIGHT_PROGRESS",
  "WATER_TRACKING", "WORKOUT_LOGGING", "SAVED_MEAL_TEMPLATES", "RECIPE_BUILDER",
  "PUBLIC_RECIPE_LIBRARY", "NEXT_MEAL_SUGGESTIONS", "ADVANCED_MACRO_TARGETS",
  "MICRONUTRIENT_DETAILS", "MICRONUTRIENT_ANALYTICS", "DATA_EXPORT",
  "FASTING_BASIC", "FASTING_ADVANCED", "AI_MEAL_DRAFTS", "AI_WORKOUT_PLANNER",
  "AI_RECIPE_GENERATION", "AI_MEAL_PREPARATION_GUIDE", "AI_NUTRITION_PLAN",
  "AI_INSIGHTS", "HEALTH_INTEGRATION", "ADVANCED_ANALYTICS", "AD_FREE",
  "CUSTOM_FOOD_LIBRARY"
];

const EMPTY_RECORD = {
  recordType: "INCIDENT",
  status: "OPEN",
  operationKey: "",
  title: "",
  summary: "",
  retryable: false
};

export function RuntimeOperationsView({ onError }: { onError: (message: string | null) => void }) {
  const [policy, setPolicy] = useState<RuntimeOperationsPolicy | null>(null);
  const [draft, setDraft] = useState<RuntimeOperationsPolicy | null>(null);
  const [metrics, setMetrics] = useState<RuntimeApiMetrics | null>(null);
  const [reliability, setReliability] = useState<SystemReliabilityAnalytics | null>(null);
  const [reliabilityWindowHours, setReliabilityWindowHours] = useState(24);
  const [records, setRecords] = useState<PageResponse<RuntimeOperationRecord> | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [typeFilter, setTypeFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [reason, setReason] = useState("");
  const [recordDraft, setRecordDraft] = useState(EMPTY_RECORD);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [actionState, setActionState] = useState<"idle" | "loading">("idle");
  const [confirmAction, setConfirmAction] = useState<"save" | "rollback" | null>(null);

  const changedFields = useMemo(() => {
    if (!policy || !draft) return [];
    return Object.keys(draft).filter((key) =>
      !["version", "rollbackAvailable", "updatedBy", "updatedAt"].includes(key)
      && draft[key as keyof RuntimeOperationsPolicy] !== policy[key as keyof RuntimeOperationsPolicy]);
  }, [draft, policy]);

  async function load() {
    setState("loading");
    try {
      const query = new URLSearchParams({ page: String(page), size: String(pageSize), sort: "createdAt,desc" });
      if (typeFilter) query.set("type", typeFilter);
      if (statusFilter) query.set("status", statusFilter);
      const [nextPolicy, nextMetrics, nextReliability, nextRecords] = await Promise.all([
        request<RuntimeOperationsPolicy>("/api/v1/admin/system/operations/policy"),
        request<RuntimeApiMetrics>("/api/v1/admin/system/operations/api-metrics"),
        request<SystemReliabilityAnalytics>(`/api/v1/admin/system/operations/reliability-analytics?windowHours=${reliabilityWindowHours}`),
        request<PageResponse<RuntimeOperationRecord>>(`/api/v1/admin/system/operations/records?${query}`)
      ]);
      setPolicy(nextPolicy);
      setDraft(nextPolicy);
      setMetrics(nextMetrics);
      setReliability(nextReliability);
      setRecords(nextRecords);
      setState("ready");
      onError(null);
    } catch (error) {
      setState("error");
      onError(formatRequestError(error));
    }
  }

  useEffect(() => { void load(); }, [page, pageSize, typeFilter, statusFilter, reliabilityWindowHours]);

  async function savePolicy() {
    if (!draft) return;
    setActionState("loading");
    try {
      const next = await request<RuntimeOperationsPolicy>("/api/v1/admin/system/operations/policy", {
        method: "PUT",
        body: { ...draft, reason }
      });
      setPolicy(next);
      setDraft(next);
      setReason("");
      setConfirmAction(null);
      onError(null);
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setActionState("idle");
    }
  }

  async function rollbackPolicy() {
    if (!policy) return;
    setActionState("loading");
    try {
      const next = await request<RuntimeOperationsPolicy>("/api/v1/admin/system/operations/policy/rollback", {
        method: "POST",
        body: { version: policy.version, reason }
      });
      setPolicy(next);
      setDraft(next);
      setReason("");
      setConfirmAction(null);
      onError(null);
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setActionState("idle");
    }
  }

  async function createRecord(event: FormEvent) {
    event.preventDefault();
    setActionState("loading");
    try {
      await request("/api/v1/admin/system/operations/records", {
        method: "POST",
        body: recordDraft
      });
      setRecordDraft(EMPTY_RECORD);
      await load();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setActionState("idle");
    }
  }

  async function retryRecord(id: number) {
    setActionState("loading");
    try {
      await request(`/api/v1/admin/system/operations/records/${id}/retry`, { method: "POST" });
      await load();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setActionState("idle");
    }
  }

  function updateDraft<K extends keyof RuntimeOperationsPolicy>(key: K, value: RuntimeOperationsPolicy[K]) {
    setDraft((current) => current ? { ...current, [key]: value } : current);
  }

  const rows = records?.content ?? [];
  const allowedStatuses = recordDraft.recordType === "INCIDENT"
    ? ["OPEN", "MONITORING", "RESOLVED"]
    : ["RUNNING", "SUCCEEDED", "FAILED"];

  return (
    <div className="stack runtime-operations-view">
      <SectionToolbar
        title="Production control center"
        description="Backend-owned runtime policy, operational signals, incidents, backups, and scheduled job visibility."
        state={state}
        onReload={() => void load()}
      />

      {metrics && (
        <div className="runtime-metric-grid">
          <MetricCard label="API requests" value={String(metrics.requests)} hint={`Window since ${formatDate(metrics.windowStartedAt)}`} />
          <MetricCard label="p95 latency" value={`${metrics.latencyP95Ms} ms`} hint={`p50 ${metrics.latencyP50Ms} / p99 ${metrics.latencyP99Ms}`} />
          <MetricCard label="Error rate" value={`${(metrics.errorRate * 100).toFixed(2)}%`} hint={`${metrics.errors} server errors`} />
          <MetricCard label="Security signals" value={String(metrics.authenticationFailures + metrics.authorizationFailures)} hint={`${metrics.rateLimited} rate limited`} />
        </div>
      )}

      {reliability && (
        <Panel title="System and provider reliability" description="Privacy-safe operational aggregates. API history covers the current process lifetime; provider and operation windows use persisted records.">
          <div className="reliability-toolbar" role="group" aria-label="Reliability time window">
            {[1, 24, 72, 168].map((hours) => (
              <button className={reliabilityWindowHours === hours ? "active" : ""} key={hours} onClick={() => setReliabilityWindowHours(hours)} type="button">
                {hours === 1 ? "1 hour" : hours === 24 ? "24 hours" : `${hours / 24} days`}
              </button>
            ))}
            <span>Generated {formatDate(reliability.generatedAt)}</span>
          </div>
          <Suspense fallback={<div className="reliability-chart-loading">Loading reliability charts...</div>}>
          <div className="reliability-chart-grid">
            <div className="reliability-chart-card"><div><strong>API traffic and latency</strong><span>Hourly requests, server errors, and p95 latency.</span></div><ApiReliabilityChart analytics={reliability} /></div>
            <div className="reliability-chart-card"><div><strong>Infrastructure signals</strong><span>Live database, Redis, heap, and cache measurements.</span></div><InfrastructureReliabilityChart analytics={reliability} /></div>
            <div className="reliability-chart-card"><div><strong>Provider outcomes</strong><span>Aggregate delivery and processing results; ignored/skipped events are not failures.</span></div><ProviderReliabilityChart analytics={reliability} /></div>
            <div className="reliability-chart-card"><div><strong>Operational outcomes</strong><span>Scheduled jobs, incidents, backups, restore drills, and dead letters.</span></div><OperationReliabilityChart analytics={reliability} /></div>
          </div>
          </Suspense>
          <div className="reliability-status-strip">
            {reliability.providers.map((provider) => <span key={provider.provider}><strong>{provider.provider}</strong>{provider.status}{provider.successRate == null ? "" : ` · ${provider.successRate.toFixed(1)}% success`}</span>)}
          </div>
        </Panel>
      )}
      {draft && (
        <Panel title="Runtime policy" description="No provider secret or credential is stored in this policy. Every change is versioned and audited.">
          <div className={`runtime-maintenance-banner ${draft.maintenanceEnabled ? "active" : ""}`}>
            <label className="toggle-field">
              <input type="checkbox" checked={draft.maintenanceEnabled} onChange={(event) => updateDraft("maintenanceEnabled", event.target.checked)} />
              <span>Maintenance mode</span>
            </label>
            <span>{draft.maintenanceEnabled ? "User API traffic will return 503; admin, auth, and webhook routes stay available." : "Normal user traffic is enabled."}</span>
          </div>
          <div className="runtime-policy-grid">
            <label className="wide-field">Maintenance message<input maxLength={240} value={draft.maintenanceMessage} onChange={(event) => updateDraft("maintenanceMessage", event.target.value)} /></label>
            <label>Release version<input value={draft.releaseVersion} onChange={(event) => updateDraft("releaseVersion", event.target.value)} /></label>
            <label>Environment<input value={draft.deploymentEnvironment} onChange={(event) => updateDraft("deploymentEnvironment", event.target.value)} /></label>
            <label>Minimum iOS<input value={draft.minimumIosVersion} onChange={(event) => updateDraft("minimumIosVersion", event.target.value)} /></label>
            <label>Minimum Android<input value={draft.minimumAndroidVersion} onChange={(event) => updateDraft("minimumAndroidVersion", event.target.value)} /></label>
            <label>Rollout feature<select value={draft.rolloutFeature} onChange={(event) => updateDraft("rolloutFeature", event.target.value)}>{FEATURES.map((feature) => <option key={feature}>{feature}</option>)}</select></label>
            <label>Plan<select value={draft.rolloutPlan ?? ""} onChange={(event) => updateDraft("rolloutPlan", event.target.value || null)}><option value="">All plans</option><option>FREE</option><option>PLUS</option><option>PRO</option></select></label>
            <label>Region<select value={draft.rolloutRegion ?? ""} onChange={(event) => updateDraft("rolloutRegion", event.target.value || null)}><option value="">All regions</option><option>TR</option><option>UK_IE</option><option>EU</option><option>GLOBAL</option></select></label>
            <label>Segment<select value={draft.rolloutSegment} onChange={(event) => updateDraft("rolloutSegment", event.target.value)}><option>ALL</option><option>NEW_USERS</option></select></label>
            <label>Rollout %<input type="number" min={0} max={100} value={draft.rolloutPercentage} onChange={(event) => updateDraft("rolloutPercentage", Number(event.target.value))} /></label>
            <label className="toggle-field compact"><input type="checkbox" checked={draft.rolloutEnabled} onChange={(event) => updateDraft("rolloutEnabled", event.target.checked)} /><span>Rollout enabled</span></label>
            <label>p95 warning (ms)<input type="number" min={50} max={120000} value={draft.apiLatencyWarningMs} onChange={(event) => updateDraft("apiLatencyWarningMs", Number(event.target.value))} /></label>
            <label>Error rate threshold<input type="number" min={0.001} max={1} step={0.001} value={draft.apiErrorRateThreshold} onChange={(event) => updateDraft("apiErrorRateThreshold", Number(event.target.value))} /></label>
            <label>Escalation email<input type="email" value={draft.escalationTarget ?? ""} onChange={(event) => updateDraft("escalationTarget", event.target.value || null)} /></label>
            <label className="wide-field">Admin reason<input minLength={8} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Why this production change is required" /></label>
          </div>
          <div className="runtime-policy-preview">
            <strong>{changedFields.length} pending change(s)</strong>
            <span>{changedFields.length ? changedFields.join(", ") : "Policy matches the persisted backend state."}</span>
          </div>
          <div className="runtime-action-row">
            <button className="primary-button" disabled={!changedFields.length || reason.trim().length < 8 || actionState === "loading"} onClick={() => setConfirmAction("save")} type="button">Review and apply</button>
            <button className="ghost-button" disabled={!policy?.rollbackAvailable || reason.trim().length < 8 || actionState === "loading"} onClick={() => setConfirmAction("rollback")} type="button">Rollback previous</button>
            <span>Updated {formatDate(policy?.updatedAt)} by {policy?.updatedBy ?? "-"}</span>
          </div>
        </Panel>
      )}

      <div className="runtime-operations-split">
        <Panel title="Record an operation" description="Create an incident, backup, or restore-drill record. Scheduled jobs remain backend-owned.">
          <form className="runtime-record-form" onSubmit={createRecord}>
            <label>Type<select value={recordDraft.recordType} onChange={(event) => setRecordDraft({ ...recordDraft, recordType: event.target.value, status: event.target.value === "INCIDENT" ? "OPEN" : "RUNNING" })}><option>INCIDENT</option><option>BACKUP</option><option>RESTORE_DRILL</option></select></label>
            <label>Status<select value={recordDraft.status} onChange={(event) => setRecordDraft({ ...recordDraft, status: event.target.value })}>{allowedStatuses.map((status) => <option key={status}>{status}</option>)}</select></label>
            <label>Operation key<input required pattern="[A-Za-z0-9._:-]+" value={recordDraft.operationKey} onChange={(event) => setRecordDraft({ ...recordDraft, operationKey: event.target.value })} placeholder="incident-2026-07-27" /></label>
            <label>Title<input required maxLength={160} value={recordDraft.title} onChange={(event) => setRecordDraft({ ...recordDraft, title: event.target.value })} /></label>
            <label className="wide-field">Safe summary<textarea required maxLength={1000} value={recordDraft.summary} onChange={(event) => setRecordDraft({ ...recordDraft, summary: event.target.value })} placeholder="Do not include credentials, tokens, or customer payloads." /></label>
            <button className="primary-button" disabled={actionState === "loading"} type="submit">Save operation record</button>
          </form>
        </Panel>
        <Panel title="Operational guardrails">
          <div className="runtime-guardrail-list">
            <span>Secrets and raw provider payloads are excluded.</span>
            <span>Maintenance leaves admin recovery, login, and webhooks available.</span>
            <span>Feature rollout is an exposure control; backend entitlement checks remain authoritative.</span>
            <span>Backup and restore rows are evidence records, not database control buttons.</span>
          </div>
        </Panel>
      </div>

      <Panel title="Operations ledger" description="Paginated history for scheduled jobs, incidents, backups, restore drills, retries, and dead letters.">
        <div className="runtime-filter-row">
          <label>Type<select value={typeFilter} onChange={(event) => { setTypeFilter(event.target.value); setPage(0); }}><option value="">All types</option><option>INCIDENT</option><option>BACKUP</option><option>RESTORE_DRILL</option><option>SCHEDULED_JOB</option></select></label>
          <label>Status<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(0); }}><option value="">All statuses</option>{["SCHEDULED", "RUNNING", "SUCCEEDED", "FAILED", "DEAD_LETTER", "OPEN", "MONITORING", "RESOLVED"].map((status) => <option key={status}>{status}</option>)}</select></label>
        </div>
        <DataTable columns={["Operation", "Type", "Status", "Timing", "Action"]} rows={rows.map((item) => [
          <div className="entity-cell"><strong>{item.title}</strong><small>{item.operationKey} · {item.summary}</small></div>,
          item.recordType,
          item.status,
          <div className="entity-cell"><span>{formatDate(item.createdAt)}</span><small>{item.nextRunAt ? `Next ${formatDate(item.nextRunAt)}` : "No next run"}</small></div>,
          item.recordType === "SCHEDULED_JOB" && item.retryable && ["FAILED", "DEAD_LETTER"].includes(item.status)
            ? <button className="ghost-button" disabled={actionState === "loading"} onClick={() => void retryRecord(item.id)} type="button">Queue retry</button>
            : <span className="muted">Read only</span>
        ])} empty="No operation records match these filters." />
        <PaginationControls
          page={records?.page ?? page}
          pageSize={records?.size ?? pageSize}
          totalElements={records?.totalElements ?? 0}
          totalPages={records?.totalPages ?? 1}
          first={records?.first ?? page === 0}
          last={records?.last ?? true}
          onPageChange={setPage}
          onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
        />
      </Panel>

      {confirmAction && (
        <div className="modal-backdrop confirm-backdrop" role="presentation" onClick={() => setConfirmAction(null)}>
          <section className="confirm-dialog" role="alertdialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="confirm-dialog-content">
              <div className={`confirm-dialog-icon ${confirmAction === "rollback" || draft?.maintenanceEnabled ? "danger" : "neutral"}`}>!</div>
              <div className="confirm-dialog-copy">
                <p className="eyebrow">Production confirmation</p>
                <h2>{confirmAction === "rollback" ? "Rollback runtime policy?" : "Apply runtime policy?"}</h2>
                <p>{confirmAction === "rollback" ? "The previous typed snapshot will become active immediately." : `${changedFields.length} reviewed field(s) will become active immediately.`}</p>
              </div>
            </div>
            <div className="modal-actions">
              <button className="ghost-button" onClick={() => setConfirmAction(null)} type="button">Cancel</button>
              <button className="primary-button danger-button" disabled={actionState === "loading"} onClick={() => void (confirmAction === "rollback" ? rollbackPolicy() : savePolicy())} type="button">{actionState === "loading" ? "Applying..." : "Confirm"}</button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

function formatDate(value?: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
