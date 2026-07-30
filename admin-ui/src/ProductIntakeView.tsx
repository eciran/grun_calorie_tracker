import { useEffect, useState } from "react";
import { formatRequestError, request } from "./api";
import { DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import type { AdminAccessProfile } from "./types";
import { ManualProductIntakeForm } from "./ManualProductIntakeForm";

type IntakeSummary = { id: number; source?: string; status?: string; marketRegion?: string; barcode?: string; riskLevel?: string; assignedAdminEmail?: string; createdAt?: string; updatedAt?: string };
type IntakePage = { content: IntakeSummary[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };
type Evidence = { assetId: number; assetType?: string; sizeBytes?: number; available: boolean };
type IntakeDetail = {
  summary: IntakeSummary;
  reviewNote?: string;
  linkedFoodItemId?: number;
  fieldComparisons?: Array<{ field: string; submittedValue?: unknown; catalogValue?: unknown; equal: boolean }>;
  warnings?: string[];
  evidence?: Evidence[];
};
type QueueMode = "ALL" | "MY_QUEUE" | "UNASSIGNED" | "NEEDS_ACTION" | "HIGH_RISK" | "OVERDUE";

const QUEUES: QueueMode[] = ["ALL", "MY_QUEUE", "UNASSIGNED", "NEEDS_ACTION", "HIGH_RISK", "OVERDUE"];
const REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];

export function ProductIntakeView({ accessProfile, onError }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void }) {
  const [queue, setQueue] = useState<QueueMode>("NEEDS_ACTION");
  const [status, setStatus] = useState("");
  const [marketRegion, setMarketRegion] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [data, setData] = useState<IntakePage | null>(null);
  const [state, setState] = useState<LoadState>("loading");
  const [selected, setSelected] = useState<IntakeDetail | null>(null);
  const [note, setNote] = useState("");
  const [foodItemId, setFoodItemId] = useState("");
  const [reassignEmail, setReassignEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const canWrite = Boolean(accessProfile?.permissions?.includes("CATALOG_MANAGE"));
  const isOwner = accessProfile?.role === "OWNER";

  async function reload() {
    setState("loading");
    onError(null);
    try {
      const params = new URLSearchParams({ queue, page: String(page), size: String(size) });
      if (status) params.set("status", status);
      if (marketRegion) params.set("marketRegion", marketRegion);
      setData(await request<IntakePage>(`/api/v1/admin/product-intakes?${params}`));
      setState("ready");
    } catch (error) {
      setState("error");
      onError(formatRequestError(error));
    }
  }

  useEffect(() => { void reload(); }, [queue, status, marketRegion, page, size]);
  useEffect(() => { setPage(0); }, [queue, status, marketRegion, size]);

  async function open(item: IntakeSummary) {
    try {
      const detail = await request<IntakeDetail>(`/api/v1/admin/product-intakes/${item.id}`);
      setSelected(detail);
      setNote(detail.reviewNote ?? "");
      setFoodItemId(detail.linkedFoodItemId ? String(detail.linkedFoodItemId) : "");
    } catch (error) {
      onError(formatRequestError(error));
    }
  }

  async function mutate(path: string, body?: unknown, success = "Action completed.") {
    if (!selected) return;
    setBusy(true);
    try {
      await request(`/api/v1/admin/product-intakes/${selected.summary.id}/${path}`, { method: "PATCH", body });
      setNotice(success);
      window.setTimeout(() => setNotice(null), 2500);
      setSelected(await request<IntakeDetail>(`/api/v1/admin/product-intakes/${selected.summary.id}`));
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setBusy(false);
    }
  }

  async function viewEvidence(asset: Evidence) {
    try {
      const read = await request<{ signedUrl: string }>(`/api/v1/admin/products/review-cases/assets/${asset.assetId}/evidence-url`);
      window.open(read.signedUrl, "_blank", "noopener,noreferrer");
    } catch (error) {
      onError(formatRequestError(error));
    }
  }

  const rows = data?.content ?? [];
  return <div className="stack contribution-review-workspace">
    <SectionToolbar title="Product Intake Workbench" description="Unified user and admin candidate review with lazy private evidence." state={state} onReload={reload} />
    {notice && <div className="success-banner compact-success">{notice}</div>}
    <div className="review-workspace-summary">
      <MetricCard label="Matching cases" value={String(data?.totalElements ?? 0)} hint="Current server-side filter" />
      <MetricCard label="Needs action" value={String(rows.filter((item) => item.status === "NEEDS_ACTION").length)} hint="On this page" />
      <MetricCard label="High risk" value={String(rows.filter((item) => item.riskLevel === "HIGH").length)} hint="On this page" />
      <MetricCard label="Unassigned" value={String(rows.filter((item) => !item.assignedAdminEmail).length)} hint="On this page" />
    </div>
    <ManualProductIntakeForm canWrite={canWrite} onCreated={reload} onError={onError} />
    <Panel title="Queue filters" description="Market is optional metadata; no country is selected by default.">
      <div className="review-filter-grid contribution-filter-grid">
        <label>Queue<select value={queue} onChange={(event) => setQueue(event.target.value as QueueMode)}>{QUEUES.map((value) => <option key={value}>{value}</option>)}</select></label>
        <label>Status<input value={status} onChange={(event) => setStatus(event.target.value.toUpperCase())} placeholder="All statuses" /></label>
        <label>Market region<select value={marketRegion} onChange={(event) => setMarketRegion(event.target.value)}><option value="">All markets</option>{REGIONS.map((value) => <option key={value}>{value}</option>)}</select></label>
      </div>
    </Panel>
    <DataTable
      columns={["Case", "Source", "Market", "Risk", "Assignment", "Updated"]}
      rows={rows.map((item) => [
        <div className="table-stack"><strong>#{item.id}</strong><span>{item.barcode ?? "No barcode"}</span><small>{item.status ?? "-"}</small></div>,
        item.source ?? "-", item.marketRegion ?? "Unspecified", item.riskLevel ?? "-", item.assignedAdminEmail ?? "Unassigned", formatDate(item.updatedAt ?? item.createdAt)
      ])}
      rowData={rows}
      onRowClick={open}
      empty="No product-intake cases match these filters."
    />
    <PaginationControls page={data?.page ?? page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 1} first={data?.first ?? true} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={setSize} />
    {selected && <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
      <section className="modal-card contribution-review-modal" role="dialog" aria-modal="true" aria-label="Product intake review" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header"><div><span>PRODUCT INTAKE #{selected.summary.id}</span><h2>{selected.summary.barcode ?? "Candidate"}</h2><p>{selected.summary.source} Â· {selected.summary.marketRegion ?? "Unspecified market"}</p></div><button className="modal-icon-close" type="button" onClick={() => setSelected(null)}>x</button></header>
        <div className="contribution-review-body">
          <div className="contribution-review-details">
            <h3>Submitted vs catalog</h3>
            <DataTable columns={["Field", "Submitted", "Catalog", "Match"]} rows={(selected.fieldComparisons ?? []).map((item) => [item.field, formatField(item.submittedValue), formatField(item.catalogValue), item.equal ? "Match" : "Review"])} empty="No field comparison is available." />
            {(selected.warnings ?? []).map((warning) => <div className="warning-banner" key={warning}>{warning}</div>)}
            <label>Review note<textarea maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} /></label>
          </div>
          <div className="contribution-evidence-panel">
            <h3>Private evidence</h3>
            {canWrite && (selected.evidence ?? []).map((asset) => <button className="ghost-button" type="button" key={asset.assetId} disabled={!asset.available} onClick={() => void viewEvidence(asset)}>{asset.assetType ?? "Evidence"} Â· {formatBytes(asset.sizeBytes)}</button>)}
            {!selected.evidence?.length && <span>No evidence attached.</span>}
            <small>{canWrite ? "URLs are requested only when opened and expire automatically." : "Read-only accounts cannot open private evidence."}</small>
            {canWrite && <div className="inline-actions"><button className="ghost-button" disabled={busy} type="button" onClick={() => void mutate("claim", undefined, "Case claimed.")}>Claim</button><button className="ghost-button" disabled={busy} type="button" onClick={() => void mutate("release", undefined, "Case released.")}>Release</button></div>}
            {isOwner && <label>Reassign to<input type="email" value={reassignEmail} onChange={(event) => setReassignEmail(event.target.value)} /></label>}
            {isOwner && <button className="ghost-button" disabled={busy || !reassignEmail.trim()} type="button" onClick={() => void mutate("reassign", { adminEmail: reassignEmail.trim() }, "Case reassigned.")}>Reassign</button>}
            {canWrite && <label>Existing food item ID<input inputMode="numeric" value={foodItemId} onChange={(event) => setFoodItemId(event.target.value.replace(/\D/g, ""))} /></label>}
            {canWrite && <button className="ghost-button" disabled={busy || !foodItemId} type="button" onClick={() => void mutate("attach-existing-product", { foodItemId: Number(foodItemId) }, "Existing product attached.")}>Attach existing product</button>}
          </div>
        </div>
        <footer className="modal-actions padded-actions">
          <button className="ghost-button" type="button" onClick={() => setSelected(null)}>Close</button>
          {canWrite && <button className="ghost-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("request-better-evidence", { note: note.trim() }, "Better evidence requested.")}>Request better evidence</button>}
          {canWrite && <button className="ghost-button danger-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/reject", { note: note.trim() }, "Evidence rejected.")}>Reject evidence</button>}
          {canWrite && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/approve", { note: note.trim() }, "Evidence approved; publication remains separate.")}>Approve evidence</button>}
        </footer>
      </section>
    </div>}
  </div>;
}

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "-";
}
function formatField(value: unknown) {
  if (value === null || value === undefined || value === "") return "-";
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}
function formatBytes(value?: number) {
  if (value === undefined) return "size unavailable";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
