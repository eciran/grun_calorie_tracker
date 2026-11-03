import { useEffect, useState } from "react";

import { formatRequestError, request } from "../api";
import { CollapsiblePanel, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";
import { useAdminLocale } from "../admin/locale";
import { Badge, formatValue, useEndpoint } from "../admin/shared";
import type {
  BrandedDuplicateDecision,
  BrandedProductDuplicateCandidate,
  BrandedProductDuplicateGroup,
  BrandedProductDuplicateGroupPage
} from "../types";

type DecisionDraft = {
  group: BrandedProductDuplicateGroup;
  decision: BrandedDuplicateDecision | "CLEAR";
  survivorProductId?: number;
  reason: string;
};

type CollapseDraft = {
  group: BrandedProductDuplicateGroup;
  action: "APPLY" | "REVERT";
  reason: string;
};

type FamilyPreview = {
  eligible?: boolean;
  errors?: string[];
  warnings?: string[];
  previewFingerprint?: string;
};

type FamilyDraft = {
  group: BrandedProductDuplicateGroup;
  familyKey: string;
  displayName: string;
  evidenceReason: string;
  representativeProductId?: number;
  preview?: FamilyPreview;
  acknowledgedWarnings: string[];
};

type DuplicateBatchJob = {
  id?: number;
  status?: string;
  query?: string;
  totalGroups?: number;
  eligibleGroups?: number;
  processedGroups?: number;
  appliedGroups?: number;
  skippedGroups?: number;
  failedGroups?: number;
  progressPercent?: number;
  lastError?: string;
  updatedAt?: string;
};

export function BrandedDuplicateWorkspace({
  onError,
  canManage
}: {
  onError: (message: string | null) => void;
  canManage: boolean;
}) {
  const { locale } = useAdminLocale();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [priorityOnly, setPriorityOnly] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [draft, setDraft] = useState<DecisionDraft | null>(null);
  const [collapseDraft, setCollapseDraft] = useState<CollapseDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [operationNotice, setOperationNotice] = useState<string | null>(null);
  const [selectedSafeGroups, setSelectedSafeGroups] = useState<string[]>([]);
  const [familyDraft, setFamilyDraft] = useState<FamilyDraft | null>(null);
  const [batchJobs, setBatchJobs] = useState<DuplicateBatchJob[]>([]);

  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (debouncedQuery) params.set("query", debouncedQuery);
  if (priorityOnly) params.set("variantSignal", "false");
  const { data, state, reload } = useEndpoint<BrandedProductDuplicateGroupPage>(
    `/api/v1/admin/products/duplicates/branded?${params.toString()}`,
    onError,
    true,
    60000
  );
  const groups = data?.content ?? [];
  const decided = groups.filter((group) => group.storedDecision && !group.decisionStale).length;
  const stale = groups.filter((group) => group.decisionStale).length;

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedQuery(query.trim()), 400);
    return () => window.clearTimeout(timeout);
  }, [query]);

  useEffect(() => setPage(0), [debouncedQuery, priorityOnly, pageSize]);

  async function loadBatchJobs() {
    try { setBatchJobs(await request<DuplicateBatchJob[]>("/api/v1/admin/products/duplicates/branded/batch-jobs", { bypassCache: true })); }
    catch (error) { onError(formatRequestError(error)); }
  }

  useEffect(() => {
    void loadBatchJobs();
    const active = batchJobs.some(job => ["QUEUED", "DISCOVERING", "RUNNING", "PAUSE_REQUESTED"].includes(job.status ?? ""));
    if (!active) return;
    const timer = window.setInterval(() => void loadBatchJobs(), 2000);
    return () => window.clearInterval(timer);
  }, [batchJobs.some(job => ["QUEUED", "DISCOVERING", "RUNNING", "PAUSE_REQUESTED"].includes(job.status ?? ""))]);

  function startDecision(
    group: BrandedProductDuplicateGroup,
    decision: BrandedDuplicateDecision | "CLEAR",
    survivorProductId?: number
  ) {
    setDraft({ group, decision, survivorProductId, reason: "" });
  }

  async function saveDecision() {
    if (!draft || draft.reason.trim().length < 10 || !draft.group.brandKey || !draft.group.nameKey) return;
    setSaving(true);
    onError(null);
    try {
      if (draft.decision === "CLEAR") {
        const params = new URLSearchParams({
          brandKey: draft.group.brandKey,
          nameKey: draft.group.nameKey,
          reason: draft.reason.trim()
        });
        await request(`/api/v1/admin/products/duplicates/branded/decision?${params.toString()}`, { method: "DELETE", timeoutMs: 60000 });
      } else {
        await request("/api/v1/admin/products/duplicates/branded/decision", {
          method: "POST",
          body: {
            brandKey: draft.group.brandKey,
            nameKey: draft.group.nameKey,
            decision: draft.decision,
            survivorProductId: draft.survivorProductId,
            reason: draft.reason.trim(),
            candidateFingerprint: draft.group.candidateFingerprint
          },
          timeoutMs: 60000
        });
      }
      setDraft(null);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  async function saveCollapse() {
    if (!collapseDraft || collapseDraft.reason.trim().length < 10
      || !collapseDraft.group.brandKey || !collapseDraft.group.nameKey
      || !collapseDraft.group.candidateFingerprint) return;
    setSaving(true);
    onError(null);
    try {
      if (collapseDraft.action === "REVERT") {
        const params = new URLSearchParams({
          brandKey: collapseDraft.group.brandKey,
          nameKey: collapseDraft.group.nameKey,
          reason: collapseDraft.reason.trim()
        });
        await request(`/api/v1/admin/products/duplicates/branded/search-collapse?${params.toString()}`, { method: "DELETE", timeoutMs: 60000 });
      } else {
        await request("/api/v1/admin/products/duplicates/branded/search-collapse", {
          method: "POST",
          body: {
            brandKey: collapseDraft.group.brandKey,
            nameKey: collapseDraft.group.nameKey,
            candidateFingerprint: collapseDraft.group.candidateFingerprint,
            reason: collapseDraft.reason.trim()
          },
          timeoutMs: 60000
        });
      }
      setCollapseDraft(null);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  async function applySurvivor(group: BrandedProductDuplicateGroup, survivorProductId?: number) {
    if (!survivorProductId || !group.brandKey || !group.nameKey || !group.candidateFingerprint) return;
    setSaving(true);
    setOperationNotice(null);
    onError(null);
    try {
      await request("/api/v1/admin/products/duplicates/branded/survivor", {
        method: "POST",
        body: {
          brandKey: group.brandKey,
          nameKey: group.nameKey,
          survivorProductId,
          candidateFingerprint: group.candidateFingerprint
        },
        timeoutMs: 60000
      });
      setOperationNotice(locale === "tr"
        ? `#${survivorProductId} survivor olarak seçildi ve aramaya uygulandı.`
        : `#${survivorProductId} was selected as survivor and applied to search.`);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  function groupKey(group: BrandedProductDuplicateGroup) {
    return `${group.brandKey ?? ""}\u0000${group.nameKey ?? ""}`;
  }

  async function applySelectedSafeGroups() {
    const selected = groups.filter(group => selectedSafeGroups.includes(groupKey(group)));
    if (!selected.length) return;
    setSaving(true);
    setOperationNotice(null);
    onError(null);
    let applied = 0;
    try {
      for (const group of selected) {
        const survivorProductId = group.recommendation?.suggestedSurvivorProductId;
        if (!survivorProductId || !group.brandKey || !group.nameKey || !group.candidateFingerprint) continue;
        await request("/api/v1/admin/products/duplicates/branded/survivor", {
          method: "POST",
          body: { brandKey: group.brandKey, nameKey: group.nameKey, survivorProductId, candidateFingerprint: group.candidateFingerprint },
          timeoutMs: 60000
        });
        applied += 1;
      }
      setSelectedSafeGroups([]);
      setOperationNotice(locale === "tr" ? `${applied} güvenli tekrar grubu aramada tekilleştirildi.` : `${applied} safe duplicate groups were collapsed in search.`);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
      await reload();
    } finally {
      setSaving(false);
    }
  }

  function openFamilyDraft(group: BrandedProductDuplicateGroup) {
    const slug = `${group.brandKey ?? "brand"}:${group.nameKey ?? "product"}`.toLowerCase().replace(/[^a-z0-9:_-]+/g, "-").replace(/-+$/g, "");
    setFamilyDraft({
      group,
      familyKey: slug,
      displayName: group.representativeName ?? group.nameKey ?? "Product family",
      evidenceReason: locale === "tr" ? "Aynı ürünün doğrulanmış farklı paket miktarları." : "Verified package-size variants of the same product.",
      representativeProductId: group.recommendation?.suggestedSurvivorProductId,
      acknowledgedWarnings: []
    });
  }

  function familyRequestBody(draft: FamilyDraft) {
    return {
      familyKey: draft.familyKey,
      displayName: draft.displayName,
      representativeProductId: draft.representativeProductId,
      evidenceReason: draft.evidenceReason,
      members: (draft.group.candidates ?? []).map(candidate => ({
        productId: candidate.productId,
        packageQuantity: candidate.servingSize,
        packageUnit: candidate.servingUnit,
        packageLabel: `${formatValue(candidate.servingSize)} ${candidate.servingUnit ?? ""}`.trim(),
        defaultVariant: candidate.productId === draft.representativeProductId
      }))
    };
  }

  async function previewFamily() {
    if (!familyDraft) return;
    setSaving(true);
    onError(null);
    try {
      const preview = await request<FamilyPreview>("/api/v1/admin/product-families/preview", { method: "POST", body: familyRequestBody(familyDraft), timeoutMs: 60000, bypassCache: true });
      setFamilyDraft({ ...familyDraft, preview, acknowledgedWarnings: [] });
    } catch (error) { onError(formatRequestError(error)); }
    finally { setSaving(false); }
  }

  async function createFamily() {
    if (!familyDraft?.preview?.eligible || !familyDraft.preview.previewFingerprint) return;
    setSaving(true);
    onError(null);
    try {
      await request("/api/v1/admin/product-families", {
        method: "POST",
        body: { ...familyRequestBody(familyDraft), previewFingerprint: familyDraft.preview.previewFingerprint, acknowledgedWarnings: familyDraft.acknowledgedWarnings },
        timeoutMs: 60000
      });
      setOperationNotice(locale === "tr" ? `${familyDraft.displayName} paket ailesi oluşturuldu.` : `${familyDraft.displayName} package family was created.`);
      setFamilyDraft(null);
      await reload();
    } catch (error) { onError(formatRequestError(error)); }
    finally { setSaving(false); }
  }

  async function startBatchJob() {
    setSaving(true); onError(null);
    try {
      await request("/api/v1/admin/products/duplicates/branded/batch-jobs", { method: "POST", body: { query: debouncedQuery || null }, timeoutMs: 60000 });
      await loadBatchJobs();
    } catch (error) { onError(formatRequestError(error)); }
    finally { setSaving(false); }
  }

  async function controlBatchJob(job: DuplicateBatchJob, action: "pause" | "resume") {
    if (!job.id) return;
    setSaving(true); onError(null);
    try {
      await request(`/api/v1/admin/products/duplicates/branded/batch-jobs/${action}?jobId=${job.id}`, { method: "POST", timeoutMs: 60000 });
      await loadBatchJobs();
    } catch (error) { onError(formatRequestError(error)); }
    finally { setSaving(false); }
  }

  return <div className="stack branded-duplicate-workspace">
    <SectionToolbar
      title={locale === "tr" ? "Markalı ürün kimlik kararları" : "Branded product identity decisions"}
      description={locale === "tr" ? "Benzer marka ve ürün adlarını GTIN, kaynak, porsiyon ve besin kanıtıyla inceleyin." : "Review similar brand and product names using GTIN, source, serving and nutrition evidence."}
      state={state}
      onReload={reload}
    />

    <div className="catalog-evidence-summary">
      <MetricCard label={locale === "tr" ? "Eşleşen grup" : "Matching groups"} value={formatValue(data?.totalElements ?? groups.length)} hint={priorityOnly ? (locale === "tr" ? "Öncelikli kimlik adayları" : "Priority identity candidates") : (locale === "tr" ? "Tüm aday kümeleri" : "All candidate groups")} />
      <MetricCard label={locale === "tr" ? "Kararı güncel" : "Current decisions"} value={formatValue(decided)} hint={locale === "tr" ? "Görünür sayfa" : "Visible page"} />
      <MetricCard label={locale === "tr" ? "Yeniden incele" : "Stale decisions"} value={formatValue(stale)} hint={locale === "tr" ? "Aday kanıtı değişti" : "Candidate evidence changed"} />
    </div>

    {operationNotice && <div className="canonical-operation-notice" role="status">{operationNotice}</div>}

    {canManage && groups.some(group => group.recommendation?.action === "COLLAPSE_DUPLICATES" && !group.searchCollapse?.active) && <div className="branded-bulk-toolbar">
      <div><strong>{locale === "tr" ? "Güvenli toplu işlem" : "Safe bulk action"}</strong><span>{locale === "tr" ? "Yalnızca %99 güvenli, aynı porsiyon ve besin profiline sahip tekrarlar." : "Only 99% confidence duplicates with matching serving and nutrition profiles."}</span></div>
      <div className="button-row"><button type="button" className="ghost-button" disabled={saving} onClick={() => setSelectedSafeGroups(groups.filter(group => group.recommendation?.action === "COLLAPSE_DUPLICATES" && !group.searchCollapse?.active).map(groupKey))}>{locale === "tr" ? "Güvenli grupları seç" : "Select safe groups"}</button><button type="button" className="primary-button" disabled={saving || !selectedSafeGroups.length} onClick={applySelectedSafeGroups}>{locale === "tr" ? `Seçilenleri uygula (${selectedSafeGroups.length})` : `Apply selected (${selectedSafeGroups.length})`}</button></div>
    </div>}

    {canManage && <section className="panel branded-batch-panel">
      <div className="modern-section-heading"><div><span className="eyebrow">{locale === "tr" ? "ARKA PLAN İŞİ" : "BACKGROUND JOB"}</span><h2>{locale === "tr" ? "Tüm güvenli tekrarları işle" : "Process all safe duplicates"}</h2><p>{locale === "tr" ? "Geçerli arama filtresindeki %99 güvenli grupları sayfa sınırı olmadan işler. Durdurulabilir ve kaldığı yerden sürdürülebilir." : "Processes 99% confidence groups in the current search scope without a page limit. It can be paused and resumed."}</p></div><button type="button" className="primary-button" disabled={saving || batchJobs.some(job => ["QUEUED", "DISCOVERING", "RUNNING", "PAUSE_REQUESTED"].includes(job.status ?? ""))} onClick={startBatchJob}>{locale === "tr" ? "Arka plan işlemini başlat" : "Start background job"}</button></div>
      {batchJobs.slice(0, 3).map(job => <article className="branded-batch-row" key={job.id}>
        <div className="branded-batch-title"><Badge value={job.status} tone={job.status === "COMPLETED" ? "good" : job.status === "FAILED" ? "warn" : "neutral"} /><strong>#{job.id}</strong><span>{job.query || (locale === "tr" ? "Tüm katalog" : "Full catalog")}</span></div>
        <div className="branded-batch-progress"><div><span style={{ width: `${job.progressPercent ?? 0}%` }} /></div><strong>%{job.progressPercent ?? 0}</strong></div>
        <dl><div><dt>{locale === "tr" ? "İşlenen" : "Processed"}</dt><dd>{formatValue(job.processedGroups)} / {formatValue(job.totalGroups)}</dd></div><div><dt>{locale === "tr" ? "Uygulanan" : "Applied"}</dt><dd>{formatValue(job.appliedGroups)}</dd></div><div><dt>{locale === "tr" ? "Atlanan" : "Skipped"}</dt><dd>{formatValue(job.skippedGroups)}</dd></div><div><dt>{locale === "tr" ? "Hatalı" : "Failed"}</dt><dd>{formatValue(job.failedGroups)}</dd></div></dl>
        {job.lastError && <p className="field-error">{job.lastError}</p>}
        <div className="button-row">{["QUEUED", "DISCOVERING", "RUNNING", "PAUSE_REQUESTED"].includes(job.status ?? "") && <button type="button" className="ghost-button" disabled={saving || job.status === "PAUSE_REQUESTED"} onClick={() => controlBatchJob(job, "pause")}>{locale === "tr" ? "Durdur" : "Pause"}</button>}{["PAUSED", "FAILED"].includes(job.status ?? "") && <button type="button" className="primary-button" disabled={saving} onClick={() => controlBatchJob(job, "resume")}>{locale === "tr" ? "Kaldığı yerden sürdür" : "Resume"}</button>}</div>
      </article>)}
    </section>}

    <CollapsiblePanel title={locale === "tr" ? "Adayları filtrele" : "Refine candidates"} description={locale === "tr" ? "Marka veya ürün adıyla arayın; önce en az varyant sinyali taşıyan grupları inceleyin." : "Search by brand or product name and prioritize groups with the fewest variant signals."} open={filtersOpen} onToggle={() => setFiltersOpen((current) => !current)}>
      <div className="canonical-filter-row">
        <label>
          {locale === "tr" ? "Marka veya ürün" : "Brand or product"}
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Wispa" />
        </label>
        <label className="checkbox-field">
          <input type="checkbox" checked={priorityOnly} onChange={(event) => setPriorityOnly(event.target.checked)} />
          <span>{locale === "tr" ? "Yalnızca öncelikli adaylar" : "Priority candidates only"}</span>
        </label>
      </div>
    </CollapsiblePanel>

    {draft && <Panel title={locale === "tr" ? "Kararı kaydet" : "Record decision"} description={`${draft.group.brandName ?? "-"} / ${draft.group.representativeName ?? "-"}`}>
      <div className="branded-decision-editor">
        <Badge value={draft.decision} tone={draft.decision === "BLOCKED" ? "warn" : "neutral"} />
        {draft.survivorProductId && <span>{locale === "tr" ? "Seçilen ürün" : "Selected product"} #{draft.survivorProductId}</span>}
        <label>
          {locale === "tr" ? "İnceleme gerekçesi" : "Review reason"}
          <textarea value={draft.reason} maxLength={1000} rows={3} onChange={(event) => setDraft({ ...draft, reason: event.target.value })} placeholder={locale === "tr" ? "En az 10 karakterlik kanıt ve karar açıklaması" : "Evidence and decision rationale, at least 10 characters"} />
        </label>
        <div className="button-row">
          <button type="button" className="ghost-button" disabled={saving} onClick={() => setDraft(null)}>{locale === "tr" ? "Vazgeç" : "Cancel"}</button>
          <button type="button" className="primary-button" disabled={saving || draft.reason.trim().length < 10} onClick={saveDecision}>{saving ? (locale === "tr" ? "Kaydediliyor" : "Saving") : (locale === "tr" ? "Kararı kaydet" : "Save decision")}</button>
        </div>
      </div>
    </Panel>}

    {collapseDraft && <Panel title={collapseDraft.action === "APPLY" ? (locale === "tr" ? "Arama tekilleştirmesini uygula" : "Apply search collapse") : (locale === "tr" ? "Arama tekilleştirmesini geri al" : "Revert search collapse")} description={`${collapseDraft.group.brandName ?? "-"} / ${collapseDraft.group.representativeName ?? "-"}`}>
      <div className="branded-decision-editor">
        <Badge value={collapseDraft.action} tone={collapseDraft.action === "REVERT" ? "warn" : "good"} />
        <p className="field-help">{collapseDraft.action === "APPLY"
          ? (locale === "tr" ? "Yalnızca seçilen survivor aramada kalır. Diğer kayıtlar, barkodlar ve kaynak kanıtı silinmez." : "Only the selected survivor remains in search. Other records, barcodes and source evidence are not deleted.")
          : (locale === "tr" ? "Gruptaki tüm kayıtlar yeniden arama sonuçlarına açılır." : "All group members become visible in search again.")}</p>
        <label>
          {locale === "tr" ? "Operasyon gerekçesi" : "Operation reason"}
          <textarea value={collapseDraft.reason} maxLength={1000} rows={3} onChange={(event) => setCollapseDraft({ ...collapseDraft, reason: event.target.value })} placeholder={locale === "tr" ? "En az 10 karakterlik uygulama veya geri alma gerekçesi" : "Apply or revert rationale, at least 10 characters"} />
        </label>
        <div className="button-row">
          <button type="button" className="ghost-button" disabled={saving} onClick={() => setCollapseDraft(null)}>{locale === "tr" ? "Vazgeç" : "Cancel"}</button>
          <button type="button" className="primary-button" disabled={saving || collapseDraft.reason.trim().length < 10} onClick={saveCollapse}>{saving ? (locale === "tr" ? "İşleniyor" : "Working") : (collapseDraft.action === "APPLY" ? (locale === "tr" ? "Aramada uygula" : "Apply to search") : (locale === "tr" ? "Geri al" : "Revert"))}</button>
        </div>
      </div>
    </Panel>}

    {familyDraft && <div className="modal-backdrop" role="presentation"><section className="modal-card branded-family-modal" role="dialog" aria-modal="true" aria-labelledby="family-draft-title">
      <header><div><span className="eyebrow">{locale === "tr" ? "PAKET AİLESİ" : "PACKAGE FAMILY"}</span><h2 id="family-draft-title">{locale === "tr" ? "Önerilen aileyi prova et" : "Preview suggested family"}</h2></div><button type="button" className="icon-button" onClick={() => setFamilyDraft(null)} aria-label={locale === "tr" ? "Kapat" : "Close"}>×</button></header>
      <div className="branded-family-form"><label>{locale === "tr" ? "Aile adı" : "Family name"}<input value={familyDraft.displayName} onChange={event => setFamilyDraft({ ...familyDraft, displayName: event.target.value, preview: undefined })} /></label><label>{locale === "tr" ? "Kalıcı anahtar" : "Stable key"}<input value={familyDraft.familyKey} onChange={event => setFamilyDraft({ ...familyDraft, familyKey: event.target.value, preview: undefined })} /></label><label className="wide">{locale === "tr" ? "Kanıt" : "Evidence"}<textarea rows={3} value={familyDraft.evidenceReason} onChange={event => setFamilyDraft({ ...familyDraft, evidenceReason: event.target.value, preview: undefined })} /></label></div>
      <div className="branded-family-members">{(familyDraft.group.candidates ?? []).map(candidate => <div key={candidate.productId}><label><input type="radio" name="family-representative" checked={candidate.productId === familyDraft.representativeProductId} onChange={() => setFamilyDraft({ ...familyDraft, representativeProductId: candidate.productId, preview: undefined })} /> {locale === "tr" ? "Temsilci" : "Representative"}</label><strong>#{candidate.productId} · {candidate.productName}</strong><span>{formatValue(candidate.servingSize)} {candidate.servingUnit} · {candidate.barcode}</span></div>)}</div>
      {familyDraft.preview && <div className={`canonical-eligibility ${familyDraft.preview.eligible ? "good" : "blocked"}`}><strong>{familyDraft.preview.eligible ? (locale === "tr" ? "Prova başarılı" : "Preview passed") : (locale === "tr" ? "Prova engellendi" : "Preview blocked")}</strong>{(familyDraft.preview.errors ?? []).map(error => <span key={error}>{error}</span>)}{(familyDraft.preview.warnings ?? []).map(warning => <label key={warning}><input type="checkbox" checked={familyDraft.acknowledgedWarnings.includes(warning)} onChange={event => setFamilyDraft({ ...familyDraft, acknowledgedWarnings: event.target.checked ? [...familyDraft.acknowledgedWarnings, warning] : familyDraft.acknowledgedWarnings.filter(item => item !== warning) })} /> {warning}</label>)}</div>}
      <footer className="button-row"><button type="button" className="ghost-button" onClick={() => setFamilyDraft(null)}>{locale === "tr" ? "Vazgeç" : "Cancel"}</button><button type="button" className="ghost-button" disabled={saving || familyDraft.evidenceReason.trim().length < 10} onClick={previewFamily}>{locale === "tr" ? "Prova çalıştır" : "Run preview"}</button><button type="button" className="primary-button" disabled={saving || !familyDraft.preview?.eligible || (familyDraft.preview.warnings?.length ?? 0) !== familyDraft.acknowledgedWarnings.length} onClick={createFamily}>{locale === "tr" ? "Aileyi oluştur" : "Create family"}</button></footer>
    </section></div>}

    {groups.length === 0 && <Panel title={locale === "tr" ? "Markalı eşleşmeler" : "Branded matches"}><p className="empty-state">{locale === "tr" ? "Bu filtreye uyan aday kümesi bulunamadı." : "No candidate groups match this filter."}</p></Panel>}
    {groups.map((group) => <BrandedGroupCard key={`${group.brandKey}:${group.nameKey}`} group={group} locale={locale} canManage={canManage} saving={saving} selected={selectedSafeGroups.includes(groupKey(group))} onToggleSelected={() => setSelectedSafeGroups(current => current.includes(groupKey(group)) ? current.filter(key => key !== groupKey(group)) : [...current, groupKey(group)])} onCreateFamily={openFamilyDraft} onDecision={startDecision} onApplySurvivor={applySurvivor} onCollapse={(selectedGroup, action) => setCollapseDraft({ group: selectedGroup, action, reason: "" })} />)}

    <PaginationControls
      page={data?.page ?? page}
      pageSize={data?.size ?? pageSize}
      totalElements={data?.totalElements ?? groups.length}
      totalPages={data?.totalPages ?? 1}
      first={Boolean(data?.first)}
      last={Boolean(data?.last)}
      onPageChange={setPage}
      onPageSizeChange={setPageSize}
    />
  </div>;
}

function BrandedGroupCard({
  group,
  locale,
  canManage,
  saving,
  onDecision,
  onApplySurvivor,
  onCollapse
  , selected, onToggleSelected, onCreateFamily
}: {
  group: BrandedProductDuplicateGroup;
  locale: "tr" | "en";
  canManage: boolean;
  saving: boolean;
  onDecision: (group: BrandedProductDuplicateGroup, decision: BrandedDuplicateDecision | "CLEAR", survivorProductId?: number) => void;
  onApplySurvivor: (group: BrandedProductDuplicateGroup, survivorProductId?: number) => void;
  onCollapse: (group: BrandedProductDuplicateGroup, action: "APPLY" | "REVERT") => void;
  selected: boolean;
  onToggleSelected: () => void;
  onCreateFamily: (group: BrandedProductDuplicateGroup) => void;
}) {
  const stored = group.storedDecision;
  return <section className="canonical-group">
    <header className="canonical-group-header">
      <div>
        <span className="eyebrow">{group.brandName ?? group.brandKey}</span>
        <h2>{group.representativeName ?? group.nameKey}</h2>
        <p>{formatValue(group.productCount)} {locale === "tr" ? "kayıt" : "records"} / {formatValue(group.barcodeCount)} GTIN</p>
      </div>
      <div className="canonical-group-status">
        {group.recommendation && <Badge value={`${group.recommendation.action} · %${group.recommendation.confidence ?? 0}`} tone={group.recommendation.action === "MANUAL_REVIEW" ? "warn" : "good"} />}
        <Badge value={group.decision} tone="warn" />
        <Badge value={group.variantSignal ? (locale === "tr" ? "VARYANT SİNYALİ" : "VARIANT SIGNAL") : (locale === "tr" ? "ÖNCELİKLİ" : "PRIORITY")} tone={group.variantSignal ? "neutral" : "warn"} />
        {stored && <Badge value={group.decisionStale ? (locale === "tr" ? "KARAR ESKİ" : "STALE DECISION") : stored.decision} tone={group.decisionStale ? "warn" : "good"} />}
        {group.searchCollapse?.active && <Badge value={locale === "tr" ? "ARAMADA UYGULANDI" : "APPLIED TO SEARCH"} tone="good" />}
      </div>
    </header>
    {group.recommendation && <div className={`branded-recommendation ${group.recommendation.action === "MANUAL_REVIEW" ? "warn" : "good"}`}><div><strong>{locale === "tr" ? "Sistem önerisi" : "System recommendation"}</strong><span>{group.recommendation.reason}</span></div>{group.recommendation.action === "COLLAPSE_DUPLICATES" && !group.searchCollapse?.active && <label className="checkbox-field"><input type="checkbox" checked={selected} onChange={onToggleSelected} /><span>{locale === "tr" ? "Toplu işleme ekle" : "Add to bulk action"}</span></label>}{group.recommendation.action === "CREATE_PACKAGE_FAMILY" && <button type="button" className="primary-button" disabled={saving} onClick={() => onCreateFamily(group)}>{locale === "tr" ? "Paket ailesi provasını aç" : "Open package-family preview"}</button>}</div>}
    {stored && <div className={`canonical-eligibility ${group.decisionStale ? "blocked" : "good"}`}>
      <strong>{stored.reason}</strong>
      <span>{stored.reviewedBy ?? "-"} / {stored.reviewedAt ?? "-"}</span>
    </div>}
    <div className="canonical-candidate-grid">
      {(group.candidates ?? []).map((candidate) => <CandidateCard key={candidate.productId ?? candidate.sourceKey} candidate={candidate} locale={locale} canManage={canManage} saving={saving} group={group} onApplySurvivor={onApplySurvivor} />)}
    </div>
    {canManage && <div className="button-row branded-group-actions">
      <button type="button" className="ghost-button" disabled={saving} onClick={() => onDecision(group, "KEEP_SEPARATE")}>{locale === "tr" ? "Varyantları ayrı tut" : "Keep variants separate"}</button>
      <button type="button" className="ghost-button" disabled={saving} onClick={() => onDecision(group, "BLOCKED")}>{locale === "tr" ? "Kanıt bekliyor" : "Block for evidence"}</button>
      {stored && <button type="button" className="ghost-button" disabled={saving} onClick={() => onDecision(group, "CLEAR")}>{locale === "tr" ? "Kararı temizle" : "Clear decision"}</button>}
      {stored?.decision === "SURVIVOR_SELECTED" && !group.decisionStale && !group.searchCollapse?.active && <button type="button" className="primary-button" disabled={saving} onClick={() => onApplySurvivor(group, stored.survivorProductId)}>{locale === "tr" ? "Survivor ve aramayı uygula" : "Apply survivor to search"}</button>}
      {group.searchCollapse?.active && <button type="button" className="ghost-button danger-button" disabled={saving} onClick={() => onCollapse(group, "REVERT")}>{locale === "tr" ? "Arama tekilleştirmesini geri al" : "Revert search collapse"}</button>}
    </div>}
  </section>;
}

function CandidateCard({ candidate, locale, canManage, saving, group, onApplySurvivor }: {
  candidate: BrandedProductDuplicateCandidate;
  locale: "tr" | "en";
  canManage: boolean;
  saving: boolean;
  group: BrandedProductDuplicateGroup;
  onApplySurvivor: (group: BrandedProductDuplicateGroup, survivorProductId?: number) => void;
}) {
  return <article className="canonical-candidate">
    <div className="canonical-candidate-title"><div><h3>{candidate.productName ?? `Product #${candidate.productId}`}</h3><span>{candidate.dataSource ?? "-"} / {candidate.sourceKey ?? "-"}</span></div><Badge value={candidate.verificationStatus} tone={candidate.verificationStatus === "VERIFIED" ? "good" : "neutral"} /></div>
    <dl className="canonical-facts">
      <div><dt>GTIN</dt><dd>{candidate.barcode ?? (locale === "tr" ? "Eksik" : "Missing")}</dd></div>
      <div><dt>{locale === "tr" ? "Pazar" : "Market"}</dt><dd>{candidate.marketRegion ?? "-"}</dd></div>
      <div><dt>{locale === "tr" ? "Porsiyon" : "Serving"}</dt><dd>{formatValue(candidate.servingSize)} {candidate.servingUnit ?? "-"}</dd></div>
      <div><dt>{locale === "tr" ? "Kalite" : "Quality"}</dt><dd>{formatValue(candidate.qualityScore)} / 100</dd></div>
      <div><dt>{locale === "tr" ? "Kalori" : "Calories"}</dt><dd>{formatValue(candidate.calories)} kcal</dd></div>
      <div><dt>{locale === "tr" ? "Makrolar" : "Macros"}</dt><dd>P {formatValue(candidate.protein)} / C {formatValue(candidate.carbs)} / F {formatValue(candidate.fat)}</dd></div>
    </dl>
    {canManage && <button type="button" className="primary-button" disabled={saving || !candidate.productId || group.searchCollapse?.active} onClick={() => onApplySurvivor(group, candidate.productId)}>{saving ? (locale === "tr" ? "Uygulanıyor" : "Applying") : (locale === "tr" ? "Seç ve aramada uygula" : "Select and apply to search")}</button>}
  </article>;
}
