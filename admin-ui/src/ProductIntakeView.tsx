import { useEffect, useState } from "react";
import { formatRequestError, request } from "./api";
import { CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import type { AdminAccessProfile } from "./types";
import { ManualProductIntakeForm } from "./ManualProductIntakeForm";
import { useAdminLocale } from "./admin/locale";
import { sectionPaths } from "./admin/navigation";
import type { AdminTargetContext } from "./admin/shared";
import { ContributionQueueChart } from "./CatalogWorkspaceCharts";

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
  ocrRuns?: OcrRun[];
};
type OcrRun = { id: number; correlationId?: string; parserVersion?: string; model?: string; fallbackInvoked?: boolean; v3Fields?: Record<string, unknown>; v4Fields?: Record<string, unknown>; fallbackFields?: Record<string, unknown>; confirmedFields?: Record<string, unknown>; v3ExactMatchRate?: number; v4ExactMatchRate?: number; fallbackExactMatchRate?: number; v3BasisExact?: boolean; v4BasisExact?: boolean; fallbackBasisExact?: boolean; latencyMs?: number; estimatedCostUsd?: number; reconciliation?: Record<string, unknown>; createdAt?: string };
type QueueMode = "ALL" | "MY_QUEUE" | "UNASSIGNED" | "NEEDS_ACTION" | "HIGH_RISK" | "OVERDUE";

const QUEUES: QueueMode[] = ["ALL", "MY_QUEUE", "UNASSIGNED", "NEEDS_ACTION", "HIGH_RISK", "OVERDUE"];
const REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];

export function ProductIntakeView({ accessProfile, onError, targetContext, onClearTarget }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { locale } = useAdminLocale();
  const [queue, setQueue] = useState<QueueMode>("NEEDS_ACTION");
  const [status, setStatus] = useState("");
  const [marketRegion, setMarketRegion] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
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
  useEffect(() => {
    if (targetContext?.targetType !== "PRODUCT_INTAKE" || !targetContext.targetId) return;
    const id = Number(targetContext.targetId);
    if (!Number.isSafeInteger(id) || id <= 0 || selected?.summary.id === id) return;
    void open({ id });
  }, [targetContext?.targetId, targetContext?.targetType]);

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
  const needsActionCount = rows.filter((item) => item.status === "NEEDS_ACTION").length;
  const highRiskCount = rows.filter((item) => item.riskLevel === "HIGH").length;
  const unassignedCount = rows.filter((item) => !item.assignedAdminEmail).length;
  const activeFilters = [queue !== "ALL" ? queue : "", status, marketRegion].filter(Boolean);
  return <div className="stack contribution-review-workspace">
    <SectionToolbar title={locale === "tr" ? "Etiket katkıları" : "Label contributions"} description={locale === "tr" ? "Kullanıcı ve yönetici ürün adaylarını, OCR kanıtlarını ve yayın kararlarını tek kuyrukta yönetin." : "Manage user and admin product candidates, OCR evidence, and publication decisions in one queue."} state={state} onReload={reload} />
    {notice && <div className="success-banner compact-success">{notice}</div>}
    <div className="catalog-evidence-overview">
      <Panel title={locale === "tr" ? "Kuyruk sağlığı" : "Queue health"} description={locale === "tr" ? "Görünür sayfadaki işlem, risk ve atama yükü." : "Action, risk, and assignment workload on the visible page."}><ContributionQueueChart needsAction={needsActionCount} highRisk={highRiskCount} unassigned={unassignedCount} locale={locale} /></Panel>
      <div className="catalog-evidence-summary">
        <MetricCard label={locale === "tr" ? "Eşleşen kayıt" : "Matching cases"} value={String(data?.totalElements ?? 0)} hint={locale === "tr" ? "Sunucu filtrelerinin toplamı" : "Current server-side filter"} />
        <MetricCard label={locale === "tr" ? "Görünür sayfa" : "Visible page"} value={String(rows.length)} hint={`${page + 1} / ${Math.max(data?.totalPages ?? 1, 1)}`} />
      </div>
    </div>
    <div className="contribution-control-grid">
      {canWrite && <CollapsiblePanel title={locale === "tr" ? "Yeni ürün adayı oluştur" : "Create product candidate"} description={locale === "tr" ? "Barkod veya kanıtla yeni bir inceleme kaydı başlatın." : "Start a new review case from a barcode or evidence."} open={createOpen} onToggle={() => setCreateOpen((current) => !current)}>
        <ManualProductIntakeForm canWrite={canWrite} onCreated={reload} onError={onError} />
      </CollapsiblePanel>}
      <CollapsiblePanel title={locale === "tr" ? "Kuyruğu filtrele" : "Refine queue"} description={locale === "tr" ? "Durum, sorumluluk ve pazar bölgesine göre kayıtları daraltın." : "Narrow records by status, ownership, and market region."} open={filtersOpen} onToggle={() => setFiltersOpen((current) => !current)}>
        <div className="review-filter-grid contribution-filter-grid">
          <label>{locale === "tr" ? "Kuyruk" : "Queue"}<select value={queue} onChange={(event) => setQueue(event.target.value as QueueMode)}>{QUEUES.map((value) => <option key={value}>{value.replaceAll("_", " ")}</option>)}</select></label>
          <label className="user-filter-search">{locale === "tr" ? "Durum" : "Status"}<span><input value={status} onChange={(event) => setStatus(event.target.value.toUpperCase())} placeholder={locale === "tr" ? "Tüm durumlar" : "All statuses"} />{status && <button type="button" aria-label={locale === "tr" ? "Durumu temizle" : "Clear status"} onClick={() => setStatus("")}>×</button>}</span></label>
          <label>{locale === "tr" ? "Pazar bölgesi" : "Market region"}<select value={marketRegion} onChange={(event) => setMarketRegion(event.target.value)}><option value="">{locale === "tr" ? "Tüm pazarlar" : "All markets"}</option>{REGIONS.map((value) => <option key={value}>{value}</option>)}</select></label>
        </div>
      </CollapsiblePanel>
    </div>
    {activeFilters.length > 0 && <div className="product-review-active-filters">{activeFilters.map((value) => <span key={value}>{value.replaceAll("_", " ")}</span>)}</div>}
    <DataTable
      columns={locale === "tr" ? ["Kayıt", "Kaynak", "Pazar", "Risk", "Atama", "Güncellendi"] : ["Case", "Source", "Market", "Risk", "Assignment", "Updated"]}
      rows={rows.map((item) => [
        <div className="table-stack"><strong>#{item.id}</strong><span>{item.barcode ?? (locale === "tr" ? "Barkod yok" : "No barcode")}</span><small>{item.status ?? "-"}</small></div>,
        item.source ?? "-", item.marketRegion ?? (locale === "tr" ? "Belirtilmedi" : "Unspecified"), item.riskLevel ?? "-", item.assignedAdminEmail ?? (locale === "tr" ? "Atanmamış" : "Unassigned"), formatDate(item.updatedAt ?? item.createdAt)
      ])}
      rowData={rows}
      onRowClick={open}
      empty={locale === "tr" ? "Bu filtrelere uyan ürün kabul kaydı bulunamadı." : "No product-intake cases match these filters."}
    />
    <PaginationControls page={data?.page ?? page} pageSize={data?.size ?? size} totalElements={data?.totalElements ?? 0} totalPages={data?.totalPages ?? 1} first={data?.first ?? true} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={setSize} />
    {selected && <div className="modal-backdrop" role="presentation" onClick={() => { setSelected(null); onClearTarget?.(); }}>
      <section className="modal-card contribution-review-modal" role="dialog" aria-modal="true" aria-label={locale === "tr" ? "Ürün kabul incelemesi" : "Product intake review"} onClick={(event) => event.stopPropagation()}>
        <header className="modal-header"><div><span>{locale === "tr" ? "ÜRÜN KABUL" : "PRODUCT INTAKE"} #{selected.summary.id}</span><h2>{selected.summary.barcode ?? (locale === "tr" ? "Aday" : "Candidate")}</h2><p>{selected.summary.source} · {selected.summary.marketRegion ?? (locale === "tr" ? "Pazar belirtilmedi" : "Unspecified market")}</p></div><button className="modal-icon-close" type="button" onClick={() => { setSelected(null); onClearTarget?.(); }} aria-label={locale === "tr" ? "Ürün kabul incelemesini kapat" : "Close product intake review"}>×</button></header>
        <div className="contribution-review-body">
          <div className="contribution-review-details">
            <h3>{locale === "tr" ? "Gönderilen veri ve katalog" : "Submitted vs catalog"}</h3>
            <DataTable columns={locale === "tr" ? ["Uygula", "Alan", "Gönderilen", "Katalog", "Karşılaştırma"] : ["Apply", "Field", "Submitted", "Catalog", "Match"]} rows={(selected.fieldComparisons ?? []).map((item) => { const applyField = toApplyField(item.field); const selectable = canWrite && selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "UPDATE_EXISTING" && Boolean(applyField); return [selectable ? <input type="checkbox" aria-label={`${locale === "tr" ? "Uygula" : "Apply"} ${item.field}`} checked={applyFields.includes(applyField!)} onChange={(event) => setApplyFields((current) => event.target.checked ? [...current, applyField!] : current.filter((value) => value !== applyField))} /> : "-", item.field, formatField(item.submittedValue), formatField(item.catalogValue), item.equal ? (locale === "tr" ? "Eşleşiyor" : "Match") : item.highImpact ? (locale === "tr" ? "Yüksek etki" : "High impact") : (locale === "tr" ? "İncele" : "Review")]; })} empty={locale === "tr" ? "Karşılaştırılabilir alan bulunmuyor." : "No field comparison is available."} />
            {selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "UPDATE_EXISTING" && canWrite && <div className="review-apply-panel"><strong>Apply selected fields to the published product</strong><p>Only checked fields will change. Barcode, market and publication state are never edited here.</p><button className="primary-button" type="button" disabled={busy || applyFields.length === 0} onClick={applySelectedFields}>Apply {applyFields.length} selected field{applyFields.length === 1 ? "" : "s"}</button></div>}
            {(selected.warnings ?? []).map((warning) => <div className="warning-banner" key={warning}>{warning}</div>)}
            <h3>{locale === "tr" ? "OCR işlem geçmişi" : "OCR processing history"}</h3>
            {(selected.ocrRuns ?? []).map((run) => <OcrRunPanel key={run.id} run={run} locale={locale} isOwner={isOwner} />)}
            {!selected.ocrRuns?.length && <p className="muted-text">{locale === "tr" ? "Bu kayıt için kalıcı OCR karşılaştırma çalışması bulunmuyor." : "No persisted OCR comparison run is available for this case."}</p>}
            <h3>{locale === "tr" ? "Doğrulayıcı kaynak kanıtı" : "Corroborating source evidence"}</h3>
            <DataTable columns={locale === "tr" ? ["Alan", "Sağlayıcı", "Değer", "Temel", "Güven", "Gözlem"] : ["Field", "Provider", "Value", "Basis", "Confidence", "Observed"]} rows={(selected.corroboratingEvidence ?? []).map((item) => [item.field, item.provider, item.numericValue, item.basis, `${item.confidenceScore}%`, formatDate(item.observedAt)])} empty={locale === "tr" ? "Ek kaynak kanıtı bulunmuyor." : "No additional source evidence is available."} />
            <label>{locale === "tr" ? "İnceleme notu" : "Review note"}<textarea maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} /></label>
          </div>
          <div className="contribution-evidence-panel">
            <div className="evidence-panel-heading"><div><span>{locale === "tr" ? "ÖZEL KANIT" : "PRIVATE EVIDENCE"}</span><h3>{locale === "tr" ? "Gönderilen görseller" : "Submitted images"}</h3></div><small>{selected.evidence?.length ?? 0} {locale === "tr" ? "dosya" : "files"}</small></div>
            {canWrite && <div className="evidence-selector" role="tablist" aria-label="Submitted product images">{(selected.evidence ?? []).map((asset) => <button className={evidencePreview?.assetId === asset.assetId ? "evidence-tab active" : "evidence-tab"} type="button" role="tab" key={asset.assetId} disabled={!asset.available || evidenceLoadingId !== null} onClick={() => void viewEvidence(asset)}><strong>{formatEvidenceType(asset.assetType)}</strong><span>{evidenceLoadingId === asset.assetId ? "Loading..." : formatBytes(asset.sizeBytes)}</span></button>)}</div>}
            {canWrite && evidencePreview && <div className="evidence-image-frame"><img src={evidencePreview.signedUrl} alt={formatEvidenceType(evidencePreview.assetType) + " submitted for review"} /><span>{formatEvidenceType(evidencePreview.assetType)}</span></div>}
            {canWrite && !evidencePreview && Boolean(selected.evidence?.length) && <div className="evidence-placeholder"><strong>Select an image</strong><span>Open the package or nutrition label without leaving this review.</span></div>}
            {!selected.evidence?.length && <span>{locale === "tr" ? "Kanıt eklenmemiş." : "No evidence attached."}</span>}
            <small>{canWrite ? "Images use short-lived private links and are loaded only when selected." : "Private evidence requires owner or catalog-admin access."}</small>
            {canWrite && <div className="inline-actions"><button className="ghost-button" disabled={busy} type="button" onClick={() => void mutate("claim", undefined, "Case claimed.")}>Claim</button><button className="ghost-button" disabled={busy} type="button" onClick={() => void mutate("release", undefined, "Case released.")}>Release</button></div>}
            {isOwner && <label>Reassign to<input type="email" value={reassignEmail} onChange={(event) => setReassignEmail(event.target.value)} /></label>}
            {isOwner && <button className="ghost-button" disabled={busy || !reassignEmail.trim()} type="button" onClick={() => void mutate("reassign", { adminEmail: reassignEmail.trim() }, "Case reassigned.")}>Reassign</button>}
            {canWrite && <label>Existing food item ID<input inputMode="numeric" value={foodItemId} onChange={(event) => setFoodItemId(event.target.value.replace(/\D/g, ""))} /></label>}
            {canWrite && <button className="ghost-button" disabled={busy || !foodItemId} type="button" onClick={() => void mutate("attach-existing-product", { foodItemId: Number(foodItemId) }, "Existing product attached.")}>Attach existing product</button>}
          </div>
        </div>
        <footer className="modal-actions padded-actions">
          <button className="ghost-button" type="button" onClick={() => setSelected(null)}>{locale === "tr" ? "Kapat" : "Close"}</button>
          {canWrite && <button className="ghost-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("request-better-evidence", { note: note.trim() }, locale === "tr" ? "Daha iyi kanıt istendi." : "Better evidence requested.")}>{locale === "tr" ? "Daha iyi kanıt iste" : "Request better evidence"}</button>}
          {canWrite && <button className="ghost-button danger-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/reject", { note: note.trim() }, locale === "tr" ? "Kanıt reddedildi." : "Evidence rejected.")}>{locale === "tr" ? "Kanıtı reddet" : "Reject evidence"}</button>}
          {canWrite && selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "NEW_CANDIDATE" && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={publishSelectedCandidate}>{locale === "tr" ? "Adayı doğrula ve yayınla" : "Verify and publish candidate"}</button>}
          {canWrite && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/approve", { note: note.trim() }, locale === "tr" ? "Kanıt onaylandı; yayınlama ayrı bir işlemdir." : "Evidence approved; publication remains separate.")}>{locale === "tr" ? "Kanıtı onayla" : "Approve evidence"}</button>}
        </footer>
      </section>
    </div>}
  </div>;
}

function OcrRunPanel({ run, locale, isOwner }: { run: OcrRun; locale: "tr" | "en"; isOwner: boolean }) {
  const fields = Array.from(new Set([
    ...Object.keys(run.v3Fields ?? {}), ...Object.keys(run.v4Fields ?? {}),
    ...Object.keys(run.fallbackFields ?? {}), ...Object.keys(run.confirmedFields ?? {})
  ])).sort();
  const percent = (value?: number) => value === undefined ? "-" : `${Math.round(value * 100)}%`;
  return <section className="panel ocr-run-panel" aria-label={`${locale === "tr" ? "OCR çalışması" : "OCR run"} #${run.id}`}>
    <div className="panel-heading"><div><strong>#{run.id} · {run.parserVersion ?? "-"}</strong><small>{formatDate(run.createdAt)}</small></div>{isOwner && run.correlationId && <a className="ghost-button" href={`${sectionPaths.errors}?correlationId=${encodeURIComponent(run.correlationId)}`}>{locale === "tr" ? "Hata Merkezi" : "Error Center"}</a>}</div>
    <div className="review-workspace-summary ocr-run-metrics">
      <MetricCard label={locale === "tr" ? "Yerel ayrıştırıcı v3" : "Local parser v3"} value={percent(run.v3ExactMatchRate)} hint={run.v3BasisExact ? "Basis match" : "Basis review"} />
      <MetricCard label={locale === "tr" ? "Yerel ayrıştırıcı v4" : "Local parser v4"} value={percent(run.v4ExactMatchRate)} hint={run.v4BasisExact ? "Basis match" : "Basis review"} />
      <MetricCard label={locale === "tr" ? "Alternatif sağlayıcı" : "Fallback provider"} value={run.fallbackInvoked ? percent(run.fallbackExactMatchRate) : (locale === "tr" ? "Çalışmadı" : "Not invoked")} hint={run.model ?? (locale === "tr" ? "Model yok" : "No model")} />
      <MetricCard label={locale === "tr" ? "Süre / maliyet" : "Latency / cost"} value={`${run.latencyMs ?? 0} ms`} hint={`$${(run.estimatedCostUsd ?? 0).toFixed(4)}`} />
    </div>
    <DataTable columns={[locale === "tr" ? "Alan" : "Field", "v3", "v4", locale === "tr" ? "Alternatif" : "Fallback", locale === "tr" ? "Kullanıcı onayı" : "Confirmed"]} rows={fields.map((field) => [field, formatField(run.v3Fields?.[field]), formatField(run.v4Fields?.[field]), formatField(run.fallbackFields?.[field]), formatField(run.confirmedFields?.[field])])} empty={locale === "tr" ? "Karşılaştırılabilir OCR alanı yok." : "No comparable OCR fields."} />
    {run.correlationId && <small>Correlation ID: {run.correlationId}</small>}
  </section>;
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
