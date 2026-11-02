import { FormEvent, useEffect, useMemo, useState } from "react";
import { formatRequestError, PageResponse, request } from "../api";
import { useAdminLocale } from "../admin/locale";
import { FoodProduct, FoodProductFamily, FoodProductFamilyPreview } from "../types";

type SelectedMember = {
  product: FoodProduct;
  packageQuantity: string;
  packageUnit: "GRAM" | "MILLILITER";
  packageLabel: string;
  defaultVariant: boolean;
  activeFamily?: FoodProductFamily;
};

type FamilyPage = PageResponse<FoodProductFamily>;
type FamilyMembership = { productId: number; familyId: number; familyKey: string; displayName: string };

const productName = (product: FoodProduct) => product.displayName || product.productName || product.name || `#${product.id}`;
const stableSlug = (value: string) => value.toLocaleLowerCase("en").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").replace(/\b\d+(?:[.,]\d+)?\s*(?:g|gr|kg|ml|l)\b/gi, " ").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
const normalizeFamilyKey = (value: string) => value.toLocaleLowerCase("en").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9:_-]+/g, "-").replace(/-{2,}/g, "-").replace(/^[-:]|[-:]$/g, "");
const readyAmountLabel = (quantity: string, unit: "GRAM" | "MILLILITER") => {
  const value = Number(quantity);
  if (!Number.isFinite(value) || value <= 0) return "";
  if (unit === "MILLILITER" && value >= 1000) return `${value / 1000} L`;
  if (unit === "GRAM" && value >= 1000) return `${value / 1000} kg`;
  return `${value} ${unit === "MILLILITER" ? "ml" : "g"}`;
};
const servingUnitForReference = (reference: string | undefined) => reference === "PER_100ML" ? "MILLILITER" : reference === "PER_SERVING" ? "SERVING" : "GRAM";
const neutralProductSlug = (product: FoodProduct, displayName: string) => {
  const name = stableSlug(displayName || productName(product));
  const brand = stableSlug(product.brand || "");
  return brand && name.startsWith(`${brand}-`) ? name.slice(brand.length + 1) : name;
};
const initialPackage = (product: FoodProduct) => {
  const unit = String(product.servingUnit || "").trim().toUpperCase();
  if (["G", "GRAM", "GRAMS"].includes(unit) && Number(product.servingSize) > 0) return { packageQuantity: String(product.servingSize), packageUnit: "GRAM" as const, packageLabel: readyAmountLabel(String(product.servingSize), "GRAM") };
  if (["ML", "MILLILITER", "MILLILITERS"].includes(unit) && Number(product.servingSize) > 0) return { packageQuantity: String(product.servingSize), packageUnit: "MILLILITER" as const, packageLabel: readyAmountLabel(String(product.servingSize), "MILLILITER") };
  const packageUnit = product.nutritionReferenceUnit === "PER_100ML" ? "MILLILITER" as const : "GRAM" as const;
  return { packageQuantity: "", packageUnit, packageLabel: "" };
};
const errorsForProduct = (errors: string[] | undefined, productId: number | undefined) => !productId ? [] : (errors || []).filter(error => new RegExp(`(^|\\D)${productId}(\\D|$)`).test(error));
const validPackageQuantity = (value: string) => /^\d{1,9}(?:\.\d{1,3})?$/.test(value) && Number(value) > 0;
const comparisonFields: Array<{ key: keyof FoodProduct; en: string; tr: string }> = [
  { key: "brand", en: "Brand", tr: "Marka" }, { key: "normalizedBarcode", en: "Barcode", tr: "Barkod" },
  { key: "calories", en: "Calories", tr: "Kalori" }, { key: "protein", en: "Protein", tr: "Protein" },
  { key: "carbs", en: "Carbs", tr: "Karbonhidrat" }, { key: "fat", en: "Fat", tr: "Yağ" },
  { key: "nutritionReferenceUnit", en: "Nutrition reference", tr: "Besin referansı" },
  { key: "servingSize", en: "Serving size", tr: "Porsiyon miktarı" }, { key: "servingUnit", en: "Serving unit", tr: "Porsiyon birimi" },
  { key: "preparationState", en: "Preparation", tr: "Hazırlama" }, { key: "catalogType", en: "Catalog", tr: "Katalog" },
  { key: "publicationStatus", en: "Publication", tr: "Yayın" }
];

export function ProductFamiliesView({ onError, canManage }: { onError: (message: string | null) => void; canManage: boolean }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, turkish: string) => tr ? turkish : en;
  const [tab, setTab] = useState<"create" | "existing">("create");
  const [query, setQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState<FoodProduct[]>([]);
  const [memberships, setMemberships] = useState<Record<number, FamilyMembership>>({});
  const [selected, setSelected] = useState<SelectedMember[]>([]);
  const [displayName, setDisplayName] = useState("");
  const [familyKey, setFamilyKey] = useState("");
  const [keyEdited, setKeyEdited] = useState(false);
  const [evidenceReason, setEvidenceReason] = useState("");
  const [preview, setPreview] = useState<FoodProductFamilyPreview | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [acknowledged, setAcknowledged] = useState<string[]>([]);
  const [applying, setApplying] = useState(false);
  const [success, setSuccess] = useState<FoodProductFamily | null>(null);
  const [families, setFamilies] = useState<FamilyPage | null>(null);
  const [familyQuery, setFamilyQuery] = useState("");
  const [activeFilter, setActiveFilter] = useState<"" | "true" | "false">("");
  const [familyPage, setFamilyPage] = useState(0);
  const [loadingFamilies, setLoadingFamilies] = useState(false);
  const [detailFamily, setDetailFamily] = useState<FoodProductFamily | null>(null);
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [addMemberQuery, setAddMemberQuery] = useState("");
  const [addMemberResults, setAddMemberResults] = useState<FoodProduct[]>([]);
  const [addMemberMemberships, setAddMemberMemberships] = useState<Record<number, FamilyMembership>>({});
  const [addMemberSearching, setAddMemberSearching] = useState(false);
  const [addMemberCandidate, setAddMemberCandidate] = useState<FoodProduct | null>(null);
  const [addMemberQuantity, setAddMemberQuantity] = useState("");
  const [addMemberUnit, setAddMemberUnit] = useState<"GRAM" | "MILLILITER">("GRAM");
  const [addMemberLabel, setAddMemberLabel] = useState("");
  const [addMemberReason, setAddMemberReason] = useState("");
  const [addMemberPreview, setAddMemberPreview] = useState<FoodProductFamilyPreview | null>(null);
  const [addMemberAcknowledged, setAddMemberAcknowledged] = useState<string[]>([]);
  const [addMemberBusy, setAddMemberBusy] = useState(false);
  const [compatibilityProduct, setCompatibilityProduct] = useState<FoodProduct | null>(null);
  const [compatibilityDraft, setCompatibilityDraft] = useState<FoodProduct | null>(null);
  const [compatibilitySaving, setCompatibilitySaving] = useState(false);
  const [deactivateTarget, setDeactivateTarget] = useState<FoodProductFamily | null>(null);
  const [deactivateReason, setDeactivateReason] = useState("");
  const [deactivating, setDeactivating] = useState(false);

  const requestBody = useMemo(() => ({
    familyKey: familyKey.trim(), displayName: displayName.trim(),
    representativeProductId: selected.find(item => item.defaultVariant)?.product.id,
    evidenceReason: evidenceReason.trim(),
    members: selected.map(item => ({ productId: item.product.id, packageQuantity: Number(item.packageQuantity), packageUnit: item.packageUnit, packageLabel: item.packageLabel.trim(), defaultVariant: item.defaultVariant }))
  }), [displayName, evidenceReason, familyKey, selected]);

  const formReady = selected.length >= 2 && selected.length <= 20 && Boolean(requestBody.representativeProductId) && requestBody.familyKey.length > 0 && requestBody.familyKey.length <= 180 && requestBody.displayName.length > 0 && requestBody.displayName.length <= 300 && evidenceReason.trim().length >= 10 && selected.every(item => validPackageQuantity(item.packageQuantity) && item.packageLabel.trim().length > 0 && item.packageLabel.trim().length <= 120);
  const selectedFamilyConflicts = selected.filter(item => item.activeFamily);
  const comparisonDifferences = useMemo(() => comparisonFields.filter(field => new Set(selected.map(item => String(item.product[field.key] ?? "—"))).size > 1), [selected]);

  useEffect(() => {
    if (keyEdited || !selected.length) return;
    const brand = stableSlug(selected[0].product.brand || "brand");
    const name = neutralProductSlug(selected[0].product, displayName);
    setFamilyKey(`${brand}:${name}`);
  }, [displayName, keyEdited, selected]);

  useEffect(() => {
    setPreview(null); setAcknowledged([]);
    if (!formReady) return;
    const timer = window.setTimeout(async () => {
      setPreviewing(true);
      try { setPreview(await request<FoodProductFamilyPreview>("/api/v1/admin/product-families/preview", { method: "POST", body: requestBody, bypassCache: true })); }
      catch (error) { onError(formatRequestError(error)); }
      finally { setPreviewing(false); }
    }, 450);
    return () => window.clearTimeout(timer);
  }, [formReady, requestBody]);

  useEffect(() => { if (tab === "existing") void loadFamilies(); }, [tab, familyPage, activeFilter]);

  async function search(event: FormEvent) {
    event.preventDefault(); if (!query.trim()) return;
    setSearching(true); onError(null);
    try {
      const page = await request<PageResponse<FoodProduct>>(`/api/v1/admin/products/catalog-search?query=${encodeURIComponent(query.trim())}&page=0&size=20`, { bypassCache: true });
      const products = page.content || [];
      setResults(products);
      const ids = products.map(product => product.id).filter((id): id is number => Boolean(id));
      if (ids.length) {
        try {
          const rows = await request<FamilyMembership[]>(`/api/v1/admin/product-families/memberships?${ids.map(id => `productIds=${id}`).join("&")}`, { bypassCache: true });
          setMemberships(Object.fromEntries(rows.map(row => [row.productId, row])));
        } catch { setMemberships({}); }
      } else setMemberships({});
    } catch (error) { onError(formatRequestError(error)); }
    finally { setSearching(false); }
  }

  async function selectProduct(product: FoodProduct) {
    if (!product.id || selected.some(item => item.product.id === product.id) || selected.length >= 20) return;
    let activeFamily: FoodProductFamily | undefined;
    try {
      const page = await request<FamilyPage>(`/api/v1/admin/product-families?query=${product.id}&active=true&page=0&size=5`, { bypassCache: true });
      activeFamily = page.content?.find(family => family.members.some(member => member.productId === product.id));
    } catch { /* Preview remains authoritative when membership lookup is unavailable. */ }
    const packageDefaults = initialPackage(product);
    setSuccess(null);
    setSelected(items => [...items, { product, ...packageDefaults, defaultVariant: items.length === 0, activeFamily }]);
    if (!displayName) setDisplayName(productName(product));
  }

  function updateMember(id: number, patch: Partial<SelectedMember>) {
    setSelected(items => items.map(item => item.product.id === id ? { ...item, ...patch } : item));
  }

  function updateMemberMeasure(id: number, quantity: string, unit: "GRAM" | "MILLILITER") {
    updateMember(id, { packageQuantity: quantity, packageUnit: unit, packageLabel: readyAmountLabel(quantity, unit) });
  }

  function makeRepresentative(id: number) {
    setSelected(items => items.map(item => ({ ...item, defaultVariant: item.product.id === id })));
  }

  async function applyFamily() {
    if (!preview?.eligible || previewing || acknowledged.length !== preview.warnings.length) return;
    setApplying(true); onError(null);
    try {
      const latest = await request<FoodProductFamilyPreview>("/api/v1/admin/product-families/preview", { method: "POST", body: requestBody, bypassCache: true });
      setPreview(latest);
      if (!latest.eligible || latest.warnings.some(warning => !acknowledged.includes(warning))) return;
      const created = await request<FoodProductFamily>("/api/v1/admin/product-families", { method: "POST", body: { ...requestBody, previewFingerprint: latest.previewFingerprint, acknowledgedWarnings: acknowledged } });
      setSuccess(created); setSelected([]); setDisplayName(""); setFamilyKey(""); setKeyEdited(false); setEvidenceReason(""); setPreview(null); setAcknowledged([]);
    } catch (error) { onError(formatRequestError(error)); }
    finally { setApplying(false); }
  }

  async function loadFamilies() {
    setLoadingFamilies(true);
    try {
      const params = new URLSearchParams({ page: String(familyPage), size: "25" });
      if (familyQuery.trim()) params.set("query", familyQuery.trim());
      if (activeFilter) params.set("active", activeFilter);
      setFamilies(await request<FamilyPage>(`/api/v1/admin/product-families?${params}`, { bypassCache: true }));
    } catch (error) { onError(formatRequestError(error)); }
    finally { setLoadingFamilies(false); }
  }

  async function deactivate() {
    if (!deactivateTarget || deactivateReason.trim().length < 10) return;
    setDeactivating(true);
    try {
      await request(`/api/v1/admin/product-families/${deactivateTarget.id}`, { method: "DELETE", body: { reason: deactivateReason.trim() } });
      setDeactivateTarget(null); setDeactivateReason(""); await loadFamilies();
    } catch (error) { onError(formatRequestError(error)); }
    finally { setDeactivating(false); }
  }

  function resetAddMember() {
    setAddMemberOpen(false); setAddMemberQuery(""); setAddMemberResults([]); setAddMemberMemberships({});
    setAddMemberCandidate(null); setAddMemberQuantity(""); setAddMemberUnit("GRAM"); setAddMemberLabel("");
    setAddMemberReason(""); setAddMemberPreview(null); setAddMemberAcknowledged([]);
  }

  async function searchAddMember(event: FormEvent) {
    event.preventDefault(); if (!addMemberQuery.trim()) return;
    setAddMemberSearching(true); onError(null);
    try {
      const page = await request<PageResponse<FoodProduct>>(`/api/v1/admin/products/catalog-search?query=${encodeURIComponent(addMemberQuery.trim())}&page=0&size=10`, { bypassCache: true });
      const products = page.content || []; setAddMemberResults(products);
      const ids = products.map(product => product.id).filter((id): id is number => Boolean(id));
      if (!ids.length) return setAddMemberMemberships({});
      const rows = await request<FamilyMembership[]>(`/api/v1/admin/product-families/memberships?${ids.map(id => `productIds=${id}`).join("&")}`, { bypassCache: true });
      setAddMemberMemberships(Object.fromEntries(rows.map(row => [row.productId, row])));
    } catch (error) { onError(formatRequestError(error)); }
    finally { setAddMemberSearching(false); }
  }

  function chooseAddMember(product: FoodProduct) {
    const defaults = initialPackage(product); setAddMemberCandidate(product);
    setAddMemberQuantity(defaults.packageQuantity); setAddMemberUnit(defaults.packageUnit); setAddMemberLabel(defaults.packageLabel);
    setAddMemberPreview(null); setAddMemberAcknowledged([]);
  }

  async function previewAddMember() {
    if (!detailFamily || !addMemberCandidate?.id) return;
    setAddMemberBusy(true); onError(null);
    try {
      const result = await request<FoodProductFamilyPreview>(`/api/v1/admin/product-families/${detailFamily.id}/members/preview`, { method: "POST", body: { member: { productId: addMemberCandidate.id, packageQuantity: Number(addMemberQuantity), packageUnit: addMemberUnit, packageLabel: addMemberLabel.trim(), defaultVariant: false }, reason: addMemberReason.trim() }, bypassCache: true });
      setAddMemberPreview(result); setAddMemberAcknowledged([]);
    } catch (error) { onError(formatRequestError(error)); }
    finally { setAddMemberBusy(false); }
  }

  async function applyAddMember() {
    if (!detailFamily || !addMemberCandidate?.id || !addMemberPreview?.eligible || addMemberAcknowledged.length !== addMemberPreview.warnings.length) return;
    setAddMemberBusy(true); onError(null);
    try {
      const updated = await request<FoodProductFamily>(`/api/v1/admin/product-families/${detailFamily.id}/members`, { method: "POST", body: { member: { productId: addMemberCandidate.id, packageQuantity: Number(addMemberQuantity), packageUnit: addMemberUnit, packageLabel: addMemberLabel.trim(), defaultVariant: false }, reason: addMemberReason.trim(), previewFingerprint: addMemberPreview.previewFingerprint, acknowledgedWarnings: addMemberAcknowledged } });
      setDetailFamily(updated); setFamilies(current => current ? { ...current, content: (current.content || []).map(item => item.id === updated.id ? updated : item) } : current); resetAddMember();
    } catch (error) { onError(formatRequestError(error)); }
    finally { setAddMemberBusy(false); }
  }

  function openCompatibilityEditor(product: FoodProduct) {
    setCompatibilityProduct(product); setCompatibilityDraft({ ...product });
  }

  function updateCompatibilityDraft(patch: Partial<FoodProduct>) {
    setCompatibilityDraft(current => {
      if (!current) return current;
      if (patch.nutritionReferenceUnit) {
        const reference = patch.nutritionReferenceUnit;
        return { ...current, ...patch, servingUnit: servingUnitForReference(reference), servingSize: reference === "PER_100ML" ? undefined : current.servingSize };
      }
      if (Object.prototype.hasOwnProperty.call(patch, "servingUnit")) {
        const requested = String(patch.servingUnit || "").trim();
        return { ...current, ...patch, servingUnit: requested ? servingUnitForReference(current.nutritionReferenceUnit) : undefined };
      }
      return { ...current, ...patch };
    });
  }

  async function saveCompatibilityProduct() {
    if (!compatibilityProduct?.id || !compatibilityDraft) return;
    setCompatibilitySaving(true); onError(null);
    try {
      const updated = await request<FoodProduct>(`/api/v1/admin/products/${compatibilityProduct.id}/review`, { method: "PATCH", body: {
        brand: compatibilityDraft.brand ?? null,
        catalogType: compatibilityDraft.catalogType ?? null,
        preparationState: compatibilityDraft.preparationState ?? null,
        nutritionBasis: compatibilityDraft.nutritionBasis ?? null,
        nutritionReferenceUnit: compatibilityDraft.nutritionReferenceUnit ?? null,
        calories: compatibilityDraft.calories ?? null,
        protein: compatibilityDraft.protein ?? null,
        carbs: compatibilityDraft.carbs ?? null,
        fat: compatibilityDraft.fat ?? null,
        servingSizeGrams: compatibilityDraft.servingSize ?? null,
        servingUnit: compatibilityDraft.servingUnit ?? null,
        clearServingSize: compatibilityDraft.servingSize == null,
        clearServingUnit: !compatibilityDraft.servingUnit,
        reviewNote: "Updated inline while resolving package-family compatibility."
      }});
      setSelected(items => items.map(item => item.product.id === updated.id ? { ...item, product: updated } : item));
      setResults(items => items.map(item => item.id === updated.id ? updated : item));
      setCompatibilityProduct(null); setCompatibilityDraft(null); setPreview(null); setAcknowledged([]);
    } catch (error) { onError(formatRequestError(error)); }
    finally { setCompatibilitySaving(false); }
  }

  return <div className="stack product-families-page">
    <header className="panel product-family-hero"><div><span className="eyebrow">{tx("PRODUCT MANAGEMENT", "ÜRÜN YÖNETİMİ")}</span><h1>{tx("Package families", "Paket aileleri")}</h1><p>{tx("Group physical package variants for name search without changing products or barcodes.", "Fiziksel paket seçeneklerini ürünleri veya barkodları değiştirmeden ad araması için gruplayın.")}</p></div><div className="product-family-safety">{tx("Non-destructive · Reversible · Audited", "Silmez · Geri alınabilir · Denetlenir")}</div></header>
    <div className="section-tabs product-family-tabs" role="tablist"><button role="tab" aria-selected={tab === "create"} className={tab === "create" ? "active" : ""} onClick={() => setTab("create")}>{tx("Create family", "Aile oluştur")}</button><button role="tab" aria-selected={tab === "existing"} className={tab === "existing" ? "active" : ""} onClick={() => setTab("existing")}>{tx("Existing families", "Mevcut aileler")}</button></div>
    {tab === "create" ? <>
      <nav className="product-family-stepper" aria-label={tx("Family creation progress", "Aile oluşturma ilerlemesi")}><a href="#family-step-1" className={selected.length >= 2 ? "complete" : "active"}><span>1</span><div><strong>{tx("Select products", "Ürünleri seç")}</strong><small>{selected.length >= 2 ? tx("Complete", "Tamamlandı") : `${selected.length}/2 ${tx("minimum", "minimum")}`}</small></div></a><a href="#family-step-2" className={selected.length >= 2 ? comparisonDifferences.length ? "attention" : "complete" : "locked"}><span>2</span><div><strong>{tx("Compare", "Karşılaştır")}</strong><small>{selected.length < 2 ? tx("Waiting", "Bekliyor") : comparisonDifferences.length ? tx(`${comparisonDifferences.length} differences`, `${comparisonDifferences.length} fark`) : tx("Compatible", "Uyumlu")}</small></div></a><a href="#family-step-3" className={selected.length < 2 ? "locked" : preview?.eligible && !selectedFamilyConflicts.length ? "complete" : "attention"}><span>3</span><div><strong>{tx("Define and review", "Tanımla ve incele")}</strong><small>{preview?.eligible && !selectedFamilyConflicts.length ? tx("Ready", "Hazır") : tx("Action required", "İşlem gerekli")}</small></div></a></nav>
      {selectedFamilyConflicts.length > 0 && <div className="product-family-conflict-banner" role="alert"><div><strong>{tx("A selected product already belongs to an active family", "Seçilen bir ürün zaten etkin bir aileye üye")}</strong><span>{tx("Remove it from this draft or deactivate its current family before continuing.", "Devam etmek için ürünü bu taslaktan çıkarın veya mevcut ailesini devre dışı bırakın.")}</span></div><div>{selectedFamilyConflicts.map(item => <span key={item.product.id}>#{item.product.id} · {item.activeFamily?.displayName}</span>)}</div></div>}
      <section className="panel product-family-step-panel" id="family-step-1"><div className="modern-section-heading"><div><span className="product-family-step-number">1</span><h2>{tx("Search and select products", "Ürünleri ara ve seç")}</h2><p>{tx("Select 2-20 independently packaged products.", "Bağımsız paketlenmiş 2-20 ürün seçin.")}</p></div><strong className="product-family-selection-count">{selected.length}/20</strong></div>
        <form className="product-family-search" onSubmit={search}><input value={query} onChange={event => setQuery(event.target.value)} placeholder={tx("Name, brand, barcode, source key or product ID", "Ad, marka, barkod, kaynak anahtarı veya ürün kimliği")} /><button className="primary-button" disabled={searching}>{searching ? tx("Searching…", "Aranıyor…") : tx("Search", "Ara")}</button></form>
        <div className="product-family-search-results">{results.map(product => { const membership = product.id ? memberships[product.id] : undefined; const blocked = product.publicationStatus !== "PUBLISHED" || product.verificationStatus === "REJECTED" || product.catalogType !== "BRANDED_PRODUCT" || Boolean(membership); return <article key={product.id} className={blocked ? "product-family-result blocked" : "product-family-result"}><div><strong>{productName(product)}</strong><span>{product.brand || "—"} · #{product.id} · {product.normalizedBarcode || product.barcode || "—"}</span><small>{product.dataSource || "—"} / {product.sourceKey || "—"} · {product.marketRegion || "—"} · {product.publicationStatus || "—"} · {product.verificationStatus || "—"}</small>{membership && <small className="inline-error">{tx(`Active family: ${membership.displayName} (#${membership.familyId})`, `Etkin aile: ${membership.displayName} (#${membership.familyId})`)}</small>}</div><button className="ghost-button" type="button" disabled={blocked || selected.some(item => item.product.id === product.id) || selected.length >= 20} onClick={() => void selectProduct(product)}>{blocked ? tx("Not eligible", "Uygun değil") : selected.some(item => item.product.id === product.id) ? tx("Selected", "Seçildi") : tx("Add", "Ekle")}</button></article>; })}</div>
      </section>
      {selected.length > 0 && <section className="panel product-family-step-panel" id="family-step-2"><div className="modern-section-heading"><div><span className="product-family-step-number">2</span><h2>{tx("Compare selected products", "Seçilen ürünleri karşılaştır")}</h2><p>{tx("Review only meaningful differences before defining the family.", "Aileyi tanımlamadan önce yalnızca anlamlı farkları inceleyin.")}</p></div><span className={comparisonDifferences.length ? "status-pill warning" : "status-pill success"}>{comparisonDifferences.length ? tx(`${comparisonDifferences.length} differences`, `${comparisonDifferences.length} fark`) : tx("Compatible", "Uyumlu")}</span></div><div className="product-family-selected-strip">{selected.map(item => <div key={item.product.id} className={item.activeFamily ? "conflict" : ""}><span><strong>{productName(item.product)}</strong><small>#{item.product.id} · {item.product.normalizedBarcode || item.product.barcode || "—"}</small></span><button className="text-button" aria-label={tx(`Remove ${productName(item.product)}`, `${productName(item.product)} ürününü kaldır`)} onClick={() => setSelected(items => items.filter(current => current.product.id !== item.product.id))}>×</button></div>)}</div>{comparisonDifferences.length > 0 && <div className="product-family-difference-summary"><strong>{tx("Fields requiring review", "İncelenmesi gereken alanlar")}</strong><div>{comparisonDifferences.map(field => <span key={field.key}>{tx(field.en, field.tr)}</span>)}</div></div>}<details className="product-family-comparison-details"><summary>{tx("Open full field comparison", "Tüm alan karşılaştırmasını aç")}</summary><div className="product-family-comparison-wrap"><table className="product-family-comparison"><thead><tr><th>{tx("Field", "Alan")}</th>{selected.map(item => <th key={item.product.id}>{productName(item.product)}</th>)}</tr></thead><tbody>{comparisonFields.map(field => { const values = selected.map(item => String(item.product[field.key] ?? "—")); const differs = new Set(values).size > 1; return <tr key={field.key} className={differs ? "differs" : ""}><th>{tx(field.en, field.tr)}</th>{values.map((value, index) => <td key={`${field.key}-${selected[index].product.id}`}>{value}</td>)}</tr>; })}</tbody></table></div></details></section>}
      {selected.length >= 2 && <section className="panel product-family-step-panel" id="family-step-3"><div className="modern-section-heading"><div><span className="product-family-step-number">3</span><h2>{tx("Define and preview family", "Aileyi tanımla ve önizle")}</h2><p>{tx("Resolve product issues, complete package values and run the final preview.", "Ürün sorunlarını çözün, paket değerlerini tamamlayın ve son önizlemeyi çalıştırın.")}</p></div><span className={previewing ? "status-pill" : preview?.eligible && !selectedFamilyConflicts.length ? "status-pill success" : "status-pill warning"}>{previewing ? tx("Checking…", "Kontrol ediliyor…") : preview?.eligible && !selectedFamilyConflicts.length ? tx("Ready to create", "Oluşturmaya hazır") : tx("Action required", "İşlem gerekli")}</span></div>
        <div className="product-family-form-grid"><label>{tx("Family display name", "Aile görünen adı")}<input maxLength={300} value={displayName} onChange={event => setDisplayName(event.target.value)} /></label><label>{tx("Stable family key", "Kalıcı aile anahtarı")}<input maxLength={180} value={familyKey} onChange={event => { setFamilyKey(normalizeFamilyKey(event.target.value)); setKeyEdited(true); }} /></label><label className="span-2">{tx("Evidence / review reason", "Kanıt / inceleme nedeni")}<textarea minLength={10} maxLength={1000} value={evidenceReason} onChange={event => setEvidenceReason(event.target.value)} placeholder={tx("Explain why these are package-only variants (10-1000 characters).", "Bunların neden yalnızca paket seçeneği olduğunu açıklayın (10-1000 karakter).")}/><small>{evidenceReason.trim().length}/1000</small></label></div>
        <div className="product-family-member-editor">{selected.map(item => { const rowErrors = errorsForProduct(preview?.errors, item.product.id); const needsAttention = Boolean(item.activeFamily || rowErrors.length); return <article key={item.product.id} className={needsAttention ? "blocked" : ""}><div className="product-family-member-identity"><div><strong>{productName(item.product)}</strong><small>#{item.product.id} · {item.product.normalizedBarcode || item.product.barcode}</small></div>{item.activeFamily && <span className="inline-error">{tx(`Already in family ${item.activeFamily.displayName}`, `Zaten ${item.activeFamily.displayName} ailesinde`)}</span>}{rowErrors.map(error => <span className="inline-error" key={error}>{error}</span>)}{needsAttention && <div className="product-family-quick-actions">{rowErrors.length > 0 && <button className="primary-button" type="button" onClick={() => openCompatibilityEditor(item.product)}>{tx("Fix compatibility fields", "Uyumluluk alanlarını düzenle")}</button>}{item.activeFamily && <button className="danger-button" type="button" onClick={() => setSelected(items => items.filter(current => current.product.id !== item.product.id))}>{tx("Remove from draft", "Tas­laktan çıkar")}</button>}</div>}</div><label className="product-family-representative"><input type="radio" name="representative" checked={item.defaultVariant} onChange={() => makeRepresentative(item.product.id!)} /> {tx("Representative", "Temsilci")}</label><label>{tx("Quantity", "Miktar")}<input type="number" min="0.001" max="999999999.999" step="0.001" value={item.packageQuantity} onChange={event => updateMemberMeasure(item.product.id!, event.target.value, item.packageUnit)}/></label><label>{tx("Unit", "Birim")}<select value={item.packageUnit} onChange={event => updateMemberMeasure(item.product.id!, item.packageQuantity, event.target.value as "GRAM" | "MILLILITER")}><option value="GRAM">GRAM</option><option value="MILLILITER">MILLILITER</option></select></label><label>{tx("Ready amount label", "Hazır miktar etiketi")}<input readOnly value={item.packageLabel}/></label></article>; })}</div>
        {preview && <div className={preview.eligible ? "product-family-preview" : "product-family-preview blocked"}><h3>{preview.eligible ? tx("Preview passed", "Önizleme geçti") : tx("Preview blocked", "Önizleme engellendi")}</h3>{preview.errors.map(error => <p className="inline-error" key={error}>{error}</p>)}{preview.warnings.map(warning => <label className="product-family-warning" key={warning}><input type="checkbox" checked={acknowledged.includes(warning)} onChange={event => setAcknowledged(items => event.target.checked ? [...items, warning] : items.filter(item => item !== warning))}/><span><strong>{tx("Source evidence required", "Kaynak kanıtı gerekli")}</strong>{warning}</span></label>)}</div>}
        <div className="product-family-actions"><span>{tx("A fresh preview runs immediately before apply.", "Uygulamadan hemen önce yeni bir önizleme çalışır.")}</span><button className="primary-button" disabled={!canManage || applying || previewing || !preview?.eligible || acknowledged.length !== preview.warnings.length || selected.some(item => item.activeFamily)} onClick={() => void applyFamily()}>{applying ? tx("Creating…", "Oluşturuluyor…") : tx("Create package family", "Paket ailesi oluştur")}</button></div>
      </section>}
      {success && <div className="success-banner" role="status">{tx(`Family #${success.id} created with ${success.members.length} package variants.`, `#${success.id} ailesi ${success.members.length} paket seçeneğiyle oluşturuldu.`)}</div>}
    </> : <section className="panel product-family-existing">
      <div className="modern-section-heading product-family-existing-heading">
        <div><span className="eyebrow">{tx("FAMILY DIRECTORY", "AİLE DİZİNİ")}</span><h2>{tx("Existing families", "Mevcut aileler")}</h2><p>{tx("Inspect active and reverted family audit history.", "Etkin ve geri alınmış ailelerin denetim geçmişini inceleyin.")}</p></div>
        <div className="product-family-count"><strong>{families?.totalElements || 0}</strong><span>{tx("families", "aile")}</span></div>
      </div>
      <form className="product-family-search product-family-directory-search" onSubmit={event => { event.preventDefault(); setFamilyPage(0); void loadFamilies(); }}>
        <label className="product-family-search-field"><span>{tx("Search families", "Ailelerde ara")}</span><input value={familyQuery} onChange={event => setFamilyQuery(event.target.value)} placeholder={tx("Family, product, barcode or admin", "Aile, ürün, barkod veya yönetici")}/></label>
        <label><span>{tx("Status", "Durum")}</span><select value={activeFilter} onChange={event => { setActiveFilter(event.target.value as "" | "true" | "false"); setFamilyPage(0); }}><option value="">{tx("All states", "Tüm durumlar")}</option><option value="true">{tx("Active", "Etkin")}</option><option value="false">{tx("Inactive", "Devre dışı")}</option></select></label>
        <button className="primary-button">{tx("Search", "Ara")}</button>
      </form>
      {loadingFamilies ? <div className="product-family-empty" role="status">{tx("Loading families…", "Aileler yükleniyor…")}</div> : !families?.content?.length ? <div className="product-family-empty"><strong>{tx("No families found", "Aile bulunamadı")}</strong><span>{tx("Try changing the search or status filter.", "Arama veya durum filtresini değiştirmeyi deneyin.")}</span></div> : <div className="product-family-list product-family-compact-list">{families.content.map(family => <article key={family.id} className={family.active ? "active" : "inactive"}>
        <button type="button" className="product-family-row-main" onClick={() => setDetailFamily(family)} aria-label={tx(`Open details for ${family.displayName}`, `${family.displayName} ayrıntılarını aç`)}>
          <span className={family.active ? "status-pill success" : "status-pill"}>{family.active ? tx("Active", "Etkin") : tx("Reverted", "Geri alındı")}</span>
          <span className="product-family-row-title"><strong>{family.displayName}</strong><code>{family.familyKey}</code></span>
          <span className="product-family-row-stat"><small>{tx("Products", "Ürünler")}</small><strong>{family.members.length}</strong></span>
          <span className="product-family-row-stat product-family-row-admin"><small>{tx("Applied by", "Uygulayan")}</small><strong>{family.appliedBy}</strong></span>
          <span className="product-family-row-stat"><small>{tx("Applied at", "Uygulama zamanı")}</small><strong>{new Date(family.appliedAt).toLocaleDateString(locale)}</strong></span>
          <span className="product-family-row-open">{tx("View details", "Detayları aç")} <b aria-hidden="true">→</b></span>
        </button>
      </article>)}</div>}
      <div className="product-family-pagination"><span>{tx(`${families?.totalElements || 0} results`, `${families?.totalElements || 0} sonuç`)}</span><div><button className="ghost-button" disabled={!families || familyPage === 0} onClick={() => setFamilyPage(page => page - 1)}>{tx("Previous", "Önceki")}</button><strong>{familyPage + 1} / {Math.max(families?.totalPages || 1, 1)}</strong><button className="ghost-button" disabled={!families || Boolean(families.last)} onClick={() => setFamilyPage(page => page + 1)}>{tx("Next", "Sonraki")}</button></div></div>
    </section>}
    {compatibilityProduct && compatibilityDraft && <div className="modal-backdrop product-family-compatibility-backdrop" role="presentation"><section className="modal product-family-compatibility-modal" role="dialog" aria-modal="true" aria-labelledby="compatibility-title"><header><div><span className="eyebrow">{tx("FAMILY COMPATIBILITY", "AİLE UYUMLULUĞU")}</span><h2 id="compatibility-title">{tx("Edit preview-critical fields", "Önizlemeyi etkileyen alanları düzenle")}</h2><p>{productName(compatibilityProduct)} · #{compatibilityProduct.id}</p></div><button className="product-family-detail-close" onClick={() => { setCompatibilityProduct(null); setCompatibilityDraft(null); }} aria-label={tx("Close", "Kapat")}>×</button></header><div className="product-family-compatibility-body"><div className="product-family-scope-note"><strong>{tx("Limited edit scope", "Sınırlı düzenleme alanı")}</strong><span>{tx("Only fields used by package-family validation are shown. Other product data remains unchanged.", "Yalnızca paket ailesi doğrulamasında kullanılan alanlar gösterilir. Diğer ürün verileri değişmeden kalır.")}</span></div><section><h3>{tx("Identity and classification", "Kimlik ve sınıflandırma")}</h3><div className="product-family-compatibility-grid"><label>{tx("Brand", "Marka")}<input value={compatibilityDraft.brand || ""} onChange={event => updateCompatibilityDraft({ brand: event.target.value })}/></label><label>{tx("Catalog type", "Katalog tipi")}<select value={compatibilityDraft.catalogType || "BRANDED_PRODUCT"} onChange={event => updateCompatibilityDraft({ catalogType: event.target.value })}><option value="BRANDED_PRODUCT">BRANDED_PRODUCT</option><option value="GENERIC_FOOD">GENERIC_FOOD</option></select></label><label>{tx("Preparation state", "Hazırlama durumu")}<select value={compatibilityDraft.preparationState || "UNSPECIFIED"} onChange={event => updateCompatibilityDraft({ preparationState: event.target.value })}>{["UNSPECIFIED","RAW","COOKED","BOILED","GRILLED","FRIED","BAKED","ROASTED","STEAMED","PREPARED"].map(value => <option key={value}>{value}</option>)}</select></label><label>{tx("Nutrition reference", "Besin referansı")}<select value={compatibilityDraft.nutritionReferenceUnit || "PER_100G"} onChange={event => updateCompatibilityDraft({ nutritionReferenceUnit: event.target.value })}>{["PER_100G","PER_100ML","PER_SERVING"].map(value => <option key={value}>{value}</option>)}</select></label><label>{tx("Nutrition basis", "Besin kaynağı")}</label><select value={compatibilityDraft.nutritionBasis || "SOURCE_REPORTED"} onChange={event => updateCompatibilityDraft({ nutritionBasis: event.target.value })}>{["SOURCE_REPORTED","CALCULATED","ESTIMATED"].map(value => <option key={value}>{value}</option>)}</select></div></section><section><h3>{tx("Core nutrition", "Temel besin değerleri")}</h3><div className="product-family-compatibility-grid nutrition">{([['calories',tx('Calories','Kalori'),'kcal'],['protein',tx('Protein','Protein'),'g'],['carbs',tx('Carbs','Karbonhidrat'),'g'],['fat',tx('Fat','Yağ'),'g']] as const).map(([key,label,unit]) => <label key={key}>{label}<span><input type="number" min="0" step="0.001" value={compatibilityDraft[key] ?? ""} onChange={event => updateCompatibilityDraft({ [key]: event.target.value === "" ? undefined : Number(event.target.value) })}/><b>{unit}</b></span></label>)}</div></section><section><h3>{tx("Serving conversion", "Porsiyon dönüşümü")}</h3><div className="product-family-compatibility-grid"><label>{tx("Serving size", "Porsiyon miktarı")}<input type="number" min="0" step="0.001" value={compatibilityDraft.servingSize ?? ""} onChange={event => updateCompatibilityDraft({ servingSize: event.target.value === "" ? undefined : Number(event.target.value) })}/></label><label>{tx("Serving unit", "Porsiyon birimi")}<input value={compatibilityDraft.servingUnit || ""} onChange={event => updateCompatibilityDraft({ servingUnit: event.target.value })}/></label></div></section></div><footer><button className="ghost-button" onClick={() => { setCompatibilityProduct(null); setCompatibilityDraft(null); }}>{tx("Cancel", "İptal")}</button><button className="primary-button" disabled={compatibilitySaving || !compatibilityDraft.brand?.trim()} onClick={() => void saveCompatibilityProduct()}>{compatibilitySaving ? tx("Saving…", "Kaydediliyor…") : tx("Save and rerun preview", "Kaydet ve önizlemeyi yenile")}</button></footer></section></div>}
    {detailFamily && <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setDetailFamily(null); }}><section className="modal product-family-detail-modal" role="dialog" aria-modal="true" aria-labelledby="family-detail-title">
      <header className="product-family-detail-header"><div><div className="product-family-title-row"><span className={detailFamily.active ? "status-pill success" : "status-pill"}>{detailFamily.active ? tx("Active", "Etkin") : tx("Reverted", "Geri alındı")}</span><span className="product-family-id">#{detailFamily.id}</span></div><h2 id="family-detail-title">{detailFamily.displayName}</h2><code>{detailFamily.familyKey}</code></div><button className="product-family-detail-close" onClick={() => setDetailFamily(null)} aria-label={tx("Close", "Kapat")}>×</button></header>
      <div className="product-family-detail-body"><div className="product-family-reason"><span>{tx("Review evidence", "İnceleme kanıtı")}</span><p>{detailFamily.evidenceReason}</p></div><div className="product-family-audit"><div><span>{tx("Applied by", "Uygulayan")}</span><strong>{detailFamily.appliedBy}</strong></div><div><span>{tx("Applied at", "Uygulama zamanı")}</span><strong>{new Date(detailFamily.appliedAt).toLocaleString(locale)}</strong></div><div><span>{tx("Products", "Ürünler")}</span><strong>{detailFamily.members.length}</strong></div>{detailFamily.revertedAt && <div className="product-family-revert"><span>{tx("Reverted", "Geri alındı")}</span><strong>{detailFamily.revertedBy} · {new Date(detailFamily.revertedAt).toLocaleString(locale)}</strong><small>{detailFamily.revertReason}</small></div>}</div><div className="product-family-members">{detailFamily.members.map(member => <div key={member.productId} className={member.defaultVariant ? "default" : ""}><div><strong>{member.displayName || `#${member.productId}`}</strong>{member.defaultVariant && <span>{tx("Representative", "Temsilci")}</span>}</div><small>#{member.productId} · {member.barcode || "—"}</small><b>{member.packageLabel}</b></div>)}</div>
        {addMemberOpen && <section className="product-family-add-member"><header><div><span className="eyebrow">{tx("ADD MEMBER", "ÜYE EKLE")}</span><h3>{tx("Add a product to this family", "Bu aileye ürün ekle")}</h3><p>{tx("Search the full catalog, verify package data and preview compatibility before applying.", "Tüm katalogda arayın, paket bilgisini doğrulayın ve uygulamadan önce uyumluluğu önizleyin.")}</p></div><button className="text-button" onClick={resetAddMember}>×</button></header>
          <form className="product-family-add-search" onSubmit={searchAddMember}><input value={addMemberQuery} onChange={event => setAddMemberQuery(event.target.value)} placeholder={tx("Product name, barcode or ID", "Ürün adı, barkod veya kimlik")}/><button className="primary-button" disabled={addMemberSearching}>{addMemberSearching ? tx("Searching…", "Aranıyor…") : tx("Search", "Ara")}</button></form>
          {!addMemberCandidate && <div className="product-family-add-results">{addMemberResults.map(product => { const membership = product.id ? addMemberMemberships[product.id] : undefined; const alreadyHere = detailFamily.members.some(member => member.productId === product.id); const blocked = Boolean(membership || alreadyHere || product.publicationStatus !== "PUBLISHED" || product.catalogType !== "BRANDED_PRODUCT"); return <button type="button" key={product.id} disabled={blocked} onClick={() => chooseAddMember(product)}><span><strong>{productName(product)}</strong><small>#{product.id} · {product.brand || "—"} · {product.normalizedBarcode || product.barcode || "—"}</small></span><b>{alreadyHere ? tx("Already in this family", "Zaten bu ailede") : membership ? tx(`In ${membership.displayName}`, `${membership.displayName} ailesinde`) : blocked ? tx("Not eligible", "Uygun değil") : tx("Select", "Seç")}</b></button>; })}</div>}
          {addMemberCandidate && <div className="product-family-add-editor"><div className="product-family-add-selected"><span><strong>{productName(addMemberCandidate)}</strong><small>#{addMemberCandidate.id} · {addMemberCandidate.brand || "—"} · {addMemberCandidate.normalizedBarcode || addMemberCandidate.barcode || "—"}</small></span><button className="ghost-button" onClick={() => { setAddMemberCandidate(null); setAddMemberPreview(null); }}>{tx("Change", "Değiştir")}</button></div><div className="product-family-add-fields"><label>{tx("Quantity", "Miktar")}<input type="number" min="0.001" step="0.001" value={addMemberQuantity} onChange={event => { const quantity = event.target.value; setAddMemberQuantity(quantity); setAddMemberLabel(readyAmountLabel(quantity, addMemberUnit)); setAddMemberPreview(null); }}/></label><label>{tx("Unit", "Birim")}<select value={addMemberUnit} onChange={event => { const unit = event.target.value as "GRAM" | "MILLILITER"; setAddMemberUnit(unit); setAddMemberLabel(readyAmountLabel(addMemberQuantity, unit)); setAddMemberPreview(null); }}><option value="GRAM">GRAM</option><option value="MILLILITER">MILLILITER</option></select></label><label>{tx("Ready amount label", "Hazır miktar etiketi")}<input readOnly value={addMemberLabel}/></label><label className="span-all">{tx("Addition reason / evidence", "Ekleme nedeni / kanıt")}<textarea minLength={10} maxLength={1000} value={addMemberReason} onChange={event => { setAddMemberReason(event.target.value); setAddMemberPreview(null); }} placeholder={tx("Explain why this is another package variant of the same product.", "Bu ürünün neden aynı ürünün başka bir paket seçeneği olduğunu açıklayın.")}/></label></div>
            {addMemberPreview && <div className={addMemberPreview.eligible ? "product-family-preview" : "product-family-preview blocked"}><strong>{addMemberPreview.eligible ? tx("Compatible with family", "Aileyle uyumlu") : tx("Cannot be added", "Eklenemez")}</strong>{addMemberPreview.errors.map(error => <span className="inline-error" key={error}>{error}</span>)}{addMemberPreview.warnings.map(warning => <label className="product-family-warning" key={warning}><input type="checkbox" checked={addMemberAcknowledged.includes(warning)} onChange={event => setAddMemberAcknowledged(items => event.target.checked ? [...items, warning] : items.filter(item => item !== warning))}/><span>{warning}</span></label>)}</div>}
            <div className="product-family-add-actions"><button className="ghost-button" disabled={addMemberBusy || !validPackageQuantity(addMemberQuantity) || !addMemberLabel.trim() || addMemberReason.trim().length < 10} onClick={() => void previewAddMember()}>{addMemberBusy ? tx("Checking…", "Kontrol ediliyor…") : tx("Preview compatibility", "Uyumluluğu önizle")}</button><button className="primary-button" disabled={addMemberBusy || !addMemberPreview?.eligible || addMemberAcknowledged.length !== addMemberPreview.warnings.length} onClick={() => void applyAddMember()}>{addMemberBusy ? tx("Adding…", "Ekleniyor…") : tx("Add to family", "Aileye ekle")}</button></div></div>}
        </section>}
      </div>
      <footer className="product-family-detail-actions"><button className="ghost-button" onClick={() => { resetAddMember(); setDetailFamily(null); }}>{tx("Close", "Kapat")}</button>{detailFamily.active && canManage && !addMemberOpen && <button className="primary-button" onClick={() => setAddMemberOpen(true)}>{tx("Add product to family", "Aileye ürün ekle")}</button>}{detailFamily.active && canManage && <button className="danger-button" onClick={() => { resetAddMember(); setDeactivateTarget(detailFamily); setDetailFamily(null); }}>{tx("Deactivate family", "Aileyi devre dışı bırak")}</button>}</footer>
    </section></div>}
    {deactivateTarget && <div className="modal-backdrop" role="presentation"><section className="modal product-family-deactivate" role="dialog" aria-modal="true" aria-labelledby="deactivate-family-title"><h2 id="deactivate-family-title">{tx("Deactivate package family", "Paket ailesini devre dışı bırak")}</h2><p>{tx(`This restores ${deactivateTarget.members.length} products in “${deactivateTarget.displayName}” to independent name-search results. Products and barcodes are not deleted.`, `“${deactivateTarget.displayName}” ailesindeki ${deactivateTarget.members.length} ürün bağımsız ad arama sonuçlarına döner. Ürünler ve barkodlar silinmez.`)}</p><ul>{deactivateTarget.members.map(member => <li key={member.productId}>#{member.productId} {member.displayName} · {member.barcode}</li>)}</ul><label>{tx("Rollback reason", "Geri alma nedeni")}<textarea minLength={10} maxLength={1000} value={deactivateReason} onChange={event => setDeactivateReason(event.target.value)}/></label><div className="modal-actions"><button className="ghost-button" onClick={() => setDeactivateTarget(null)}>{tx("Cancel", "İptal")}</button><button className="danger-button" disabled={deactivating || deactivateReason.trim().length < 10} onClick={() => void deactivate()}>{deactivating ? tx("Deactivating…", "Devre dışı bırakılıyor…") : tx("Confirm deactivation", "Devre dışı bırakmayı onayla")}</button></div></section></div>}
  </div>;
}
