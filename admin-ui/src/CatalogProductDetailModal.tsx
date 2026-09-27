import { useState } from "react";
import { formatRequestError, request } from "./api";
import { normalizeBarcode, validateGtin } from "./admin/barcode";
import { useAdminLocale } from "./admin/locale";
import { Badge, DetailItem, IMAGE_SOURCES, IMAGE_STATUSES, MARKET_REGIONS, VERIFICATION_STATUSES, formatDate, formatValue, humanizeFeature, marketRegionLabel, productName } from "./admin/shared";
import { useDialogAccessibility } from "./AdminPrimitives";
import {
  CATALOG_TYPES,
  PREPARATION_STATES,
  PRODUCT_MACRO_FIELDS,
  PRODUCT_MINERAL_FIELDS,
  PRODUCT_VITAMIN_FIELDS,
  productReviewNutritionPayload,
  toProductReviewDraft,
  type ProductReviewDraft,
  type ProductReviewNumberField
} from "./pages/ProductReviewView";
import type { FoodProduct } from "./types";

type DetailTab = "overview" | "nutrition" | "identity";

const ALLERGEN_GROUPS = [
  { key: "animal", en: "Animal-derived", tr: "Hayvansal kaynaklı", values: ["MILK", "EGGS", "FISH", "CRUSTACEAN_SHELLFISH", "MOLLUSCS"] },
  { key: "nuts", en: "Nuts and seeds", tr: "Kuruyemiş ve tohumlar", values: ["TREE_NUTS", "PEANUTS", "SESAME"] },
  { key: "plants", en: "Grains and plants", tr: "Tahıl ve bitkiler", values: ["WHEAT", "GLUTEN", "SOYBEANS", "CELERY", "MUSTARD", "LUPIN"] },
  { key: "additives", en: "Preservatives", tr: "Koruyucular", values: ["SULPHITES"] }
] as const;
const ALLERGEN_CODES = new Set<string>(ALLERGEN_GROUPS.flatMap(group => [...group.values]));

export function CatalogProductDetailModal({ product, canManage, onClose, onSaved }: {
  product: FoodProduct;
  canManage: boolean;
  onClose: () => void;
  onSaved: (product: FoodProduct) => void;
}) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, turkish: string) => tr ? turkish : en;
  const dialogRef = useDialogAccessibility<HTMLElement>(onClose);
  const [tab, setTab] = useState<DetailTab>("overview");
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<ProductReviewDraft>(() => toProductReviewDraft(product));
  const [reviewNote, setReviewNote] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const currentBarcode = normalizeBarcode(product.normalizedBarcode ?? product.barcode ?? "");
  const [barcode, setBarcode] = useState(currentBarcode);
  const [barcodeReason, setBarcodeReason] = useState("");
  const [barcodeBusy, setBarcodeBusy] = useState(false);
  const barcodeValidation = validateGtin(barcode);
  const image = product.displayImageUrl ?? product.imageUrl ?? product.externalImageUrl;

  function updateDraft<K extends keyof ProductReviewDraft>(key: K, value: ProductReviewDraft[K]) {
    setDraft(current => ({ ...current, [key]: value }));
  }

  function cancelEditing() {
    setDraft(toProductReviewDraft(product));
    setReviewNote("");
    setActionError(null);
    setEditing(false);
  }

  async function saveProduct() {
    if (!product.id || !draft.productName.trim()) {
      setActionError(tx("Product name and product ID are required.", "Ürün adı ve ürün kimliği zorunludur."));
      return;
    }
    setSaving(true); setActionError(null); setNotice(null);
    try {
      const updated = await request<FoodProduct>(`/api/v1/admin/products/${product.id}/review`, {
        method: "PATCH",
        body: {
          productName: draft.productName.trim(), brand: draft.brand.trim() || null,
          displayImageUrl: draft.displayImageUrl.trim() || null, marketRegion: draft.marketRegion || null,
          verificationStatus: draft.verificationStatus || null, imageStatus: draft.imageStatus || null,
          imageSource: draft.imageSource || null, catalogType: draft.catalogType || null,
          preparationState: draft.preparationState || null, nutritionBasis: draft.nutritionBasis || null,
          nutritionReferenceUnit: draft.nutritionReferenceUnit || null, ingredientsText: draft.ingredientsText,
          allergens: draft.allergens, sourceCategoryTags: draft.sourceCategoryTags.split(",").map(value => value.trim()).filter(Boolean),
          adminSourceName: draft.adminSourceName, adminSourceUrl: draft.adminSourceUrl,
          adminCreationNote: draft.adminCreationNote, ...productReviewNutritionPayload(draft),
          reviewNote: reviewNote.trim() || "Updated from catalog search workspace."
        }
      });
      onSaved(updated); setDraft(toProductReviewDraft(updated)); setEditing(false); setReviewNote("");
      setNotice(tx("Product details were updated.", "Ürün bilgileri güncellendi."));
    } catch (error) { setActionError(formatRequestError(error)); }
    finally { setSaving(false); }
  }

  async function replaceBarcode() {
    if (!product.id || !barcodeValidation.valid || !barcodeReason.trim() || barcodeValidation.barcode === currentBarcode) return;
    setBarcodeBusy(true); setActionError(null); setNotice(null);
    try {
      const updated = await request<FoodProduct>(`/api/v1/admin/products/${product.id}/barcode`, {
        method: "PATCH", body: { barcode: barcodeValidation.barcode, reason: barcodeReason.trim() }
      });
      onSaved(updated); setBarcode(updated.normalizedBarcode ?? updated.barcode ?? barcodeValidation.barcode);
      setBarcodeReason(""); setNotice(tx("Barcode was updated and audited.", "Barkod güncellendi ve denetim kaydına işlendi."));
    } catch (error) { setActionError(formatRequestError(error)); }
    finally { setBarcodeBusy(false); }
  }

  return <div className="modal-backdrop catalog-detail-backdrop" role="presentation" onClick={onClose}>
    <section ref={dialogRef} tabIndex={-1} className="product-modal catalog-detail-modal" role="dialog" aria-modal="true" aria-labelledby="catalog-detail-title" onClick={event => event.stopPropagation()}>
      <header className="modal-header catalog-detail-header"><div><p className="eyebrow">{tx("CATALOG PRODUCT", "KATALOG ÜRÜNÜ")}</p><h2 id="catalog-detail-title">{productName(product)}</h2><span>#{product.id} · {product.brand || tx("No brand", "Marka yok")}</span></div><div className="catalog-detail-header-actions">{canManage && !editing && <button className="primary-button" type="button" onClick={() => setEditing(true)}>{tx("Edit product", "Ürünü düzenle")}</button>}<button className="icon-button" type="button" onClick={onClose} aria-label={tx("Close", "Kapat")}>×</button></div></header>
      {actionError && <div className="modal-error catalog-detail-message" role="alert"><div><strong>{tx("Action could not be completed", "İşlem tamamlanamadı")}</strong><span>{actionError}</span></div><button type="button" onClick={() => setActionError(null)} aria-label={tx("Dismiss error", "Hatayı kapat")}>×</button></div>}
      {notice && <div className="success-banner catalog-detail-message" role="status"><span>{notice}</span><button type="button" onClick={() => setNotice(null)} aria-label={tx("Dismiss message", "Mesajı kapat")}>×</button></div>}
      <nav className="catalog-detail-tabs" aria-label={tx("Product detail sections", "Ürün detay bölümleri")}>
        {(["overview", "nutrition", "identity"] as DetailTab[]).map(value => <button key={value} className={tab === value ? "active" : ""} type="button" onClick={() => setTab(value)}>{value === "overview" ? tx("Product details", "Ürün detayları") : value === "nutrition" ? tx("Nutrition details", "Besin değerleri") : tx("Barcode & identity", "Barkod ve kimlik")}</button>)}
      </nav>
      <div className="catalog-detail-body">
        <aside className="catalog-detail-summary"><div className="product-image-frame">{image ? <img src={image} alt={productName(product)} /> : <span>{tx("No image", "Görsel yok")}</span>}</div><div className="catalog-detail-badges"><Badge value={product.verificationStatus} /><Badge value={product.publicationStatus} tone="neutral" /><Badge value={product.imageStatus} tone="neutral" /></div><div className="catalog-detail-score"><span>{tx("Quality score", "Kalite puanı")}</span><strong>{formatValue(product.qualityScore)} / 100</strong></div></aside>
        <main className="catalog-detail-workspace">
          {tab === "overview" && <section className="catalog-detail-pane">
            <div className="catalog-detail-grid">
              <Field label={tx("Product name", "Ürün adı")} editing={editing} value={draft.productName} onChange={value => updateDraft("productName", value)} />
              <Field label={tx("Brand", "Marka")} editing={editing} value={draft.brand} onChange={value => updateDraft("brand", value)} />
              <SelectField label={tx("Market", "Pazar")} editing={editing} value={draft.marketRegion} values={MARKET_REGIONS} optionLabel={value => marketRegionLabel(value, locale)} onChange={value => updateDraft("marketRegion", value)} />
              <SelectField label={tx("Catalog type", "Katalog tipi")} editing={editing} value={draft.catalogType} values={CATALOG_TYPES} onChange={value => updateDraft("catalogType", value)} />
              <SelectField label={tx("Preparation", "Hazırlama durumu")} editing={editing} value={draft.preparationState} values={PREPARATION_STATES} onChange={value => updateDraft("preparationState", value)} />
              <SelectField label={tx("Verification", "Doğrulama")} editing={editing} value={draft.verificationStatus} values={VERIFICATION_STATUSES} onChange={value => updateDraft("verificationStatus", value)} />
              <DetailItem label={tx("Data source", "Veri kaynağı")} value={product.dataSource} />
              <DetailItem label={tx("Last reviewed", "Son inceleme")} value={formatDate(product.lastReviewedAt)} />
            </div>
            <TextField label={tx("Ingredients", "İçindekiler")} editing={editing} value={draft.ingredientsText} onChange={value => updateDraft("ingredientsText", value)} />
            <AllergenPicker editing={editing} value={draft.allergens} locale={locale} onChange={value => updateDraft("allergens", value)} />
            <Field label={tx("Display image URL", "Gösterim görseli adresi")} editing={editing} value={draft.displayImageUrl} onChange={value => updateDraft("displayImageUrl", value)} />
            {editing && <div className="catalog-detail-grid"><SelectField label={tx("Image status", "Görsel durumu")} editing value={draft.imageStatus} values={IMAGE_STATUSES} onChange={value => updateDraft("imageStatus", value)} /><SelectField label={tx("Image source", "Görsel kaynağı")} editing value={draft.imageSource} values={IMAGE_SOURCES} onChange={value => updateDraft("imageSource", value)} /></div>}
          </section>}
          {tab === "nutrition" && <section className="catalog-detail-pane">
            <div className="catalog-detail-grid"><SelectField label={tx("Nutrition basis", "Beslenme temeli")} editing={editing} value={draft.nutritionBasis} values={["SOURCE_REPORTED", "CALCULATED", "ESTIMATED"]} onChange={value => updateDraft("nutritionBasis", value)} /><SelectField label={tx("Reference unit", "Referans ölçü")} editing={editing} value={draft.nutritionReferenceUnit} values={["PER_100G", "PER_100ML"]} onChange={value => updateDraft("nutritionReferenceUnit", value)} /><NumberField label={tx("Serving size", "Porsiyon miktarı")} editing={editing} value={draft.servingSizeGrams} suffix="g/ml" onChange={value => updateDraft("servingSizeGrams", value)} /><Field label={tx("Serving unit", "Porsiyon birimi")} editing={editing} value={draft.servingUnit} onChange={value => updateDraft("servingUnit", value)} /></div>
            <NutritionGroup title={tx("Macros", "Makrolar")} fields={PRODUCT_MACRO_FIELDS} draft={draft} editing={editing} onChange={updateDraft} />
            <NutritionGroup title={tx("Minerals", "Mineraller")} fields={PRODUCT_MINERAL_FIELDS} draft={draft} editing={editing} onChange={updateDraft} />
            <NutritionGroup title={tx("Vitamins", "Vitaminler")} fields={PRODUCT_VITAMIN_FIELDS} draft={draft} editing={editing} onChange={updateDraft} />
          </section>}
          {tab === "identity" && <section className="catalog-detail-pane">
            <div className="catalog-detail-grid"><DetailItem label={tx("Product ID", "Ürün kimliği")} value={formatValue(product.id)} /><DetailItem label={tx("Barcode", "Barkod")} value={currentBarcode || "-"} /><DetailItem label={tx("Source key", "Kaynak anahtarı")} value={product.sourceKey} /><DetailItem label={tx("Canonical key", "Kanonik anahtar")} value={product.canonicalFoodKey} /><DetailItem label={tx("Reviewed by", "İnceleyen")} value={product.reviewedBy} /><DetailItem label={tx("External sync", "Dış kaynak eşitlemesi")} value={formatDate(product.lastExternalSyncAt)} /></div>
            <Field label={tx("Source category tags", "Kaynak kategori etiketleri")} editing={editing} value={draft.sourceCategoryTags} onChange={value => updateDraft("sourceCategoryTags", value)} />
            {canManage && <section className="catalog-barcode-workbench"><header><div><span>{tx("BARCODE CONTROL", "BARKOD KONTROLÜ")}</span><strong>{tx("Validated and audited replacement", "Doğrulamalı ve denetimli değişiklik")}</strong></div><small>{tx("The barcode must pass GTIN checksum and must not belong to another product.", "Barkod GTIN kontrol basamağını geçmeli ve başka bir ürüne ait olmamalıdır.")}</small></header><div className="catalog-barcode-fields"><label>{tx("New barcode", "Yeni barkod")}<input inputMode="numeric" maxLength={14} value={barcode} onChange={event => setBarcode(normalizeBarcode(event.target.value))} /></label><label>{tx("Change reason", "Değişiklik gerekçesi")}<input maxLength={500} value={barcodeReason} onChange={event => setBarcodeReason(event.target.value)} placeholder={tx("Verified package label or source", "Doğrulanmış ambalaj etiketi veya kaynak")} /></label></div>{barcode && !barcodeValidation.valid && <span className="danger-text">{tx("Enter a valid GTIN-8, GTIN-12, GTIN-13 or GTIN-14.", "Geçerli bir GTIN-8, GTIN-12, GTIN-13 veya GTIN-14 girin.")}</span>}<button className="ghost-button" type="button" disabled={barcodeBusy || !barcodeValidation.valid || barcodeValidation.barcode === currentBarcode || !barcodeReason.trim()} onClick={() => void replaceBarcode()}>{barcodeBusy ? tx("Updating…", "Güncelleniyor…") : tx("Update barcode", "Barkodu güncelle")}</button></section>}
          </section>}
        </main>
      </div>
      <footer className="modal-actions catalog-detail-actions"><div><strong>{editing ? tx("Editing enabled", "Düzenleme açık") : tx("Read-only view", "Salt okunur görünüm")}</strong><span>{editing ? tx("Changes are recorded in product audit history.", "Değişiklikler ürün denetim geçmişine kaydedilir.") : tx("Use Edit product to change catalog data.", "Katalog verisini değiştirmek için Ürünü düzenle seçeneğini kullanın.")}</span></div>{editing && <label>{tx("Review note", "İnceleme notu")}<input maxLength={1000} value={reviewNote} onChange={event => setReviewNote(event.target.value)} /></label>}<button className="ghost-button" type="button" onClick={editing ? cancelEditing : onClose}>{editing ? tx("Cancel editing", "Düzenlemeyi iptal et") : tx("Close", "Kapat")}</button>{editing && <button className="primary-button" disabled={saving} type="button" onClick={() => void saveProduct()}>{saving ? tx("Saving…", "Kaydediliyor…") : tx("Save changes", "Değişiklikleri kaydet")}</button>}</footer>
    </section>
  </div>;
}

function Field({ label, value, editing, onChange }: { label: string; value: string; editing: boolean; onChange: (value: string) => void }) { return <label className="catalog-detail-field"><span>{label}</span>{editing ? <input value={value} onChange={event => onChange(event.target.value)} /> : <strong>{value || "-"}</strong>}</label>; }
function TextField({ label, value, editing, onChange }: { label: string; value: string; editing: boolean; onChange: (value: string) => void }) { return <label className="catalog-detail-field wide"><span>{label}</span>{editing ? <textarea value={value} onChange={event => onChange(event.target.value)} /> : <strong>{value || "-"}</strong>}</label>; }
function SelectField({ label, value, values, editing, optionLabel = humanizeFeature, onChange }: { label: string; value: string; values: string[]; editing: boolean; optionLabel?: (value: string) => string; onChange: (value: string) => void }) { return <label className="catalog-detail-field"><span>{label}</span>{editing ? <select value={value} onChange={event => onChange(event.target.value)}><option value="">-</option>{values.map(item => <option key={item} value={item}>{optionLabel(item)}</option>)}</select> : <strong>{value ? optionLabel(value) : "-"}</strong>}</label>; }
function NumberField({ label, value, suffix, editing, onChange }: { label: string; value: string; suffix: string; editing: boolean; onChange: (value: string) => void }) { return <label className="catalog-detail-field"><span>{label}</span>{editing ? <div className="catalog-number-input"><input type="number" inputMode="decimal" min="0" step="0.01" value={value} onChange={event => onChange(event.target.value)} /><em>{suffix}</em></div> : <strong>{value ? `${value} ${suffix}` : "-"}</strong>}</label>; }
function NutritionGroup({ title, fields, draft, editing, onChange }: { title: string; fields: Array<{ key: ProductReviewNumberField; label: string; suffix: string }>; draft: ProductReviewDraft; editing: boolean; onChange: <K extends keyof ProductReviewDraft>(key: K, value: ProductReviewDraft[K]) => void }) { return <section className="catalog-nutrition-group"><h3>{title}</h3><div className="catalog-nutrition-grid">{fields.map(field => <NumberField key={field.key} label={field.label} suffix={field.suffix} editing={editing} value={draft[field.key]} onChange={value => onChange(field.key, value)} />)}</div></section>; }

function AllergenPicker({ value, editing, locale, onChange }: { value: string; editing: boolean; locale: "tr" | "en"; onChange: (value: string) => void }) {
  const tr = locale === "tr";
  const tokens = value.split(/[,;|]/).map(item => item.trim()).filter(Boolean);
  const normalize = (item: string) => item.toUpperCase().replace(/[\s-]+/g, "_");
  const selected = new Set(tokens.map(normalize).filter(item => ALLERGEN_CODES.has(item)));
  const unrecognized = tokens.filter(item => !ALLERGEN_CODES.has(normalize(item)));
  const labels: Record<string, [string, string]> = {
    MILK: ["Milk", "Süt"], EGGS: ["Eggs", "Yumurta"], FISH: ["Fish", "Balık"],
    CRUSTACEAN_SHELLFISH: ["Crustacean shellfish", "Kabuklu deniz ürünleri"], MOLLUSCS: ["Molluscs", "Yumuşakçalar"],
    TREE_NUTS: ["Tree nuts", "Sert kabuklu yemişler"], PEANUTS: ["Peanuts", "Yer fıstığı"], SESAME: ["Sesame", "Susam"],
    WHEAT: ["Wheat", "Buğday"], GLUTEN: ["Gluten", "Gluten"], SOYBEANS: ["Soybeans", "Soya"], CELERY: ["Celery", "Kereviz"],
    MUSTARD: ["Mustard", "Hardal"], LUPIN: ["Lupin", "Acı bakla"], SULPHITES: ["Sulphites", "Sülfitler"]
  };
  const labelFor = (code: string) => labels[code]?.[tr ? 1 : 0] ?? humanizeFeature(code);
  function toggle(code: string) {
    const next = new Set(selected);
    if (next.has(code)) next.delete(code); else next.add(code);
    onChange([...next, ...unrecognized].join(", "));
  }
  if (!editing) return <section className="catalog-allergen-picker readonly"><span>{tr ? "Alerjenler" : "Allergens"}</span><div className="catalog-allergen-summary">{selected.size ? [...selected].map(code => <span key={code}>{labelFor(code)} <small>{code}</small></span>) : null}{unrecognized.map(item => <span key={item} className="legacy">{item}</span>)}{!tokens.length && <strong>-</strong>}</div></section>;
  return <details className="catalog-allergen-picker" open>
    <summary><span><strong>{tr ? "Alerjenler" : "Allergens"}</strong><small>{tr ? "Standart listeden seçin" : "Select from the standard list"}</small></span><em>{selected.size}</em></summary>
    <div className="catalog-allergen-groups">{ALLERGEN_GROUPS.map(group => <fieldset key={group.key}><legend>{tr ? group.tr : group.en}</legend>{group.values.map(code => <label key={code}><input type="checkbox" checked={selected.has(code)} onChange={() => toggle(code)} /><span>{labelFor(code)}<small>{code}</small></span></label>)}</fieldset>)}</div>
    {unrecognized.length > 0 && <div className="catalog-allergen-preserved"><strong>{tr ? "Kaynakta bulunan diğer değerler" : "Other source values"}</strong><span>{unrecognized.join(", ")}</span><small>{tr ? "Bu değerler kaydedilirken korunur." : "These values are preserved when saving."}</small></div>}
  </details>;
}
