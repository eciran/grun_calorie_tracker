import { useEffect, useState } from "react";

import { formatRequestError, request } from "../api";

import { FoodProduct, FoodCanonicalDuplicateGroup, FoodCanonicalDuplicateGroupPage } from "../types";

import { CollapsiblePanel, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { Badge, ConfirmDialog, formatValue, productName, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";
import { DuplicateResolutionChart } from "../CatalogWorkspaceCharts";

export type CanonicalDecision = {
  group: FoodCanonicalDuplicateGroup;
  product: FoodProduct;
};

export function CanonicalDuplicateWorkspace({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [filter, setFilter] = useState<"ALL" | "UNRESOLVED" | "DECIDED">("ALL");
  const [pendingDecision, setPendingDecision] = useState<CanonicalDecision | null>(null);
  const [pendingClear, setPendingClear] = useState<FoodCanonicalDuplicateGroup | null>(null);
  const [saving, setSaving] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (filter === "UNRESOLVED") params.set("resolved", "false");
  if (filter === "DECIDED") params.set("resolved", "true");
  const { data, state, reload } = useEndpoint<FoodCanonicalDuplicateGroupPage>(
    `/api/v1/admin/products/duplicates/canonical?${params.toString()}`,
    onError
  );
  const groups = data?.content ?? [];
  const attentionCount = groups.filter((group) => group.resolutionState !== "RESOLVED").length;
  const resolvedCount = groups.filter((group) => group.resolutionState === "RESOLVED").length;
  const candidateCount = groups.reduce((sum, group) => sum + (group.productCount ?? 0), 0);

  useEffect(() => setPage(0), [filter, pageSize]);

  async function resolvePrimary(decision: CanonicalDecision) {
    if (!decision.group.canonicalFoodKey || !decision.product.id) return;
    setSaving(true);
    onError(null);
    try {
      await request("/api/v1/admin/products/duplicates/canonical/resolve", {
        method: "POST",
        body: {
          canonicalFoodKey: decision.group.canonicalFoodKey,
          primaryProductId: decision.product.id
        }
      });
      setPendingDecision(null);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  async function clearResolution(group: FoodCanonicalDuplicateGroup) {
    if (!group.canonicalFoodKey) return;
    setSaving(true);
    onError(null);
    try {
      await request(`/api/v1/admin/products/duplicates/canonical/resolution?canonicalFoodKey=${encodeURIComponent(group.canonicalFoodKey)}`, {
        method: "DELETE"
      });
      setPendingClear(null);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  return <div className="stack">
    <SectionToolbar title={locale === "tr" ? "Tekrarlanan ürün kararları" : "Duplicate product decisions"} description={locale === "tr" ? "Aynı ürünü temsil eden kaynak kayıtları arasında güvenli ana ürünü belirleyin." : "Choose the safe primary product among source records that represent the same item."} state={state} onReload={reload} />
    <div className="catalog-evidence-overview">
      <Panel title={locale === "tr" ? "Karar durumu" : "Decision status"} description={locale === "tr" ? "Görünür sayfadaki tamamlanan ve bekleyen kararlar." : "Resolved and pending decisions on the visible page."}><DuplicateResolutionChart resolved={resolvedCount} attention={attentionCount} locale={locale} /></Panel>
      <div className="catalog-evidence-summary">
        <MetricCard label={locale === "tr" ? "Eşleşen grup" : "Matching groups"} value={formatValue(data?.totalElements ?? groups.length)} hint={locale === "tr" ? "Geçerli karar filtresi" : "Current decision filter"} />
        <MetricCard label={locale === "tr" ? "Kaynak aday" : "Source candidates"} value={formatValue(candidateCount)} hint={locale === "tr" ? "Kaynak kayıtlar korunur" : "Source records remain preserved"} />
      </div>
    </div>

    {filter !== "ALL" && <div className="product-review-active-filters"><span>{filter === "DECIDED" ? (locale === "tr" ? "Karar verildi" : "Resolved") : (locale === "tr" ? "Karar bekliyor" : "Needs decision")}</span></div>}
    <CollapsiblePanel title={locale === "tr" ? "Kararları filtrele" : "Refine decisions"} description={locale === "tr" ? "Karar bekleyen veya tamamlanan ürün gruplarına odaklanın." : "Focus on product groups with pending or completed decisions."} open={filtersOpen} onToggle={() => setFiltersOpen((current) => !current)}>
      <div className="canonical-filter-row">
        <label>
          {locale === "tr" ? "Karar durumu" : "Resolution"}
          <select value={filter} onChange={(event) => setFilter(event.target.value as typeof filter)}>
            <option value="ALL">{locale === "tr" ? "Tüm gruplar" : "All groups"}</option>
            <option value="UNRESOLVED">{locale === "tr" ? "Karar bekleyen" : "No stored decision"}</option>
            <option value="DECIDED">{locale === "tr" ? "Karar verilmiş" : "Stored decision"}</option>
          </select>
        </label>
        <p>{locale === "tr" ? "Öneriler kurallara göre üretilir; her ana ürün kararını bir yönetici onaylar." : "Recommendations are deterministic. An admin must confirm every primary decision."}</p>
      </div>
    </CollapsiblePanel>

    {groups.length === 0 && <Panel title={locale === "tr" ? "Tekrarlanan ürün grupları" : "Duplicate product groups"}><p className="empty-state">{locale === "tr" ? "Bu filtreye uyan tekrarlanan ürün grubu bulunamadı." : "No duplicate groups match this filter."}</p></Panel>}
    {groups.map((group) => {
      const assessments = group.candidateAssessments?.length
        ? group.candidateAssessments
        : (group.products ?? []).map((product) => ({ product, primaryEligible: true, eligibilityIssues: [], recommended: product.id === group.recommendedPrimaryProductId }));
      return <section className="canonical-group" key={group.canonicalFoodKey}>
        <header className="canonical-group-header">
          <div>
            <span className="eyebrow">{group.canonicalFoodKey}</span>
            <h2>{formatValue(group.productCount)} {locale === "tr" ? "kaynak adayı" : "source candidates"}</h2>
            {group.resolutionStatusReason && <p>{group.resolutionStatusReason}</p>}
          </div>
          <div className="canonical-group-status">
            <Badge value={group.resolutionState} tone={group.resolutionState === "RESOLVED" ? "good" : group.resolutionState === "UNRESOLVED" ? "neutral" : "warn"} />
            {group.primaryProductId && <small>{locale === "tr" ? "Ana ürün" : "Primary"} #{group.primaryProductId}</small>}
            {group.primaryProductId && <button className="ghost-button" type="button" onClick={() => setPendingClear(group)}>{locale === "tr" ? "Kararı temizle" : "Clear decision"}</button>}
          </div>
        </header>
        <div className="canonical-candidate-grid">
          {assessments.map((assessment) => {
            const product = assessment.product;
            if (!product) return null;
            const selected = product.id === group.primaryProductId;
            return <article className={`canonical-candidate${selected ? " selected" : ""}`} key={product.id ?? product.sourceKey}>
              <div className="canonical-candidate-title">
                <div>
                  <h3>{productName(product)}</h3>
                  <span>{product.dataSource ?? "Unknown source"} / {product.sourceKey ?? `Product #${product.id}`}</span>
                </div>
                <div className="badge-stack">
                  {selected && <Badge value={locale === "tr" ? "MEVCUT ANA ÜRÜN" : "CURRENT PRIMARY"} tone="good" />}
                  {assessment.recommended && <Badge value={locale === "tr" ? "ÖNERİLEN" : "RECOMMENDED"} tone="neutral" />}
                </div>
              </div>
              <dl className="canonical-facts">
                <div><dt>{locale === "tr" ? "Hazırlama" : "Preparation"}</dt><dd>{product.preparationState ?? "UNSPECIFIED"}</dd></div>
                <div><dt>{locale === "tr" ? "Pazar" : "Market"}</dt><dd>{product.marketRegion ?? "GLOBAL"}</dd></div>
                <div><dt>{locale === "tr" ? "Kalite" : "Quality"}</dt><dd>{formatValue(product.qualityScore)} / 100</dd></div>
                <div><dt>{locale === "tr" ? "Kullanım" : "Usage"}</dt><dd>{formatValue(product.usageCount)}</dd></div>
                <div><dt>{locale === "tr" ? "Kalori" : "Calories"}</dt><dd>{formatValue(product.calories)} kcal</dd></div>
                <div><dt>{locale === "tr" ? "Makrolar" : "Macros"}</dt><dd>P {formatValue(product.protein)} / C {formatValue(product.carbs)} / F {formatValue(product.fat)}</dd></div>
              </dl>
              <div className={`canonical-eligibility ${assessment.primaryEligible ? "good" : "blocked"}`}>
                <strong>{assessment.primaryEligible ? (locale === "tr" ? "Ana ürün olmaya uygun" : "Eligible for primary") : (locale === "tr" ? "Seçim engellendi" : "Selection blocked")}</strong>
                {(assessment.eligibilityIssues ?? []).map((issue) => <span key={issue}>{issue}</span>)}
              </div>
              <button
                className="primary-button"
                type="button"
                disabled={saving || !assessment.primaryEligible || selected}
                onClick={() => setPendingDecision({ group, product })}
              >
                {selected ? (locale === "tr" ? "Ana ürün seçildi" : "Selected primary") : (locale === "tr" ? "Ana ürün olarak seç" : "Select as primary")}
              </button>
            </article>;
          })}
        </div>
      </section>;
    })}

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

    {pendingDecision && <ConfirmDialog
      title={locale === "tr" ? "Ana ürün değiştirilsin mi?" : "Change canonical primary?"}
      message={locale === "tr" ? `${productName(pendingDecision.product)} bu grup için kullanıcılara gösterilen ana ürün olarak seçilsin mi? Kaynak kayıtlar korunacaktır.` : `Select ${productName(pendingDecision.product)} as the user-visible primary for this canonical group? Source records will remain stored.`}
      confirmLabel={locale === "tr" ? "Ana ürünü onayla" : "Confirm primary"}
      busy={saving}
      onCancel={() => setPendingDecision(null)}
      onConfirm={() => resolvePrimary(pendingDecision)}
    />}
    {pendingClear && <ConfirmDialog
      title={locale === "tr" ? "Ana ürün kararı temizlensin mi?" : "Clear canonical decision?"}
      message={locale === "tr" ? "Yeni bir ana ürün seçilene kadar tüm uygun kaynak adayları tekrar görünür olur. Hiçbir ürün kaydı silinmez." : "All eligible source candidates will become visible again until a new primary is selected. No product record will be deleted."}
      confirmLabel={locale === "tr" ? "Kararı temizle" : "Clear decision"}
      busy={saving}
      onCancel={() => setPendingClear(null)}
      onConfirm={() => clearResolution(pendingClear)}
    />}
  </div>;
}
