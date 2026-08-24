import { useEffect, useState } from "react";
import { formatRequestError, request } from "./api";
import { DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import type { AdminAccessProfile } from "./types";
import { ManualProductIntakeForm } from "./ManualProductIntakeForm";

type IntakeSummary = { id: number; source?: string; status?: string; marketRegion?: string; barcode?: string; resolutionMode?: string; riskLevel?: string; assignedAdminEmail?: string; createdAt?: string; updatedAt?: string };
type IntakePage = { content: IntakeSummary[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };
type Evidence = { assetId: number; assetType?: string; sizeBytes?: number; available: boolean };
type EvidenceRead = { assetId: number; assetType?: string; contentType?: string; signedUrl: string; expiresAt?: string };
type IntakeDetail = {
  summary: IntakeSummary;
  reviewNote?: string;
  linkedFoodItemId?: number;
  fieldComparisons?: Array<{ field: string; submittedValue?: unknown; catalogValue?: unknown; equal: boolean; highImpact: boolean }>;
  corroboratingEvidence?: Array<{ field: string; provider: string; numericValue: number; basis: string; confidenceScore: number; observedAt?: string; sourceVersion?: string }>;
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
  const [applyFields, setApplyFields] = useState<string[]>([]);
  const [evidencePreview, setEvidencePreview] = useState<EvidenceRead | null>(null);
  const [evidenceLoadingId, setEvidenceLoadingId] = useState<number | null>(null);
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
      setApplyFields([]);
      setEvidencePreview(null);
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

  function applySelectedFields() {
    if (!selected || applyFields.length === 0) return;
    const highImpact = (selected.fieldComparisons ?? []).filter((item) => item.highImpact && applyFields.includes(toApplyField(item.field) ?? ""));
    const confirmed = highImpact.length === 0 || window.confirm(`This materially changes ${highImpact.map((item) => item.field).join(", ")}. Apply only after checking all evidence. Continue?`);
    if (!confirmed) return;
    void mutate("apply-existing", { fields: applyFields, confirmed: true }, "Selected fields applied to the existing product.");
  }

  function publishSelectedCandidate() {
    if (!note.trim()) return;
    const confirmed = window.confirm("Publish this verified candidate to the public catalog? This action will notify the contributor.");
    if (confirmed) void mutate("publish-candidate", { note: note.trim(), confirmed: true }, "Candidate verified and published through the central publication service.");
  }
  async function viewEvidence(asset: Evidence) {
    setEvidenceLoadingId(asset.assetId);
    try {
      const read = await request<EvidenceRead>("/api/v1/admin/products/review-cases/assets/" + asset.assetId + "/evidence-url");
      setEvidencePreview(read);
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setEvidenceLoadingId(null);
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
        <header className="modal-header"><div><span>PRODUCT INTAKE #{selected.summary.id}</span><h2>{selected.summary.barcode ?? "Candidate"}</h2><p>{selected.summary.source} · {selected.summary.marketRegion ?? "Unspecified market"}</p></div><button className="modal-icon-close" type="button" onClick={() => setSelected(null)} aria-label="Close product intake review">×</button></header>
        <div className="contribution-review-body">
          <div className="contribution-review-details">
            <h3>Submitted vs catalog</h3>
            <DataTable columns={["Apply", "Field", "Submitted", "Catalog", "Match"]} rows={(selected.fieldComparisons ?? []).map((item) => { const applyField = toApplyField(item.field); const selectable = canWrite && selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "UPDATE_EXISTING" && Boolean(applyField); return [selectable ? <input type="checkbox" aria-label={`Apply ${item.field}`} checked={applyFields.includes(applyField!)} onChange={(event) => setApplyFields((current) => event.target.checked ? [...current, applyField!] : current.filter((value) => value !== applyField))} /> : "-", item.field, formatField(item.submittedValue), formatField(item.catalogValue), item.equal ? "Match" : item.highImpact ? "High impact" : "Review"]; })} empty="No field comparison is available." />
            {selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "UPDATE_EXISTING" && canWrite && <div className="review-apply-panel"><strong>Apply selected fields to the published product</strong><p>Only checked fields will change. Barcode, market and publication state are never edited here.</p><button className="primary-button" type="button" disabled={busy || applyFields.length === 0} onClick={applySelectedFields}>Apply {applyFields.length} selected field{applyFields.length === 1 ? "" : "s"}</button></div>}
            {(selected.warnings ?? []).map((warning) => <div className="warning-banner" key={warning}>{warning}</div>)}
            <h3>Corroborating source evidence</h3>
            <DataTable columns={["Field", "Provider", "Value", "Basis", "Confidence", "Observed"]} rows={(selected.corroboratingEvidence ?? []).map((item) => [item.field, item.provider, item.numericValue, item.basis, `${item.confidenceScore}%`, formatDate(item.observedAt)])} empty="No additional source evidence is available." />
            <label>Review note<textarea maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} /></label>
          </div>
          <div className="contribution-evidence-panel">
            <div className="evidence-panel-heading"><div><span>PRIVATE EVIDENCE</span><h3>Submitted images</h3></div><small>{selected.evidence?.length ?? 0} files</small></div>
            {canWrite && <div className="evidence-selector" role="tablist" aria-label="Submitted product images">{(selected.evidence ?? []).map((asset) => <button className={evidencePreview?.assetId === asset.assetId ? "evidence-tab active" : "evidence-tab"} type="button" role="tab" key={asset.assetId} disabled={!asset.available || evidenceLoadingId !== null} onClick={() => void viewEvidence(asset)}><strong>{formatEvidenceType(asset.assetType)}</strong><span>{evidenceLoadingId === asset.assetId ? "Loading..." : formatBytes(asset.sizeBytes)}</span></button>)}</div>}
            {canWrite && evidencePreview && <div className="evidence-image-frame"><img src={evidencePreview.signedUrl} alt={formatEvidenceType(evidencePreview.assetType) + " submitted for review"} /><span>{formatEvidenceType(evidencePreview.assetType)}</span></div>}
            {canWrite && !evidencePreview && Boolean(selected.evidence?.length) && <div className="evidence-placeholder"><strong>Select an image</strong><span>Open the package or nutrition label without leaving this review.</span></div>}
            {!selected.evidence?.length && <span>No evidence attached.</span>}
            <small>{canWrite ? "Images use short-lived private links and are loaded only when selected." : "Private evidence requires owner or catalog-admin access."}</small>
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
          {canWrite && selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "NEW_CANDIDATE" && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={publishSelectedCandidate}>Verify and publish candidate</button>}
          {canWrite && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/approve", { note: note.trim() }, "Evidence approved; publication remains separate.")}>Approve evidence</button>}
        </footer>
      </section>
    </div>}
  </div>;
}

function toApplyField(field: string) {
  const normalized = field.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase();
  const supported = new Set(["PRODUCT_NAME", "BRAND", "CALORIES", "PROTEIN", "FAT", "CARBS", "FIBER", "SUGAR", "SODIUM"]);
  return supported.has(normalized) ? normalized : null;
}function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "-";
}
function formatField(value: unknown) {
  if (value === null || value === undefined || value === "") return "-";
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}
function formatEvidenceType(value?: string) {
  if (!value) return "Evidence";
  return value.split("_").map((part) => part.charAt(0) + part.slice(1).toLowerCase()).join(" ");
}
function formatBytes(value?: number) {
  if (value === undefined) return "size unavailable";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
