import { useEffect, useMemo, useState } from "react";
import { request } from "./api";
import AiRequestTimeline from "./AiRequestTimeline";
import { AiOverviewPanel as Panel } from "./AiOverviewPanel";
import { useAdminLocale } from "./admin/locale";
import { sectionPaths } from "./admin/navigation";
import type { AiMonitoringSummary, AiOperationsPolicy } from "./types";



export default function AiCostWorkbench({ summary, policy }: { summary: AiMonitoringSummary; policy: AiOperationsPolicy | null }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const [fx, setFx] = useState<{ source: string; rateDate: string; usdToEur: number; stale: boolean } | null>(null);
  const [fxUnavailable, setFxUnavailable] = useState(false);
  const [fxAttempt, setFxAttempt] = useState(0);
  const [fxLoading, setFxLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setFxLoading(true);
    void request<{ source: string; rateDate: string; usdToEur: number; stale: boolean }>("/api/v1/admin/ai/monitoring/exchange-rate").then(value => {
      if (active && Number.isFinite(value.usdToEur) && value.usdToEur > 0) { setFx(value); setFxUnavailable(false); }
      else if (active) setFxUnavailable(true);
    }).catch(() => { if (active) setFxUnavailable(true); }).finally(() => { if (active) setFxLoading(false); });
    const timer = window.setInterval(() => setFxAttempt(value => value + 1), 60000);
    return () => { active = false; window.clearInterval(timer); };
  }, [fxAttempt]);
  const [selectedCurrency, setCurrency] = useState("");
  const [group, setGroup] = useState<"feature" | "model">("feature");
  const currencies = [...new Set([...Object.keys(summary.estimatedCostByCurrency ?? {}), ...(summary.segments ?? []).map(row => row.costCurrency), ...(summary.providerModels ?? []).map(row => row.costCurrency)].filter((value): value is string => Boolean(value)))].sort();
  const converted = selectedCurrency === "USD_EUR" && fx != null;
  const currency = converted ? "USD" : currencies.includes(selectedCurrency) ? selectedCurrency : currencies.find(value => value === policy?.costCurrency) ?? currencies.find(value => value !== "UNSPECIFIED") ?? currencies[0];
  const displayCurrency = converted ? "EUR" : currency;
  const conversionRate = converted ? fx!.usdToEur : 1;
  const money = (value: number) => new Intl.NumberFormat(tr ? "tr-TR" : "en-GB", { minimumFractionDigits: 2, maximumFractionDigits: 6 }).format(value * conversionRate) + " " + (displayCurrency ?? "");
  const rows = useMemo(() => {
    const grouped = new Map<string, { name: string; cost: number; requests: number }>();
    const records = group === "feature"
      ? (summary.segments ?? []).map(row => ({ ...row, name: row.requestType ?? "UNKNOWN" }))
      : (summary.providerModels ?? []).map(row => ({ ...row, name: [row.provider, row.model].filter(Boolean).join(" / ") || "UNKNOWN" }));
    for (const row of records) {
      if (!currency || row.costCurrency !== currency) continue;
      const item = grouped.get(row.name) ?? { name: row.name, cost: 0, requests: 0 };
      item.cost += Number(row.estimatedCost ?? 0);
      item.requests += Number(row.requestCount ?? 0);
      grouped.set(row.name, item);
    }
    return [...grouped.values()].sort((a, b) => b.cost - a.cost);
  }, [summary, currency, group]);
  const total = currency ? summary.estimatedCostByCurrency?.[currency] : undefined;
  const budget = policy?.maxCostPer24Hours;
  const budgetAvailable = summary.windowHours === 24 && currency === policy?.costCurrency && total != null && budget != null && budget > 0;
  const budgetPercent = budgetAvailable ? total! / budget! * 100 : 0;
  const featureNames: Record<string, [string, string]> = {
    PHOTO_MEAL_LOG: ["Fotoğraf analizi", "Photo analysis"], VOICE_FOOD_LOG: ["Sesli öğün kaydı", "Voice food log"],
    AI_DAILY_INSIGHT: ["Günlük değerlendirme", "Daily insight"], AI_WEEKLY_INSIGHT: ["Haftalık değerlendirme", "Weekly insight"],
    AI_NUTRITION_PLAN: ["Beslenme planı", "Nutrition plan"], AI_WORKOUT_PLAN: ["Antrenman planı", "Workout plan"],
    AI_RECIPE_GENERATION: ["Tarif oluşturma", "Recipe generation"], AI_MEAL_PREPARATION_GUIDE: ["Öğün hazırlama rehberi", "Meal preparation guide"]
  };
  return <Panel className="ai-cost-workbench" title={tr ? "AI maliyet merkezi" : "AI cost centre"} description={tr ? "Kayıtlı tahmini kullanım maliyetleri; sağlayıcı faturası değildir. Para birimleri birleştirilmez." : "Recorded estimated usage costs, not provider invoices. Currencies are kept separate."}>
    <div className="ai-cost-controls">
      <div className="segmented-control" role="group" aria-label={tr ? "Maliyet kırılımı" : "Cost breakdown"}>
        <button type="button" className={group === "feature" ? "active" : ""} aria-pressed={group === "feature"} onClick={() => setGroup("feature")}>{tr ? "Özellik" : "Feature"}</button>
        <button type="button" className={group === "model" ? "active" : ""} aria-pressed={group === "model"} onClick={() => setGroup("model")}>{tr ? "Sağlayıcı / model" : "Provider / model"}</button>
      </div>
      <label>{tr ? "Para birimi" : "Currency"}<select value={converted ? "USD_EUR" : currency ?? ""} onChange={event => setCurrency(event.target.value)} disabled={!currencies.length}>{!currencies.length && <option value="">—</option>}{currencies.map(value => <option key={value}>{value}</option>)}{currencies.includes("USD") && <option value="USD_EUR" disabled={!fx}>{tr ? "EUR · USD’den dönüşüm" : "EUR · converted from USD"}</option>}</select></label>
    </div>
    {converted && fx && <p className="ai-fx-note">{tr ? "USD kayıtlarının EUR karşılığı" : "EUR equivalent of USD records"} · {fx.source} · {fx.rateDate} · 1 USD = {fx.usdToEur.toFixed(6)} EUR. {tr ? "Tüm dönem son referans kurla çevrilir; tarihsel veya anlık işlem kuru değildir." : "The entire period uses the latest reference rate, not historical or live trading rates."}{fx.stale && (tr ? " Son sorgu başarısız; önbellekteki kur kullanılıyor." : "Latest fetch failed; using the cached rate.")}</p>}
    {fxUnavailable && <div className="ai-fx-note"><small>{tr ? "Kur servislerine ulaşılamadı. Otomatik yeniden denenecek." : "Rate services unavailable. Retrying automatically."}</small> <button type="button" className="ghost-button" disabled={fxLoading} onClick={() => setFxAttempt(value => value + 1)}>{tr ? "Kuru yeniden dene" : "Retry exchange rate"}</button></div>}
    <div className="ai-cost-layout">
      <div className="ai-cost-summary">
        <span>{tr ? "Dönemin tahmini maliyeti" : "Estimated period cost"}</span>
        <strong>{total == null ? "—" : money(total)}</strong>
        <small>{tr ? "Seçili izleme dönemi" : "Selected monitoring window"}</small>
        {total === 0 && rows.some(row => row.requests > 0) && <small>{tr ? "İstek var, kayıtlı maliyet sıfır. Bu değer kullanımın ücretsiz olduğunu doğrulamaz; model fiyatlarını ve maliyet kaydını kontrol edin." : "Requests exist but recorded cost is zero. This does not confirm free usage; check model pricing and cost tracking."}</small>}
        <div className="ai-budget-meter">
          <span>{tr ? "24 saatlik bütçe kullanımı" : "24-hour budget usage"}</span>
          {budgetAvailable ? <><strong>{budgetPercent.toFixed(1)}%</strong><progress max={100} value={Math.min(100, budgetPercent)} aria-label={tr ? "Bütçe kullanımı" : "Budget usage"} /><small>{money(total!)} / {money(budget!)}</small>{budgetPercent > 100 && <small>{tr ? "Bütçe eşiği aşıldı" : "Budget threshold exceeded"}</small>}</> : <small>{tr ? "Karşılaştırma için 24 saatlik dönemi ve politika para birimini seçin. Tanımlı bütçe gerekir." : "Select the 24-hour window and policy currency to compare against a configured budget."}</small>}
        </div>
      </div>
      <AiRequestTimeline summary={summary} currency={currency} displayCurrency={displayCurrency} conversionRate={conversionRate} />
    </div>
    <div className="ai-feature-list">{rows.map(row => <article className="ai-feature-row" key={row.name}>
      <div className="ai-feature-name"><span className="ai-feature-mark" aria-hidden="true">{group === "feature" ? "AI" : "M"}</span><div><strong>{group === "feature" ? (featureNames[row.name]?.[tr ? 0 : 1] ?? row.name) : row.name}</strong>{group === "feature" && <small>{row.name}</small>}</div></div>
      <dl><div><dt>{tr ? "İstek" : "Requests"}</dt><dd>{row.requests.toLocaleString(tr ? "tr-TR" : "en-GB")}</dd></div><div><dt>{tr ? "Tahmini maliyet" : "Estimated cost"}</dt><dd>{money(row.cost)}</dd></div><div><dt>{tr ? "İstek başına" : "Per request"}</dt><dd>{row.requests > 0 ? money(row.cost / row.requests) : "—"}</dd></div></dl>
      {group === "feature" && <a className="ghost-button" aria-label={(tr ? "İstekleri incele: " : "Review requests: ") + (featureNames[row.name]?.[tr ? 0 : 1] ?? row.name)} href={sectionPaths.aiRequests + "?requestType=" + encodeURIComponent(row.name)}>{tr ? "İstekleri incele" : "Review requests"} →</a>}
    </article>)}{!rows.length && <p>{tr ? "Maliyet kırılımı bulunamadı." : "No cost breakdown available."}</p>}</div>
  </Panel>;
}
