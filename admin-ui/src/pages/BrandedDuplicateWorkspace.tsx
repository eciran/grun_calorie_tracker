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
  const [priorityOnly, setPriorityOnly] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [draft, setDraft] = useState<DecisionDraft | null>(null);
  const [collapseDraft, setCollapseDraft] = useState<CollapseDraft | null>(null);
  const [saving, setSaving] = useState(false);

  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (query.trim()) params.set("query", query.trim());
  if (priorityOnly) params.set("variantSignal", "false");
  const { data, state, reload } = useEndpoint<BrandedProductDuplicateGroupPage>(
    `/api/v1/admin/products/duplicates/branded?${params.toString()}`,
    onError
  );
  const groups = data?.content ?? [];
  const decided = groups.filter((group) => group.storedDecision && !group.decisionStale).length;
  const stale = groups.filter((group) => group.decisionStale).length;

  useEffect(() => setPage(0), [query, priorityOnly, pageSize]);

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
        await request(`/api/v1/admin/products/duplicates/branded/decision?${params.toString()}`, { method: "DELETE" });
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
          }
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
        await request(`/api/v1/admin/products/duplicates/branded/search-collapse?${params.toString()}`, { method: "DELETE" });
      } else {
        await request("/api/v1/admin/products/duplicates/branded/search-collapse", {
          method: "POST",
          body: {
            brandKey: collapseDraft.group.brandKey,
            nameKey: collapseDraft.group.nameKey,
            candidateFingerprint: collapseDraft.group.candidateFingerprint,
            reason: collapseDraft.reason.trim()
          }
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

  return <div className="stack">
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

    {groups.length === 0 && <Panel title={locale === "tr" ? "Markalı eşleşmeler" : "Branded matches"}><p className="empty-state">{locale === "tr" ? "Bu filtreye uyan aday kümesi bulunamadı." : "No candidate groups match this filter."}</p></Panel>}
    {groups.map((group) => <BrandedGroupCard key={`${group.brandKey}:${group.nameKey}`} group={group} locale={locale} canManage={canManage} saving={saving} onDecision={startDecision} onCollapse={(selectedGroup, action) => setCollapseDraft({ group: selectedGroup, action, reason: "" })} />)}

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
  onCollapse
}: {
  group: BrandedProductDuplicateGroup;
  locale: "tr" | "en";
  canManage: boolean;
  saving: boolean;
  onDecision: (group: BrandedProductDuplicateGroup, decision: BrandedDuplicateDecision | "CLEAR", survivorProductId?: number) => void;
  onCollapse: (group: BrandedProductDuplicateGroup, action: "APPLY" | "REVERT") => void;
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
        <Badge value={group.decision} tone="warn" />
        <Badge value={group.variantSignal ? (locale === "tr" ? "VARYANT SİNYALİ" : "VARIANT SIGNAL") : (locale === "tr" ? "ÖNCELİKLİ" : "PRIORITY")} tone={group.variantSignal ? "neutral" : "warn"} />
        {stored && <Badge value={group.decisionStale ? (locale === "tr" ? "KARAR ESKİ" : "STALE DECISION") : stored.decision} tone={group.decisionStale ? "warn" : "good"} />}
        {group.searchCollapse?.active && <Badge value={locale === "tr" ? "ARAMADA UYGULANDI" : "APPLIED TO SEARCH"} tone="good" />}
      </div>
    </header>
    {stored && <div className={`canonical-eligibility ${group.decisionStale ? "blocked" : "good"}`}>
      <strong>{stored.reason}</strong>
      <span>{stored.reviewedBy ?? "-"} / {stored.reviewedAt ?? "-"}</span>
    </div>}
    <div className="canonical-candidate-grid">
      {(group.candidates ?? []).map((candidate) => <CandidateCard key={candidate.productId ?? candidate.sourceKey} candidate={candidate} locale={locale} canManage={canManage} saving={saving} group={group} onDecision={onDecision} />)}
    </div>
    {canManage && <div className="button-row branded-group-actions">
      <button type="button" className="ghost-button" disabled={saving} onClick={() => onDecision(group, "KEEP_SEPARATE")}>{locale === "tr" ? "Varyantları ayrı tut" : "Keep variants separate"}</button>
      <button type="button" className="ghost-button" disabled={saving} onClick={() => onDecision(group, "BLOCKED")}>{locale === "tr" ? "Kanıt bekliyor" : "Block for evidence"}</button>
      {stored && <button type="button" className="ghost-button" disabled={saving} onClick={() => onDecision(group, "CLEAR")}>{locale === "tr" ? "Kararı temizle" : "Clear decision"}</button>}
      {stored?.decision === "SURVIVOR_SELECTED" && !group.decisionStale && !group.searchCollapse?.active && <button type="button" className="primary-button" disabled={saving} onClick={() => onCollapse(group, "APPLY")}>{locale === "tr" ? "Aramada survivor uygula" : "Apply survivor to search"}</button>}
      {group.searchCollapse?.active && <button type="button" className="ghost-button danger-button" disabled={saving} onClick={() => onCollapse(group, "REVERT")}>{locale === "tr" ? "Arama tekilleştirmesini geri al" : "Revert search collapse"}</button>}
    </div>}
  </section>;
}

function CandidateCard({ candidate, locale, canManage, saving, group, onDecision }: {
  candidate: BrandedProductDuplicateCandidate;
  locale: "tr" | "en";
  canManage: boolean;
  saving: boolean;
  group: BrandedProductDuplicateGroup;
  onDecision: (group: BrandedProductDuplicateGroup, decision: BrandedDuplicateDecision, survivorProductId?: number) => void;
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
    {canManage && <button type="button" className="primary-button" disabled={saving || !candidate.productId} onClick={() => onDecision(group, "SURVIVOR_SELECTED", candidate.productId)}>{locale === "tr" ? "Survivor olarak seç" : "Select survivor"}</button>}
  </article>;
}
