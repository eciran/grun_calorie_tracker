import { FormEvent, useState } from "react";
import { formatRequestError, request } from "./api";
import { Panel } from "./AdminPrimitives";

const REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];

export function ManualProductIntakeForm({
  canWrite,
  onCreated,
  onError
}: {
  canWrite: boolean;
  onCreated: () => Promise<void>;
  onError: (message: string | null) => void;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    barcode: "",
    marketRegion: "",
    productName: "",
    brand: "",
    calories: "",
    protein: "",
    fat: "",
    carbs: "",
    fiber: "",
    sugar: "",
    sodium: "",
    nutritionBasis: "SOURCE_REPORTED"
  });

  if (!canWrite) return null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    onError(null);
    try {
      await request("/api/v1/admin/product-intakes/manual", {
        method: "POST",
        body: {
          idempotencyKey: crypto.randomUUID(),
          barcode: form.barcode.trim(),
          marketRegion: form.marketRegion,
          productName: form.productName.trim(),
          brand: form.brand.trim() || null,
          calories: Number(form.calories),
          protein: optionalNumber(form.protein),
          fat: optionalNumber(form.fat),
          carbs: optionalNumber(form.carbs),
          fiber: optionalNumber(form.fiber),
          sugar: optionalNumber(form.sugar),
          sodium: optionalNumber(form.sodium),
          nutritionBasis: form.nutritionBasis
        }
      });
      setOpen(false);
      setForm((current) => ({ ...current, barcode: "", productName: "", brand: "", calories: "" }));
      await onCreated();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setBusy(false);
    }
  }

  return <Panel
    title="Admin manual product intake"
    description="Creates an internal-review candidate; it never publishes directly."
    actions={<button className="ghost-button" type="button" onClick={() => setOpen((value) => !value)}>{open ? "Close form" : "New candidate"}</button>}
  >
    {open && <form className="commercial-form" onSubmit={submit}>
      <div className="commercial-form-grid">
        <label>Barcode<input required maxLength={64} value={form.barcode} onChange={(event) => setForm({ ...form, barcode: event.target.value })} /></label>
        <label>Market region<select required value={form.marketRegion} onChange={(event) => setForm({ ...form, marketRegion: event.target.value })}>
          <option value="">Select market</option>
          {REGIONS.map((region) => <option key={region}>{region}</option>)}
        </select></label>
        <label>Product name<input required maxLength={255} value={form.productName} onChange={(event) => setForm({ ...form, productName: event.target.value })} /></label>
        <label>Brand<input maxLength={255} value={form.brand} onChange={(event) => setForm({ ...form, brand: event.target.value })} /></label>
        <label>Nutrition basis<select value={form.nutritionBasis} onChange={(event) => setForm({ ...form, nutritionBasis: event.target.value })}>
          <option value="PER_100G">Per 100 g</option>
          <option value="PER_SERVING">Per serving</option>
        </select></label>
        {(["calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium"] as const).map((field) =>
          <label key={field}>{field[0].toUpperCase() + field.slice(1)}
            <input required={field === "calories"} min="0" step="any" type="number" value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} />
          </label>
        )}
      </div>
      <div className="inline-actions">
        <button className="primary-button" disabled={busy || !form.marketRegion} type="submit">{busy ? "Creating..." : "Create internal candidate"}</button>
      </div>
    </form>}
  </Panel>;
}

function optionalNumber(value: string): number | null {
  return value.trim() ? Number(value) : null;
}
