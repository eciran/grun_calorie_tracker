import { FormEvent, useEffect, useState } from "react";
import { formatRequestError, type PageResponse, request } from "./api";
import { BarcodeScanner } from "./admin/BarcodeScanner";
import { CollapsiblePanel } from "./AdminPrimitives";
import { Badge, IMAGE_STATUSES, MARKET_REGIONS, VERIFICATION_STATUSES, formatValue, humanizeFeature, marketRegionLabel, productName } from "./admin/shared";
import { useAdminLocale } from "./admin/locale";
import type { FoodProduct } from "./types";
import { CatalogProductDetailModal } from "./CatalogProductDetailModal";

type Props = {
  compact?: boolean;
  initialProductId?: number;
  onError: (message: string | null) => void;
  onSelect?: (product: FoodProduct) => void;
  onSelectionClear?: () => void;
  refreshToken?: number;
  canManage?: boolean;
};

type CatalogFilters = { verificationStatus: string; imageStatus: string; region: string; catalogType: string; dataSource: string };
const EMPTY_FILTERS: CatalogFilters = { verificationStatus: "", imageStatus: "", region: "", catalogType: "", dataSource: "" };
const CATALOG_TYPES = ["BRANDED_PRODUCT", "GENERIC_INGREDIENT", "LOCAL_DISH", "USER_CUSTOM"];
const DATA_SOURCES = ["MANUAL", "OPEN_FOOD_FACTS", "USDA_FOODDATA", "COFID", "EDAMAM", "NUTRITIONIX", "LOCAL_CURATED", "ADMIN_IMPORT", "USER_SUBMITTED_LABEL", "ADMIN_REVIEWED_LABEL"];

export function CatalogProductFinder({ compact = false, initialProductId, onError, onSelect, onSelectionClear, refreshToken = 0, canManage = false }: Props) {
  const { locale } = useAdminLocale();
  const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<FoodProduct> | null>(null);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<FoodProduct | null>(null);
  const [detailProduct, setDetailProduct] = useState<FoodProduct | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [draftFilters, setDraftFilters] = useState<CatalogFilters>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<CatalogFilters>(EMPTY_FILTERS);
  const size = compact ? 6 : 20;

  async function load(search: string, targetPage = 0) {
    setLoading(true);
    onError(null);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(size) });
      if (search.trim()) params.set("query", search.trim());
      Object.entries(appliedFilters).forEach(([key, value]) => { if (value) params.set(key, value); });
      const result = await request<PageResponse<FoodProduct>>(`/api/v1/admin/products/catalog-search?${params}`);
      setData(result);
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(appliedQuery, page); }, [appliedQuery, appliedFilters, page, refreshToken]);
  useEffect(() => {
    if (!initialProductId || selected?.id === initialProductId) return;
    void request<PageResponse<FoodProduct>>(`/api/v1/admin/products/catalog-search?query=${initialProductId}&page=0&size=20`).then((result) => {
      const match = result.content?.find((item) => item.id === initialProductId);
      if (match) setSelected(match);
    }).catch(() => undefined);
  }, [initialProductId]);

  function submit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedQuery(query.trim());
    setAppliedFilters(draftFilters);
  }

  async function lookupBarcode(barcode: string) {
    const params = new URLSearchParams({ query: barcode, page: "0", size: "20" });
    try {
      const result = await request<PageResponse<FoodProduct>>(`/api/v1/admin/products/catalog-search?${params}`);
      const match = result.content?.find((item) => (item.normalizedBarcode ?? item.barcode) === barcode);
      setQuery(barcode); setAppliedQuery(barcode); setPage(0); setData(result);
      if (match) choose(match);
      return Boolean(match);
    } catch (error) {
      onError(formatRequestError(error));
      throw error;
    }
  }

  function choose(product: FoodProduct) {
    setSelected(product);
    onSelect?.(product);
    if (!compact) setDetailProduct(product);
  }

  function clearSelection() {
    setSelected(null);
    onSelectionClear?.();
  }

  const rows = data?.content ?? [];
  const activeFilterCount = Object.values(appliedFilters).filter(Boolean).length + (appliedQuery ? 1 : 0);
  function resetFilters() {
    setQuery(""); setAppliedQuery(""); setDraftFilters(EMPTY_FILTERS); setAppliedFilters(EMPTY_FILTERS); setPage(0); clearSelection();
  }
  return <div className={`catalog-product-finder${compact ? " compact" : ""}`}>
    {!compact && <BarcodeScanner onLookup={lookupBarcode} />}
    <form className="catalog-product-searchbar" onSubmit={submit}>
      <label><span>{tx("Search the entire catalog", "Tüm katalogda ara")}</span><input type="search" value={query} onChange={(event) => { setQuery(event.target.value); clearSelection(); }} placeholder={tx("Product name, brand, barcode or product ID", "Ürün adı, marka, barkod veya ürün kimliği")} /></label>
      <button className="primary-button" disabled={loading} type="submit">{loading ? tx("Searching…", "Aranıyor…") : tx("Search", "Ara")}</button>
    </form>
    {!compact && <CollapsiblePanel className="catalog-search-filter-panel" title={tx("Catalog filters", "Katalog filtreleri")} description={activeFilterCount ? tx(`${activeFilterCount} active criteria`, `${activeFilterCount} etkin ölçüt`) : tx("Narrow the full database by catalog attributes", "Tüm veritabanını katalog özelliklerine göre daraltın")} open={filtersOpen} onToggle={() => setFiltersOpen(value => !value)}>
      <div className="catalog-search-filter-grid">
        <FilterSelect label={tx("Verification", "Doğrulama")} value={draftFilters.verificationStatus} values={VERIFICATION_STATUSES} all={tx("All states", "Tüm durumlar")} onChange={value => setDraftFilters(current => ({ ...current, verificationStatus: value }))} />
        <FilterSelect label={tx("Image status", "Görsel durumu")} value={draftFilters.imageStatus} values={IMAGE_STATUSES} all={tx("All image states", "Tüm görsel durumları")} onChange={value => setDraftFilters(current => ({ ...current, imageStatus: value }))} />
        <FilterSelect label={tx("Market", "Pazar")} value={draftFilters.region} values={MARKET_REGIONS} all={tx("All markets", "Tüm pazarlar")} labelFor={value => marketRegionLabel(value, locale)} onChange={value => setDraftFilters(current => ({ ...current, region: value }))} />
        <FilterSelect label={tx("Catalog type", "Katalog tipi")} value={draftFilters.catalogType} values={CATALOG_TYPES} all={tx("All catalog types", "Tüm katalog tipleri")} onChange={value => setDraftFilters(current => ({ ...current, catalogType: value }))} />
        <FilterSelect label={tx("Data source", "Veri kaynağı")} value={draftFilters.dataSource} values={DATA_SOURCES} all={tx("All sources", "Tüm kaynaklar")} onChange={value => setDraftFilters(current => ({ ...current, dataSource: value }))} />
      </div>
      <div className="catalog-search-filter-actions"><button className="ghost-button" type="button" disabled={!activeFilterCount && !Object.values(draftFilters).some(Boolean)} onClick={resetFilters}>{tx("Clear all", "Tümünü temizle")}</button><button className="primary-button" type="button" disabled={loading} onClick={() => { setAppliedFilters(draftFilters); setAppliedQuery(query.trim()); setPage(0); clearSelection(); }}>{tx("Apply filters", "Filtreleri uygula")}</button></div>
    </CollapsiblePanel>}
    <div className="catalog-product-search-meta"><span><strong>{formatValue(data?.totalElements ?? 0)}</strong> {tx("matching products", "eşleşen ürün")}</span>{activeFilterCount > 0 && <div className="catalog-search-active-filters">{appliedQuery && <span>{tx("Search", "Arama")}: {appliedQuery}</span>}{Object.entries(appliedFilters).filter(([, value]) => value).map(([key, value]) => <span key={key}>{humanizeFeature(value)}</span>)}</div>}{activeFilterCount > 0 && <button className="text-button" type="button" onClick={resetFilters}>{tx("Clear all", "Tümünü temizle")}</button>}</div>
    <div className="catalog-product-results" aria-live="polite">
      {rows.map((product) => <button className={`catalog-product-result${selected?.id === product.id ? " selected" : ""}`} key={product.id ?? product.normalizedBarcode} type="button" onClick={() => choose(product)}>
        <div className="catalog-product-thumb">{product.displayImageUrl || product.imageUrl || product.externalImageUrl ? <img src={product.displayImageUrl ?? product.imageUrl ?? product.externalImageUrl} alt="" /> : <span>{(productName(product) || "?").charAt(0)}</span>}</div>
        <div><strong>{productName(product) || tx("Unnamed product", "Adsız ürün")}</strong><span>{product.brand || tx("No brand", "Marka yok")}</span><small>#{product.id} · {product.normalizedBarcode ?? product.barcode ?? tx("No barcode", "Barkod yok")}</small></div>
        <div className="catalog-product-result-status"><Badge value={product.verificationStatus} tone={product.verificationStatus === "VERIFIED" ? "good" : product.verificationStatus === "REJECTED" ? "danger" : "warn"} /><small>{product.marketRegion ?? "GLOBAL"}</small></div>
      </button>)}
      {!loading && !rows.length && <div className="empty-state"><strong>{tx("No catalog product found", "Katalog ürünü bulunamadı")}</strong><span>{tx("Try a product name, brand, exact barcode or numeric product ID.", "Ürün adı, marka, tam barkod veya sayısal ürün kimliği deneyin.")}</span></div>}
    </div>
    {!compact && (data?.totalPages ?? 0) > 1 && <div className="catalog-product-search-pagination"><button className="ghost-button" disabled={page <= 0} onClick={() => setPage((value) => value - 1)} type="button">{tx("Previous", "Önceki")}</button><span>{page + 1} / {data?.totalPages}</span><button className="ghost-button" disabled={Boolean(data?.last)} onClick={() => setPage((value) => value + 1)} type="button">{tx("Next", "Sonraki")}</button></div>}
    {detailProduct && <CatalogProductDetailModal product={detailProduct} canManage={canManage} onClose={() => setDetailProduct(null)} onSaved={(updated) => { setDetailProduct(updated); setSelected(updated); setData(current => current ? { ...current, content: current.content?.map(item => item.id === updated.id ? updated : item) } : current); }} />}
  </div>;
}

function FilterSelect({ label, value, values, all, labelFor = humanizeFeature, onChange }: { label: string; value: string; values: string[]; all: string; labelFor?: (value: string) => string; onChange: (value: string) => void }) {
  return <label><span>{label}</span><select value={value} onChange={event => onChange(event.target.value)}><option value="">{all}</option>{values.map(item => <option key={item} value={item}>{labelFor(item)}</option>)}</select></label>;
}
