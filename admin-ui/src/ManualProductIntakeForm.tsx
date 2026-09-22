import { FormEvent, ReactNode, useState } from "react";
import { formatRequestError, request } from "./api";
import { Panel } from "./AdminPrimitives";
import { useAdminLocale } from "./admin/locale";

const REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];
const CATALOG_TYPES = ["BRANDED_PRODUCT", "GENERIC_INGREDIENT", "LOCAL_DISH", "USER_CUSTOM"];
const PREPARATION_STATES = ["UNSPECIFIED", "RAW", "COOKED", "BOILED", "GRILLED", "FRIED", "BAKED", "ROASTED", "STEAMED", "PREPARED"];
const NUTRIENTS = ["calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium"] as const;
const EXTENDED_NUTRIENTS = ["saturatedFat", "transFat", "sugarAlcohol", "potassium", "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminB12"] as const;

const emptyForm = {
  barcode: "", marketRegion: "", productName: "", brand: "", catalogType: "BRANDED_PRODUCT", preparationState: "UNSPECIFIED",
  nutritionBasis: "SOURCE_REPORTED", nutritionReferenceUnit: "PER_100G", servingSizeGrams: "100", servingUnit: "g",
  calories: "", protein: "", fat: "", carbs: "", fiber: "", sugar: "", sodium: "", saturatedFat: "", transFat: "", sugarAlcohol: "",
  potassium: "", cholesterol: "", calcium: "", iron: "", magnesium: "", zinc: "", vitaminA: "", vitaminC: "", vitaminD: "", vitaminE: "", vitaminB12: "",
  ingredientsText: "", allergens: "", sourceCategoryTags: "", adminSourceName: "", adminSourceUrl: "", adminCreationNote: ""
};

export function ManualProductIntakeForm({ canWrite, onCreated, onError }: { canWrite: boolean; onCreated: () => Promise<void>; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const initialBarcode = new URLSearchParams(window.location.search).get("barcode")?.replace(/[^0-9]/g, "") ?? "";
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ ...emptyForm, barcode: initialBarcode });
  const tr = locale === "tr";
  if (!canWrite) return null;
  const update = (field: keyof typeof form, value: string) => setForm((current) => ({ ...current, [field]: value }));

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); onError(null);
    try {
      const numeric = Object.fromEntries([...NUTRIENTS, ...EXTENDED_NUTRIENTS].map((field) => [field, optionalNumber(form[field])]));
      await request("/api/v1/admin/product-intakes/manual", { method: "POST", body: {
        idempotencyKey: crypto.randomUUID(), barcode: form.barcode.trim(), marketRegion: form.marketRegion,
        productName: form.productName.trim(), brand: nullableText(form.brand), ...numeric, calories: Number(form.calories),
        nutritionBasis: form.nutritionBasis, nutritionReferenceUnit: form.nutritionReferenceUnit,
        servingSizeGrams: optionalNumber(form.servingSizeGrams), servingUnit: nullableText(form.servingUnit), catalogType: form.catalogType,
        preparationState: form.preparationState, ingredientsText: nullableText(form.ingredientsText), allergens: nullableText(form.allergens),
        sourceCategoryTags: form.sourceCategoryTags.split(",").map((value) => value.trim()).filter(Boolean), adminSourceName: nullableText(form.adminSourceName),
        adminSourceUrl: nullableText(form.adminSourceUrl), adminCreationNote: nullableText(form.adminCreationNote)
      }});
      setOpen(false); setForm({ ...emptyForm }); await onCreated();
    } catch (error) { onError(formatRequestError(error)); } finally { setBusy(false); }
  }

  return <Panel title={tr ? "Yönetici ürün girişi" : "Admin manual product intake"} description={tr ? "Tüm katalog alanlarıyla dahili inceleme adayı oluşturur; doğrudan yayınlamaz." : "Creates an internal-review candidate with complete catalog data; it never publishes directly."} actions={<button className="ghost-button" type="button" onClick={() => setOpen((value) => !value)}>{open ? (tr ? "Formu kapat" : "Close form") : (tr ? "Yeni aday" : "New candidate")}</button>}>
    {open && <form className="manual-intake-form" onSubmit={submit}>
      <FormSection title={tr ? "1 · Ürün kimliği" : "1 · Product identity"} description={tr ? "Ürünün katalogda bulunmasını ve doğru sınıflandırılmasını sağlayan bilgiler." : "Information used to find and classify the product in the catalog."}>
        <div className="manual-intake-grid">
          <Field label={tr ? "Barkod" : "Barcode"} required><input required inputMode="numeric" maxLength={64} value={form.barcode} onChange={(e) => update("barcode", e.target.value.replace(/\D/g, ""))}/></Field>
          <Field label={tr ? "Pazar bölgesi" : "Market region"} required><select required value={form.marketRegion} onChange={(e) => update("marketRegion", e.target.value)}><option value="">{tr ? "Pazar seçin" : "Select market"}</option>{REGIONS.map((value) => <option key={value}>{value}</option>)}</select></Field>
          <Field label={tr ? "Ürün adı" : "Product name"} required><input required maxLength={255} value={form.productName} onChange={(e) => update("productName", e.target.value)}/></Field>
          <Field label={tr ? "Marka" : "Brand"}><input maxLength={255} value={form.brand} onChange={(e) => update("brand", e.target.value)}/></Field>
          <Field label={tr ? "Katalog tipi" : "Catalog type"} required><select value={form.catalogType} onChange={(e) => update("catalogType", e.target.value)}>{CATALOG_TYPES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}</select></Field>
          <Field label={tr ? "Hazırlama durumu" : "Preparation state"}><select value={form.preparationState} onChange={(e) => update("preparationState", e.target.value)}>{PREPARATION_STATES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}</select></Field>
        </div>
      </FormSection>
      <FormSection title={tr ? "2 · Ölçüm ve porsiyon" : "2 · Measurement and serving"} description={tr ? "Besin değerlerinin hangi ölçüye göre verildiğini ve varsayılan porsiyonu tanımlar." : "Defines the nutrition reference and default serving conversion."}>
        <div className="manual-intake-grid">
          <Field label={tr ? "Veri kaynağı" : "Nutrition provenance"} required><select value={form.nutritionBasis} onChange={(e) => update("nutritionBasis", e.target.value)}><option value="SOURCE_REPORTED">{tr ? "Kaynakta bildirilen" : "Source reported"}</option><option value="CALCULATED">{tr ? "Hesaplanan" : "Calculated"}</option><option value="ESTIMATED">{tr ? "Tahmini" : "Estimated"}</option></select></Field>
          <Field label={tr ? "Referans ölçü" : "Reference unit"} required><select value={form.nutritionReferenceUnit} onChange={(e) => update("nutritionReferenceUnit", e.target.value)}><option value="PER_100G">100 g</option><option value="PER_100ML">100 ml</option></select></Field>
          <NumberField label={tr ? "Porsiyon miktarı" : "Serving size"} value={form.servingSizeGrams} onChange={(value) => update("servingSizeGrams", value)}/>
          <Field label={tr ? "Porsiyon birimi" : "Serving unit"}><input maxLength={100} value={form.servingUnit} onChange={(e) => update("servingUnit", e.target.value)} placeholder={form.nutritionReferenceUnit === "PER_100ML" ? "ml" : "g"}/></Field>
        </div>
      </FormSection>
      <FormSection title={tr ? "3 · Temel besin değerleri" : "3 · Core nutrition"} description={tr ? "Etiket üzerindeki temel enerji ve makro değerleri." : "Energy and macronutrients shown on the product label."}>
        <div className="manual-intake-grid nutrients">{NUTRIENTS.map((field) => <NumberField key={field} label={nutrientLabel(field, locale)} required={field === "calories"} value={form[field]} onChange={(value) => update(field, value)}/>)}</div>
      </FormSection>
      <details className="manual-intake-advanced"><summary><span><strong>{tr ? "4 · Mikro besinler ve detaylar" : "4 · Micronutrients and details"}</strong><small>{tr ? "Etikette varsa yağ kırılımı, mineraller ve vitaminleri girin." : "Add fat breakdown, minerals, and vitamins when available on the label."}</small></span></summary><div className="manual-intake-grid nutrients">{EXTENDED_NUTRIENTS.map((field) => <NumberField key={field} label={nutrientLabel(field, locale)} value={form[field]} onChange={(value) => update(field, value)}/>)}</div></details>
      <FormSection title={tr ? "5 · Güvenlik ve kaynak" : "5 · Safety and source"} description={tr ? "Alerjen bilgisi ve yöneticinin kullandığı kaynağın izlenebilirliği." : "Allergen information and traceability for the admin source."}>
        <div className="manual-intake-grid">
          <Field label={tr ? "İçindekiler" : "Ingredients"} wide><textarea maxLength={5000} value={form.ingredientsText} onChange={(e) => update("ingredientsText", e.target.value)} placeholder={tr ? "Etikette yazan içerik listesini girin" : "Enter the ingredient list from the label"}/></Field>
          <Field label={tr ? "Alerjenler" : "Allergens"} wide><textarea maxLength={1000} value={form.allergens} onChange={(e) => update("allergens", e.target.value)} placeholder={tr ? "Örn. süt, fındık, soya" : "e.g. milk, nuts, soy"}/></Field>
          <Field label={tr ? "Kaynak kategori etiketleri" : "Source category tags"} wide><input value={form.sourceCategoryTags} onChange={(e) => update("sourceCategoryTags", e.target.value)} placeholder={tr ? "Virgülle ayırın: içecek, gazlı içecek" : "Comma separated: beverage, soft drink"}/></Field>
          <Field label={tr ? "Kaynak adı" : "Source name"}><input maxLength={160} value={form.adminSourceName} onChange={(e) => update("adminSourceName", e.target.value)} placeholder={tr ? "Üretici etiketi, resmi veri seti…" : "Manufacturer label, official dataset…"}/></Field>
          <Field label={tr ? "Kaynak bağlantısı" : "Source URL"}><input type="url" maxLength={1000} value={form.adminSourceUrl} onChange={(e) => update("adminSourceUrl", e.target.value)}/></Field>
          <Field label={tr ? "Oluşturma notu" : "Creation note"} wide><textarea maxLength={500} value={form.adminCreationNote} onChange={(e) => update("adminCreationNote", e.target.value)}/></Field>
        </div>
      </FormSection>
      <div className="manual-intake-footer"><span>{tr ? "Kayıt dahili inceleme kuyruğuna gönderilir." : "The record enters the internal review queue."}</span><button className="primary-button" disabled={busy || !form.marketRegion || !form.calories} type="submit">{busy ? (tr ? "Oluşturuluyor…" : "Creating…") : (tr ? "İnceleme adayı oluştur" : "Create review candidate")}</button></div>
    </form>}
  </Panel>;
}

function FormSection({ title, description, children }: { title: string; description: string; children: ReactNode }) { return <section className="manual-intake-section"><header><strong>{title}</strong><small>{description}</small></header>{children}</section>; }
function Field({ label, required, wide, children }: { label: string; required?: boolean; wide?: boolean; children: ReactNode }) { return <label className={wide ? "wide" : ""}><span>{label}{required && <b> *</b>}</span>{children}</label>; }
function NumberField({ label, value, onChange, required }: { label: string; value: string; onChange: (value: string) => void; required?: boolean }) { return <Field label={label} required={required}><input required={required} min="0" step="any" type="number" value={value} onChange={(e) => onChange(e.target.value)}/></Field>; }
function optionalNumber(value: string): number | null { return value.trim() ? Number(value) : null; }
function nullableText(value: string): string | null { return value.trim() || null; }
function humanize(value: string) { return value.replaceAll("_", " ").toLowerCase().replace(/^./, (letter) => letter.toUpperCase()); }
function nutrientLabel(field: string, locale: "tr" | "en") { const tr: Record<string,string> = { calories:"Kalori (kcal)",protein:"Protein (g)",carbs:"Karbonhidrat (g)",fat:"Yağ (g)",fiber:"Lif (g)",sugar:"Şeker (g)",sodium:"Sodyum (mg)",saturatedFat:"Doymuş yağ (g)",transFat:"Trans yağ (g)",sugarAlcohol:"Şeker alkolü (g)",potassium:"Potasyum (mg)",cholesterol:"Kolesterol (mg)",calcium:"Kalsiyum (mg)",iron:"Demir (mg)",magnesium:"Magnezyum (mg)",zinc:"Çinko (mg)",vitaminA:"A vitamini (µg)",vitaminC:"C vitamini (mg)",vitaminD:"D vitamini (µg)",vitaminE:"E vitamini (mg)",vitaminB12:"B12 vitamini (µg)" }; return locale === "tr" ? tr[field] ?? humanize(field) : humanize(field.replace(/([a-z])([A-Z])/g,"$1 $2")); }
