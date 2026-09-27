import { useEffect, useState } from "react";
import { formatRequestError, request, requestBlob } from "./api";
import { CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "./AdminPrimitives";
import type { AdminAccessProfile } from "./types";
import { ManualProductIntakeForm } from "./ManualProductIntakeForm";
import { useAdminLocale } from "./admin/locale";
import { sectionPaths } from "./admin/navigation";
import { Badge, ConfirmDialog, type AdminTargetContext } from "./admin/shared";
import { ContributionQueueChart } from "./CatalogWorkspaceCharts";
import { CatalogProductFinder } from "./CatalogProductFinder";
import { browserImageObjectUrl } from "./admin/imagePreview";

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
type QueueMode = "ALL" | "ACTIVE" | "COMPLETED" | "APPROVED" | "MY_QUEUE" | "UNASSIGNED" | "NEEDS_ACTION" | "HIGH_RISK" | "OVERDUE";
type IntakeConfirmation = { type: "publish" } | { type: "apply"; fields: string[]; highImpactLabels: string[] };

const REVIEW_NOTE_TEMPLATES = [
  { key: "thanks-tr", label: "Teşekkür · TR", tone: "positive", text: "Katkınız için teşekkür ederiz. Gönderdiğiniz ürün bilgileri ve kanıtlar incelendi; katkınız onaylandı." },
  { key: "thanks-en", label: "Thank you · EN", tone: "positive", text: "Thank you for your contribution. The product information and evidence you submitted were reviewed, and your contribution was approved." },
  { key: "reject-tr", label: "Ret · TR", tone: "negative", text: "Katkınız için teşekkür ederiz. Gönderdiğiniz ürün bilgileri mevcut kanıtlarla doğrulanamadığı için katkınız bu aşamada onaylanamadı." },
  { key: "reject-en", label: "Rejection · EN", tone: "negative", text: "Thank you for your contribution. We could not verify the submitted product information against the available evidence, so the contribution could not be approved at this time." }
] as const;

const QUEUES: QueueMode[] = ["ACTIVE", "APPROVED", "COMPLETED", "MY_QUEUE", "UNASSIGNED", "NEEDS_ACTION", "HIGH_RISK", "OVERDUE", "ALL"];
const STATUSES = ["SUBMITTED", "IN_REVIEW", "NEEDS_SUBMITTER_ACTION", "APPROVED", "APPLIED", "REJECTED", "WITHDRAWN", "EXPIRED"];
const TERMINAL_STATUSES = new Set(["APPLIED", "REJECTED", "WITHDRAWN", "EXPIRED"]);
const REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];

export function ProductIntakeView({ accessProfile, onError, targetContext, onClearTarget }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { locale } = useAdminLocale();
  const [queue, setQueue] = useState<QueueMode>("ACTIVE");
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
  const [evidencePreviewUrl, setEvidencePreviewUrl] = useState<string | null>(null);
  const [evidenceLoadingId, setEvidenceLoadingId] = useState<number | null>(null);
  const [confirmation, setConfirmation] = useState<IntakeConfirmation | null>(null);
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
  useEffect(() => () => { if (evidencePreviewUrl) URL.revokeObjectURL(evidencePreviewUrl); }, [evidencePreviewUrl]);
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
      setEvidencePreviewUrl(null);
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
    if (highImpact.length > 0) {
      setConfirmation({ type: "apply", fields: [...applyFields], highImpactLabels: highImpact.map((item) => item.field) });
      return;
    }
    void applyConfirmedFields(applyFields);
  }

  function publishSelectedCandidate() {
    if (!note.trim()) return;
    setConfirmation({ type: "publish" });
  }

  async function applyConfirmedFields(fields: string[]) {
    setConfirmation(null);
    await mutate("apply-existing", { fields, confirmed: true }, locale === "tr" ? "Seçilen alanlar mevcut ürüne uygulandı." : "Selected fields applied to the existing product.");
  }

  async function publishConfirmedCandidate() {
    setConfirmation(null);
    await mutate("publish-candidate", { note: note.trim(), confirmed: true }, locale === "tr" ? "Aday doğrulandı ve yayınlandı." : "Candidate verified and published.");
  }
  async function viewEvidence(asset: Evidence) {
    setEvidenceLoadingId(asset.assetId);
    try {
      const read = await request<EvidenceRead>("/api/v1/admin/products/review-cases/assets/" + asset.assetId + "/evidence-url");
      const blob = await requestBlob("/api/v1/admin/products/review-cases/assets/" + asset.assetId + "/evidence");
      const previewUrl = await browserImageObjectUrl(blob, read.contentType);
      setEvidencePreview(read);
      setEvidencePreviewUrl(previewUrl);
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setEvidenceLoadingId(null);
    }
  }

  const rows = data?.content ?? [];
  const needsActionCount = rows.filter((item) => ["SUBMITTED", "IN_REVIEW", "APPROVED"].includes(item.status ?? "")).length;
  const highRiskCount = rows.filter((item) => item.riskLevel === "HIGH").length;
  const unassignedCount = rows.filter((item) => !TERMINAL_STATUSES.has(item.status ?? "") && !item.assignedAdminEmail).length;
  const activeFilters = [queue !== "ALL" ? queue : "", status, marketRegion].filter(Boolean);
  const selectedTerminal = TERMINAL_STATUSES.has(selected?.summary.status ?? "");
  const selectedEvidenceReviewable = ["SUBMITTED", "IN_REVIEW"].includes(selected?.summary.status ?? "");
  const selectedAssignedToMe = Boolean(selected?.summary.assignedAdminEmail && selected.summary.assignedAdminEmail.toLowerCase() === accessProfile?.email?.toLowerCase());
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
          <label>{locale === "tr" ? "Kuyruk" : "Queue"}<select value={queue} onChange={(event) => { setQueue(event.target.value as QueueMode); setStatus(""); }}>{QUEUES.map((value) => <option key={value} value={value}>{queueLabel(value, locale)}</option>)}</select></label>
          <label>{locale === "tr" ? "Durum" : "Status"}<select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">{locale === "tr" ? "Tüm durumlar" : "All statuses"}</option>{STATUSES.map((value) => <option key={value} value={value}>{statusLabel(value, locale)}</option>)}</select></label>
          <label>{locale === "tr" ? "Pazar bölgesi" : "Market region"}<select value={marketRegion} onChange={(event) => setMarketRegion(event.target.value)}><option value="">{locale === "tr" ? "Tüm pazarlar" : "All markets"}</option>{REGIONS.map((value) => <option key={value}>{value}</option>)}</select></label>
        </div>
      </CollapsiblePanel>
    </div>
    {activeFilters.length > 0 && <div className="product-review-active-filters">{activeFilters.map((value) => <span key={value}>{value.replaceAll("_", " ")}</span>)}</div>}
    <DataTable
      columns={locale === "tr" ? ["Kayıt", "Kaynak", "Pazar", "Risk", "Atama", "Güncellendi"] : ["Case", "Source", "Market", "Risk", "Assignment", "Updated"]}
      rows={rows.map((item) => [
        <div className="table-stack"><strong>#{item.id}</strong><span>{item.barcode ?? (locale === "tr" ? "Barkod yok" : "No barcode")}</span><Badge value={statusLabel(item.status ?? "", locale)} tone={statusTone(item.status)} /></div>,
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
        <div className="intake-review-summary" aria-label={locale === "tr" ? "İnceleme özeti" : "Review summary"}>
          <SummaryItem label={locale === "tr" ? "Durum" : "Status"} value={selected.summary.status} tone={selected.summary.status === "APPROVED" ? "success" : "accent"} />
          <SummaryItem label={locale === "tr" ? "Çözüm" : "Resolution"} value={selected.summary.resolutionMode} />
          <SummaryItem label={locale === "tr" ? "Risk" : "Risk"} value={selected.summary.riskLevel} tone={selected.summary.riskLevel === "HIGH" ? "danger" : undefined} />
          <SummaryItem label={locale === "tr" ? "Sorumlu" : "Assignee"} value={selected.summary.assignedAdminEmail ?? (locale === "tr" ? "Atanmamış" : "Unassigned")} />
          <SummaryItem label={locale === "tr" ? "Katalog kaydı" : "Catalog record"} value={selected.linkedFoodItemId ? `#${selected.linkedFoodItemId}` : (locale === "tr" ? "Bağlı değil" : "Not linked")} />
        </div>
        <div className="contribution-review-body">
          <div className="contribution-review-details">
            <FieldComparisonReview comparisons={selected.fieldComparisons ?? []} locale={locale} canSelect={canWrite && selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "UPDATE_EXISTING"} selectedFields={applyFields} onSelectionChange={setApplyFields} />
            {selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "UPDATE_EXISTING" && canWrite && <div className="review-apply-panel"><strong>Apply selected fields to the published product</strong><p>Only checked fields will change. Barcode, market and publication state are never edited here.</p><button className="primary-button" type="button" disabled={busy || applyFields.length === 0} onClick={applySelectedFields}>Apply {applyFields.length} selected field{applyFields.length === 1 ? "" : "s"}</button></div>}
            {(selected.warnings ?? []).map((warning) => <div className="warning-banner" key={warning}>{warning}</div>)}
            <h3>{locale === "tr" ? "OCR işlem geçmişi" : "OCR processing history"}</h3>
            {(selected.ocrRuns ?? []).map((run) => <OcrRunPanel key={run.id} run={run} locale={locale} isOwner={isOwner} />)}
            {!selected.ocrRuns?.length && <p className="muted-text">{locale === "tr" ? "Bu kayıt için kalıcı OCR karşılaştırma çalışması bulunmuyor." : "No persisted OCR comparison run is available for this case."}</p>}
            <h3>{locale === "tr" ? "Doğrulayıcı kaynak kanıtı" : "Corroborating source evidence"}</h3>
            <DataTable columns={locale === "tr" ? ["Alan", "Sağlayıcı", "Değer", "Temel", "Güven", "Gözlem"] : ["Field", "Provider", "Value", "Basis", "Confidence", "Observed"]} rows={(selected.corroboratingEvidence ?? []).map((item) => [item.field, item.provider, item.numericValue, item.basis, `${item.confidenceScore}%`, formatDate(item.observedAt)])} empty={locale === "tr" ? "Ek kaynak kanıtı bulunmuyor." : "No additional source evidence is available."} />
            {selectedTerminal && <div className="success-banner compact-success">{locale === "tr" ? "Bu inceleme tamamlandı. Kayıt geçmiş amacıyla salt okunur gösteriliyor." : "This review is complete. The record is shown read-only for history."}</div>}
            <div className="intake-review-note-editor">
              <div className="intake-review-note-heading"><div><span>{locale === "tr" ? "HAZIR YANITLAR" : "QUICK REPLIES"}</span><strong>{locale === "tr" ? "Kullanıcıya gönderilecek mesaj" : "Message shown to the contributor"}</strong></div><small>{locale === "tr" ? "Bir şablon seçin, ardından gerekirse metni düzenleyin." : "Choose a template, then edit it if needed."}</small></div>
              <div className="intake-review-note-templates" aria-label={locale === "tr" ? "Hazır inceleme notları" : "Review note templates"}>
                {REVIEW_NOTE_TEMPLATES.map((template) => <button className={`ghost-button ${template.tone}`} disabled={selectedTerminal} key={template.key} type="button" onClick={() => setNote(template.text)}>{template.label}</button>)}
              </div>
              <label>{locale === "tr" ? "İnceleme notu" : "Review note"}<textarea disabled={selectedTerminal} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} placeholder={locale === "tr" ? "Kullanıcıya gösterilecek yanıtı yazın veya yukarıdan hazır metin seçin." : "Write the response shown to the contributor or select a template above."} /></label>
              <div className="intake-review-note-meta"><span>{note.length}/1000</span><small>{locale === "tr" ? "Bu metin kullanıcıya mesaj olarak gösterilir." : "This text is shown to the contributor as a message."}</small></div>
            </div>
          </div>
          <div className="contribution-evidence-panel">
            <div className="evidence-panel-heading"><div><span>{locale === "tr" ? "ÖZEL KANIT" : "PRIVATE EVIDENCE"}</span><h3>{locale === "tr" ? "Gönderilen görseller" : "Submitted images"}</h3></div><small>{selected.evidence?.length ?? 0} {locale === "tr" ? "dosya" : "files"}</small></div>
            {canWrite && <div className="evidence-selector" role="tablist" aria-label="Submitted product images">{(selected.evidence ?? []).map((asset) => <button className={evidencePreview?.assetId === asset.assetId ? "evidence-tab active" : "evidence-tab"} type="button" role="tab" key={asset.assetId} disabled={!asset.available || evidenceLoadingId !== null} onClick={() => void viewEvidence(asset)}><strong>{formatEvidenceType(asset.assetType)}</strong><span>{evidenceLoadingId === asset.assetId ? "Loading..." : formatBytes(asset.sizeBytes)}</span></button>)}</div>}
            {canWrite && evidencePreview && evidencePreviewUrl && <div className="evidence-image-frame"><img src={evidencePreviewUrl} alt={formatEvidenceType(evidencePreview.assetType) + " submitted for review"} /><span>{formatEvidenceType(evidencePreview.assetType)}</span></div>}
            {canWrite && !evidencePreview && Boolean(selected.evidence?.length) && <div className="evidence-placeholder"><strong>Select an image</strong><span>Open the package or nutrition label without leaving this review.</span></div>}
            {!selected.evidence?.length && <span>{locale === "tr" ? "Kanıt eklenmemiş." : "No evidence attached."}</span>}
            <small>{canWrite ? "Images use short-lived private links and are loaded only when selected." : "Private evidence requires owner or catalog-admin access."}</small>
            {canWrite && !selectedTerminal && <section className="intake-side-action"><div><span>{locale === "tr" ? "SORUMLULUK" : "OWNERSHIP"}</span><strong>{locale === "tr" ? "İncelemeyi yönet" : "Manage review"}</strong></div><div className="inline-actions"><button className="ghost-button" disabled={busy || Boolean(selected.summary.assignedAdminEmail && !selectedAssignedToMe)} type="button" onClick={() => void mutate("claim", undefined, locale === "tr" ? "İnceleme üzerinize alındı." : "Case claimed.")}>{selectedAssignedToMe ? (locale === "tr" ? "Üzerinizde" : "Assigned to you") : (locale === "tr" ? "Üzerime al" : "Claim")}</button><button className="ghost-button" disabled={busy || !selected.summary.assignedAdminEmail || (!isOwner && !selectedAssignedToMe)} type="button" onClick={() => void mutate("release", undefined, locale === "tr" ? "Atama kaldırıldı." : "Case released.")}>{locale === "tr" ? "Serbest bırak" : "Release"}</button></div>{isOwner && <div className="compact-action-form"><label>{locale === "tr" ? "Başka katalog yöneticisine ata" : "Reassign to catalog admin"}<input type="email" value={reassignEmail} onChange={(event) => setReassignEmail(event.target.value)} placeholder="admin@example.com" /></label><button className="ghost-button" disabled={busy || !reassignEmail.trim()} type="button" onClick={() => void mutate("reassign", { adminEmail: reassignEmail.trim() }, locale === "tr" ? "İnceleme yeniden atandı." : "Case reassigned.")}>{locale === "tr" ? "Ata" : "Reassign"}</button></div>}</section>}
            {canWrite && !selectedTerminal && <section className="intake-side-action intake-catalog-linker"><div><span>{locale === "tr" ? "KATALOG BAĞLANTISI" : "CATALOG LINK"}</span><strong>{locale === "tr" ? "Mevcut ürünü ara ve doğrula" : "Search and verify the existing product"}</strong><small>{locale === "tr" ? "Tüm veritabanında ad, marka, barkod veya ürün kimliğiyle arayın; doğru ürünü seçmeden bağlantı kurulmaz." : "Search the full database by name, brand, barcode or product ID. A link is created only after selecting a result."}</small></div><CatalogProductFinder compact initialProductId={selected.linkedFoodItemId} onError={onError} onSelect={(product) => setFoodItemId(product.id ? String(product.id) : "")} onSelectionClear={() => setFoodItemId("")} />{foodItemId && <div className="catalog-link-confirm"><span>{locale === "tr" ? "Seçilen katalog kaydı" : "Selected catalog record"}</span><strong>#{foodItemId}</strong><button className="primary-button" disabled={busy} type="button" onClick={() => void mutate("attach-existing-product", { foodItemId: Number(foodItemId) }, locale === "tr" ? "Mevcut ürün bağlandı." : "Existing product attached.")}>{locale === "tr" ? "Seçilen ürüne bağla" : "Attach selected product"}</button></div>}</section>}
          </div>
        </div>
        <footer className="modal-actions padded-actions">
          <button className="ghost-button" type="button" onClick={() => setSelected(null)}>{locale === "tr" ? "Kapat" : "Close"}</button>
          {canWrite && selectedEvidenceReviewable && <button className="ghost-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("request-better-evidence", { note: note.trim() }, locale === "tr" ? "Daha iyi kanıt istendi." : "Better evidence requested.")}>{locale === "tr" ? "Daha iyi kanıt iste" : "Request better evidence"}</button>}
          {canWrite && selectedEvidenceReviewable && <button className="ghost-button danger-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/reject", { note: note.trim() }, locale === "tr" ? "Kanıt reddedildi." : "Evidence rejected.")}>{locale === "tr" ? "Kanıtı reddet" : "Reject evidence"}</button>}
          {canWrite && selected.summary.status === "APPROVED" && selected.summary.resolutionMode === "NEW_CANDIDATE" && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={publishSelectedCandidate}>{locale === "tr" ? "Adayı doğrula ve yayınla" : "Verify and publish candidate"}</button>}
          {canWrite && selectedEvidenceReviewable && <button className="primary-button" disabled={busy || !note.trim()} type="button" onClick={() => void mutate("evidence/approve", { note: note.trim() }, locale === "tr" ? "Kanıt onaylandı; yayınlama ayrı bir işlemdir." : "Evidence approved; publication remains separate.")}>{locale === "tr" ? "Kanıtı onayla" : "Approve evidence"}</button>}
        </footer>
      </section>
    </div>}
    {selected && confirmation?.type === "publish" && <ConfirmDialog
      title={locale === "tr" ? "Aday yayınlansın mı?" : "Publish this candidate?"}
      message={locale === "tr" ? "Doğrulanmış aday herkese açık kataloğa yayınlanacak ve inceleme notunuz kullanıcının bildiriminde gösterilecek." : "The verified candidate will be published to the public catalog and your review note will be shown in the contributor's notification."}
      confirmLabel={locale === "tr" ? "Doğrula ve yayınla" : "Verify and publish"}
      busy={busy}
      onCancel={() => setConfirmation(null)}
      onConfirm={() => void publishConfirmedCandidate()}
    />}
    {selected && confirmation?.type === "apply" && <ConfirmDialog
      title={locale === "tr" ? "Yüksek etkili alanlar uygulansın mı?" : "Apply high-impact fields?"}
      message={locale === "tr" ? `${confirmation.highImpactLabels.join(", ")} alanları katalog ürününü önemli ölçüde değiştirecek. Tüm kanıtları kontrol ettiğinizden emin olun.` : `${confirmation.highImpactLabels.join(", ")} will materially change the catalog product. Confirm that all evidence has been checked.`}
      confirmLabel={locale === "tr" ? "Alanları uygula" : "Apply fields"}
      busy={busy}
      onCancel={() => setConfirmation(null)}
      onConfirm={() => void applyConfirmedFields(confirmation.fields)}
    />}
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

type FieldComparison = NonNullable<IntakeDetail["fieldComparisons"]>[number];

function SummaryItem({ label, value, tone }: { label: string; value?: string; tone?: "accent" | "success" | "danger" }) {
  return <div className={`intake-summary-item${tone ? ` ${tone}` : ""}`}><span>{label}</span><strong>{value?.replaceAll("_", " ") || "-"}</strong></div>;
}

function FieldComparisonReview({ comparisons, locale, canSelect, selectedFields, onSelectionChange }: { comparisons: FieldComparison[]; locale: "tr" | "en"; canSelect: boolean; selectedFields: string[]; onSelectionChange: (fields: string[]) => void }) {
  const groups = ["identity", "nutrition", "serving", "metadata"] as const;
  const visible = comparisons.filter((item) => item.field !== "originalInput");
  const raw = comparisons.find((item) => item.field === "originalInput");
  const differenceCount = visible.filter((item) => !item.equal).length;
  const highImpactCount = visible.filter((item) => !item.equal && item.highImpact).length;
  return <section className="field-comparison-workspace">
    <div className="comparison-heading">
      <div><span>{locale === "tr" ? "ALAN KARŞILAŞTIRMASI" : "FIELD COMPARISON"}</span><h3>{locale === "tr" ? "Gönderilen değerler ve katalog kaydı" : "Submitted values and catalog record"}</h3><p>{locale === "tr" ? "Her alanı aynı satırda karşılaştırın. Farklı alanlar renk ve durum etiketiyle belirtilir." : "Compare both sources on one row. Differences are marked with a status and color."}</p></div>
      <div className="comparison-counts"><span>{differenceCount} {locale === "tr" ? "fark" : "differences"}</span>{highImpactCount > 0 && <span className="danger">{highImpactCount} {locale === "tr" ? "yüksek etki" : "high impact"}</span>}</div>
    </div>
    <div className="comparison-column-guide" aria-hidden="true"><span>{locale === "tr" ? "Alan" : "Field"}</span><span>{locale === "tr" ? "Gönderilen" : "Submitted"}</span><span>{locale === "tr" ? "Katalog" : "Catalog"}</span><span>{locale === "tr" ? "Sonuç" : "Result"}</span></div>
    {groups.map((group) => {
      const items = visible.filter((item) => comparisonGroup(item.field) === group);
      if (!items.length) return null;
      return <section className="comparison-group" key={group}><h4>{comparisonGroupLabel(group, locale)}</h4><div>{items.map((item) => {
        const applyField = toApplyField(item.field);
        const selectable = canSelect && Boolean(applyField);
        const checked = Boolean(applyField && selectedFields.includes(applyField));
        return <div className={`comparison-row${item.equal ? " equal" : item.highImpact ? " high-impact" : " different"}`} key={item.field}>
          <div className="comparison-field">{selectable && <input type="checkbox" aria-label={`${locale === "tr" ? "Uygula" : "Apply"} ${fieldLabel(item.field, locale)}`} checked={checked} onChange={(event) => onSelectionChange(event.target.checked ? [...selectedFields, applyField!] : selectedFields.filter((value) => value !== applyField))} />}<strong>{fieldLabel(item.field, locale)}</strong><small>{item.field}</small></div>
          <ComparisonValue value={item.submittedValue} emptyLabel={locale === "tr" ? "Gönderilmedi" : "Not submitted"} />
          <ComparisonValue value={item.catalogValue} emptyLabel={locale === "tr" ? "Katalogda yok" : "Not in catalog"} />
          <span className="comparison-status">{item.equal ? (locale === "tr" ? "Eşleşiyor" : "Match") : item.highImpact ? (locale === "tr" ? "Yüksek etki" : "High impact") : (locale === "tr" ? "Farklı" : "Different")}</span>
        </div>;
      })}</div></section>;
    })}
    {!visible.length && <p className="muted-text">{locale === "tr" ? "Karşılaştırılabilir alan bulunmuyor." : "No field comparison is available."}</p>}
    {raw && <details className="raw-submission"><summary>{locale === "tr" ? "Ham OCR girdisini görüntüle" : "View raw OCR input"}</summary><pre>{prettyField(raw.submittedValue)}</pre></details>}
  </section>;
}

function ComparisonValue({ value, emptyLabel }: { value: unknown; emptyLabel: string }) {
  const empty = value === null || value === undefined || value === "";
  return <div className={`comparison-value${empty ? " empty" : ""}`}>{empty ? emptyLabel : formatField(value)}</div>;
}

function comparisonGroup(field: string): "identity" | "nutrition" | "serving" | "metadata" {
  const normalized = field.toLowerCase();
  if (["productname", "brand", "barcode"].includes(normalized)) return "identity";
  if (["calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "saturatedfat", "transfat", "sugaralcohol", "potassium", "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc", "vitamind", "vitamine", "vitaminb12"].includes(normalized)) return "nutrition";
  if (normalized.includes("serving") || normalized.includes("basis")) return "serving";
  return "metadata";
}

function comparisonGroupLabel(group: "identity" | "nutrition" | "serving" | "metadata", locale: "tr" | "en") {
  const labels = locale === "tr" ? { identity: "Ürün kimliği", nutrition: "Besin değerleri", serving: "Porsiyon ve ölçüm", metadata: "Kayıt bilgileri" } : { identity: "Product identity", nutrition: "Nutrition", serving: "Serving and measurement", metadata: "Record details" };
  return labels[group];
}

function fieldLabel(field: string, locale: "tr" | "en") {
  const tr: Record<string, string> = { productName: "Ürün adı", brand: "Marka", barcode: "Barkod", calories: "Kalori", protein: "Protein", fat: "Yağ", carbs: "Karbonhidrat", fiber: "Lif", sugar: "Şeker", sodium: "Sodyum", saturatedFat: "Doymuş yağ", transFat: "Trans yağ", sugarAlcohol: "Şeker alkolü", potassium: "Potasyum", cholesterol: "Kolesterol", calcium: "Kalsiyum", iron: "Demir", magnesium: "Magnezyum", zinc: "Çinko", vitaminA: "A vitamini", vitaminC: "C vitamini", vitaminD: "D vitamini", vitaminE: "E vitamini", vitaminB12: "B12 vitamini", servingSizeGrams: "Porsiyon gramı", servingUnit: "Porsiyon birimi", basis: "Besin değeri temeli", nutritionBasis: "Besin değeri kaynağı", nutritionReferenceUnit: "Referans ölçü", catalogType: "Katalog tipi", preparationState: "Hazırlama durumu", ingredientsText: "İçindekiler", allergens: "Alerjenler", sourceCategoryTags: "Kaynak kategori etiketleri", adminSourceName: "Kaynak adı", adminSourceUrl: "Kaynak bağlantısı", adminCreationNote: "Oluşturma notu", entryMethod: "Giriş yöntemi" };
  if (locale === "tr" && tr[field]) return tr[field];
  return field.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ").replace(/^./, (value) => value.toUpperCase());
}

function prettyField(value: unknown) {
  if (typeof value !== "string") return JSON.stringify(value, null, 2);
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

function toApplyField(field: string) {
  const normalized = field.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase();
  const supported = new Set(["PRODUCT_NAME", "BRAND", "CALORIES", "PROTEIN", "FAT", "CARBS", "FIBER", "SUGAR", "SODIUM"]);
  return supported.has(normalized) ? normalized : null;
}
function queueLabel(value: QueueMode, locale: "tr" | "en") {
  const labels: Record<QueueMode, [string, string]> = {
    ACTIVE: ["Aktif incelemeler", "Active reviews"], APPROVED: ["Onaylandı, uygulama bekliyor", "Approved, awaiting application"],
    COMPLETED: ["Tamamlananlar", "Completed"], MY_QUEUE: ["Benim kuyruğum", "My queue"],
    UNASSIGNED: ["Atanmamış", "Unassigned"], NEEDS_ACTION: ["Kullanıcıdan işlem bekliyor", "Awaiting contributor"],
    HIGH_RISK: ["Yüksek risk", "High risk"], OVERDUE: ["Süresi geçen", "Overdue"], ALL: ["Tüm kayıtlar", "All records"]
  };
  return labels[value][locale === "tr" ? 0 : 1];
}
function statusLabel(value: string, locale: "tr" | "en") {
  const tr: Record<string, string> = { SUBMITTED: "Gönderildi", IN_REVIEW: "İnceleniyor", NEEDS_SUBMITTER_ACTION: "Kullanıcıdan işlem bekliyor", APPROVED: "Onaylandı · uygulama bekliyor", APPLIED: "Tamamlandı · uygulandı", REJECTED: "Tamamlandı · reddedildi", WITHDRAWN: "Geri çekildi", EXPIRED: "Süresi doldu" };
  return locale === "tr" ? (tr[value] ?? value.replaceAll("_", " ")) : value.replaceAll("_", " ");
}
function statusTone(value?: string): "good" | "warn" | "danger" | "neutral" {
  if (value === "APPLIED") return "good";
  if (["REJECTED", "EXPIRED", "WITHDRAWN"].includes(value ?? "")) return "danger";
  if (value === "APPROVED") return "good";
  if (["SUBMITTED", "IN_REVIEW", "NEEDS_SUBMITTER_ACTION"].includes(value ?? "")) return "warn";
  return "neutral";
}
function formatDate(value?: string) {
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
