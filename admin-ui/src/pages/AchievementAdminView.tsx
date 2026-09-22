import { FormEvent, useState } from "react";

import { formatRequestError, request } from "../api";

import { AdminAchievementDefinition, AdminAchievementMetrics } from "../types";

import { DataTable, MetricCard, SectionToolbar } from "../AdminPrimitives";
import { CommunicationBars, CommunicationHero } from "../CommunicationsPrimitives";
import { useAdminLocale } from "../admin/locale";

import { Badge, combineStates, formatValue, humanizeFeature, useEndpoint } from "./../admin/shared";

export const ACHIEVEMENT_CATEGORIES = ["ONBOARDING", "FOOD", "EXERCISE", "FASTING", "PROGRESS", "WATER"];

export const ACHIEVEMENT_TIERS = ["BRONZE", "SILVER", "GOLD"];

export type AchievementForm = {
  code: string;
  title: string;
  description: string;
  metricKey: string;
  category: string;
  tier: string;
  targetValue: string;
  active: string;
  sortOrder: string;
};

export const emptyAchievementForm: AchievementForm = {
  code: "",
  title: "",
  description: "",
  metricKey: "",
  category: "FOOD",
  tier: "BRONZE",
  targetValue: "1",
  active: "true",
  sortOrder: "1000"
};

export function AchievementAdminView({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const { data, state, reload } = useEndpoint<AdminAchievementDefinition[]>("/api/v1/admin/achievements", onError);
  const { data: metrics, state: metricState } = useEndpoint<AdminAchievementMetrics>("/api/v1/admin/achievements/metrics", onError);
  const [selected, setSelected] = useState<AdminAchievementDefinition | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [form, setForm] = useState<AchievementForm>(emptyAchievementForm);
  const [saving, setSaving] = useState(false);
  const definitions = data ?? [];
  const metricKeys = metrics?.metricKeys ?? [];
  const activeCount = definitions.filter((item) => item.active).length;
  const inactiveCount = definitions.length - activeCount;
  const categoryCounts = ACHIEVEMENT_CATEGORIES.map((category) => ({ label: humanizeFeature(category), value: definitions.filter((item) => item.category === category).length })).filter((item) => item.value > 0);

  function startCreate() {
    setSelected(null);
    setForm({ ...emptyAchievementForm, metricKey: metricKeys[0] ?? "" });
    setEditorOpen(true);
  }

  function startEdit(definition: AdminAchievementDefinition) {
    setSelected(definition);
    setForm({
      code: definition.code ?? "",
      title: definition.title ?? "",
      description: definition.description ?? "",
      metricKey: definition.metricKey ?? metricKeys[0] ?? "",
      category: definition.category ?? "FOOD",
      tier: definition.tier ?? "BRONZE",
      targetValue: String(definition.targetValue ?? 1),
      active: definition.active ? "true" : "false",
      sortOrder: String(definition.sortOrder ?? 1000)
    });
    setEditorOpen(true);
  }

  function updateForm(key: keyof AchievementForm, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function saveDefinition(event: FormEvent) {
    event.preventDefault();
    const payload = {
      code: form.code.trim().toUpperCase(),
      title: form.title.trim(),
      description: form.description.trim(),
      metricKey: form.metricKey,
      category: form.category,
      tier: form.tier,
      targetValue: Number(form.targetValue),
      active: form.active === "true",
      sortOrder: Number(form.sortOrder)
    };
    if (!payload.code || !payload.title || !payload.description || !payload.metricKey) {
      onError(tx("Code, title, description and metric key are required.", "Kod, başlık, açıklama ve metrik alanları zorunludur."));
      return;
    }
    setSaving(true);
    onError(null);
    try {
      await request<AdminAchievementDefinition>(
        selected?.code ? `/api/v1/admin/achievements/${selected.code}` : "/api/v1/admin/achievements",
        {
          method: selected?.code ? "PUT" : "POST",
          body: payload
        }
      );
      await reload();
      setSelected(null);
      setForm(emptyAchievementForm);
      setEditorOpen(false);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }

  async function deactivate(definition: AdminAchievementDefinition) {
    if (!definition.code) return;
    setSaving(true);
    onError(null);
    try {
      await request<AdminAchievementDefinition>(`/api/v1/admin/achievements/${definition.code}`, { method: "DELETE" });
      await reload();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }


  return (
    <div className="stack communications-ops-view achievements-page">
      <SectionToolbar title={tx("Achievement definitions", "Başarım tanımları")} state={combineStates([state, metricState])} onReload={reload}>
        <button className="primary-button" onClick={startCreate} type="button">{tx("New achievement", "Yeni başarım")}</button>
      </SectionToolbar>
      <CommunicationHero eyebrow={tx("Recognition system", "Başarım sistemi")} title={tx("Build measurable achievements without cluttering the user journey.", "Kullanıcı yolculuğunu karmaşıklaştırmadan ölçülebilir başarımlar oluşturun.")} description={tx("Create, review and retire badge rules from one controlled workspace.", "Rozet kurallarını tek kontrollü çalışma alanından oluşturun, inceleyin ve sonlandırın.")} status={<span className="communication-status-pill good">{activeCount} {tx("ACTIVE RULES", "AKTİF KURAL")}</span>} />

      <div className="user-summary-grid">
        <MetricCard label={tx("Definitions", "Tanımlar")} value={formatValue(definitions.length)} hint={tx("Total configured rules", "Toplam yapılandırılmış kural")} />
        <MetricCard label={tx("Active", "Aktif")} value={formatValue(activeCount)} hint={tx("Visible to app users", "Uygulama kullanıcılarına görünür")} />
        <MetricCard label={tx("Inactive", "Etkin değil")} value={formatValue(inactiveCount)} hint={tx("Hidden from user achievement list", "Kullanıcı başarım listesinden gizli")} />
      </div>
      <section className="communication-insight-strip"><div><p className="eyebrow">{tx("Rule coverage", "Kural kapsamı")}</p><h3>{tx("Definitions by category", "Kategoriye göre tanımlar")}</h3><p>{tx("Only configured categories appear; the chart does not repeat inactive totals.", "Yalnızca yapılandırılmış kategoriler gösterilir; grafik etkin olmayan toplamı tekrarlamaz.")}</p></div><CommunicationBars items={categoryCounts} empty={tx("No achievement categories configured.", "Yapılandırılmış başarım kategorisi yok.")} /></section>

      {editorOpen && (
        <div className="modal-backdrop" role="presentation" onClick={() => setEditorOpen(false)}>
          <section className="modal-card communication-editor-modal" role="dialog" aria-modal="true" aria-labelledby="achievement-editor-title" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div><p className="eyebrow">{tx("Achievement rule", "Başarım kuralı")}</p><h2 id="achievement-editor-title">{selected ? `${tx("Edit", "Düzenle")} ${selected.code}` : tx("Create achievement", "Başarım oluştur")}</h2><span>{tx("Keep the rule concise, measurable and visible in the correct category.", "Kuralı kısa, ölçülebilir ve doğru kategoride görünür tutun.")}</span></div>
              <button className="icon-button" type="button" aria-label={tx("Close", "Kapat")} onClick={() => setEditorOpen(false)}>×</button>
            </header>
            <form className="review-filter-grid" onSubmit={saveDefinition}>
          <label>
            {tx("Code", "Kod")}
            <input
              disabled={Boolean(selected)}
              value={form.code}
              onChange={(event) => updateForm("code", event.target.value.toUpperCase())}
              placeholder="FOOD_LOG_30_DAYS"
            />
          </label>
          <label>
            {tx("Title", "Başlık")}
            <input value={form.title} onChange={(event) => updateForm("title", event.target.value)} placeholder="30 Day Food Logger" />
          </label>
          <label>
            {tx("Metric", "Metrik")}
            <select value={form.metricKey} onChange={(event) => updateForm("metricKey", event.target.value)}>
              <option value="">{tx("Select metric", "Metrik seçin")}</option>
              {metricKeys.map((metric) => <option key={metric} value={metric}>{humanizeFeature(metric)}</option>)}
            </select>
          </label>
          <label>
            {tx("Category", "Kategori")}
            <select value={form.category} onChange={(event) => updateForm("category", event.target.value)}>
              {ACHIEVEMENT_CATEGORIES.map((category) => <option key={category} value={category}>{humanizeFeature(category)}</option>)}
            </select>
          </label>
          <label>
            {tx("Tier", "Seviye")}
            <select value={form.tier} onChange={(event) => updateForm("tier", event.target.value)}>
              {ACHIEVEMENT_TIERS.map((tier) => <option key={tier} value={tier}>{humanizeFeature(tier)}</option>)}
            </select>
          </label>
          <label>
            {tx("Target", "Hedef")}
            <input min="1" type="number" value={form.targetValue} onChange={(event) => updateForm("targetValue", event.target.value)} />
          </label>
          <label>
            {tx("Sort", "Sıra")}
            <input min="0" type="number" value={form.sortOrder} onChange={(event) => updateForm("sortOrder", event.target.value)} />
          </label>
          <label>
            {tx("Status", "Durum")}
            <select value={form.active} onChange={(event) => updateForm("active", event.target.value)}>
              <option value="true">{tx("Active", "Aktif")}</option>
              <option value="false">{tx("Inactive", "Etkin değil")}</option>
            </select>
          </label>
          <label className="wide-field">
            {tx("Description", "Açıklama")}
            <input value={form.description} onChange={(event) => updateForm("description", event.target.value)} placeholder="Describe what the user needs to do." />
          </label>
          <div className="form-actions">
            <button className="ghost-button" onClick={() => {
              setSelected(null);
              setForm(emptyAchievementForm);
              setEditorOpen(false);
            }} type="button">{tx("Clear", "Temizle")}</button>
            <button className="primary-button" disabled={saving} type="submit">{saving ? tx("Saving...", "Kaydediliyor...") : selected ? tx("Save changes", "Değişiklikleri kaydet") : tx("Create", "Oluştur")}</button>
          </div>
            </form>
          </section>
        </div>
      )}

      <DataTable
        columns={[tx("Achievement", "Başarım"), tx("Metric", "Metrik"), tx("Target", "Hedef"), tx("State", "Durum"), tx("Order", "Sıra")]}
        rows={definitions.map((definition) => [
          <div className="entity-cell">
            <strong>{definition.title ?? definition.code ?? "-"}</strong>
            <small>{definition.code ?? "-"} | {definition.description ?? "-"}</small>
          </div>,
          <div className="badge-stack">
            <Badge value={definition.metricKey} />
            <Badge value={definition.category} tone="neutral" />
          </div>,
          <div className="table-stack">
            <span>{formatValue(definition.targetValue)}</span>
            <small>{definition.tier ?? "-"}</small>
          </div>,
          <Badge value={definition.active ? "ACTIVE" : "INACTIVE"} tone={definition.active ? "good" : "warn"} />,
          <div className="table-stack">
            <span>{formatValue(definition.sortOrder)}</span>
            <button className="ghost-button" disabled={saving || !definition.active} onClick={(event) => {
              event.stopPropagation();
              deactivate(definition);
            }} type="button">{tx("Deactivate", "Devre dışı bırak")}</button>
          </div>
        ])}
        rowData={definitions}
        onRowClick={startEdit}
        empty={tx("No achievement definitions found.", "Başarım tanımı bulunamadı.")}
      />
    </div>
  );
}
