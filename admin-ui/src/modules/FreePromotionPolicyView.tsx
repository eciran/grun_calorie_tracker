import { FormEvent, useCallback, useEffect, useState } from "react";
import { formatRequestError, request } from "../api";
import { AdminAccessProfile } from "../types";
import { LoadState, MetricCard, Panel, SectionToolbar } from "../AdminPrimitives";
import { useAdminLocale } from "../admin/locale";

type Policy = {
  id: number;
  version: number;
  enabled: boolean;
  minimumIntervalHours: number;
  maxImpressions24h: number;
  dismissCooldownHours: number;
  minimumSessionNumber: number;
  rolloutPercentage: number;
  campaignVersion: number;
  changeReason?: string;
  updatedBy?: string;
  updatedAt?: string;
};

type Draft = Pick<Policy, "version" | "enabled" | "minimumIntervalHours" | "maxImpressions24h" | "dismissCooldownHours" | "minimumSessionNumber" | "rolloutPercentage"> & { changeReason: string };
const API = "/api/v1/admin/free-promotion";

type QuotaPolicy = Omit<Policy, "minimumSessionNumber">;
type QuotaDraft = Pick<QuotaPolicy, "version" | "enabled" | "minimumIntervalHours" | "maxImpressions24h" | "dismissCooldownHours" | "rolloutPercentage"> & { changeReason: string };
const QUOTA_API = `${API}/quota-exhausted`;

function toDraft(policy: Policy): Draft {
  return {
    version: policy.version,
    enabled: policy.enabled,
    minimumIntervalHours: policy.minimumIntervalHours,
    maxImpressions24h: policy.maxImpressions24h,
    dismissCooldownHours: policy.dismissCooldownHours,
    minimumSessionNumber: policy.minimumSessionNumber,
    rolloutPercentage: policy.rolloutPercentage,
    changeReason: ""
  };
}

function toQuotaDraft(policy: QuotaPolicy): QuotaDraft {
  return {
    version: policy.version,
    enabled: policy.enabled,
    minimumIntervalHours: policy.minimumIntervalHours,
    maxImpressions24h: policy.maxImpressions24h,
    dismissCooldownHours: policy.dismissCooldownHours,
    rolloutPercentage: policy.rolloutPercentage,
    changeReason: ""
  };
}

export function FreePromotionPolicyView({ accessProfile, onError }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (english: string, turkish: string) => tr ? turkish : english;
  const canManage = Boolean(accessProfile?.permissions?.includes("GROWTH_MANAGE"));
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [quotaPolicy, setQuotaPolicy] = useState<QuotaPolicy | null>(null);
  const [quotaDraft, setQuotaDraft] = useState<QuotaDraft | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState("loading");
    try {
      const [next, nextQuota] = await Promise.all([
        request<Policy>(API),
        request<QuotaPolicy>(QUOTA_API)
      ]);
      setPolicy(next);
      setDraft(toDraft(next));
      setQuotaPolicy(nextQuota);
      setQuotaDraft(toQuotaDraft(nextQuota));
      setState("ready");
      setNotice(null);
      onError(null);
    } catch (error) {
      const message = formatRequestError(error);
      setState("error");
      onError(message);
    }
  }, [onError]);

  useEffect(() => { void load(); }, [load]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!draft || !canManage || !draft.changeReason.trim()) return;
    setActionState("loading");
    setNotice(null);
    try {
      const saved = await request<Policy>(API, { method: "PUT", body: { ...draft, changeReason: draft.changeReason.trim() } });
      setPolicy(saved);
      setDraft(toDraft(saved));
      setActionState("ready");
      setNotice(saved.enabled ? `Free promotion is active for ${saved.rolloutPercentage}% of eligible users.` : "Free promotion is disabled. No automatic paywall will be shown.");
      onError(null);
    } catch (error) {
      const message = formatRequestError(error);
      setActionState("error");
      setNotice(message.toLowerCase().includes("version") || message.toLowerCase().includes("conflict")
        ? "This policy changed in another admin session. Refresh before saving again."
        : message);
    }
  }

  async function saveQuotaPolicy(event: FormEvent) {
    event.preventDefault();
    if (!quotaDraft || !canManage || !quotaDraft.changeReason.trim()) return;
    setActionState("loading");
    setNotice(null);
    try {
      const saved = await request<QuotaPolicy>(QUOTA_API, {
        method: "PUT",
        body: { ...quotaDraft, changeReason: quotaDraft.changeReason.trim() }
      });
      setQuotaPolicy(saved);
      setQuotaDraft(toQuotaDraft(saved));
      setActionState("ready");
      setNotice(saved.enabled
        ? tx(`Quota-exhausted offers are active for ${saved.rolloutPercentage}% of eligible paid users.`, `Kotası tükenen kullanıcı teklifleri uygun ücretli kullanıcıların %${saved.rolloutPercentage} kadarı için aktif.`)
        : tx("Quota-exhausted offers are disabled.", "Kotası tükenen kullanıcı teklifleri devre dışı."));
      onError(null);
    } catch (error) {
      const message = formatRequestError(error);
      setActionState("error");
      setNotice(message.toLowerCase().includes("version") || message.toLowerCase().includes("conflict")
        ? tx("This policy changed in another admin session. Refresh before saving again.", "Bu politika başka bir admin oturumunda değişti. Yeniden kaydetmeden önce sayfayı yenileyin.")
        : message);
    }
  }

  return <div className="stack free-promotion-policy-view">
    <SectionToolbar title={tx("Free user paywall", "Ücretsiz kullanıcı ödeme duvarı")} description={tx("Control the optional home-screen plan introduction. Eligibility and frequency are enforced by the backend.", "Ana ekrandaki isteğe bağlı plan tanıtımını yönetin. Uygunluk ve gösterim sıklığı backend tarafından uygulanır.")} state={state} onReload={() => void load()} />
    {notice && <div className={`form-notice ${actionState === "error" ? "warning" : ""}`} role="status">{notice}</div>}
    {!canManage && <div className="form-notice warning" role="note"><strong>{tx("Read-only access.", "Salt okunur erişim.")}</strong> {tx("GROWTH_MANAGE is required to change this policy.", "Bu politikayı değiştirmek için GROWTH_MANAGE izni gerekir.")}</div>}
    <div className="metric-grid">
      <MetricCard label={tx("Current state", "Mevcut durum")} value={policy?.enabled ? "ACTIVE" : "DISABLED"} hint={policy?.enabled ? tx(`${policy.rolloutPercentage}% deterministic rollout`, `%${policy.rolloutPercentage} belirlenmiş dağıtım`) : tx("Fail-closed; no automatic display", "Güvenli kapalı; otomatik gösterim yok")} />
      <MetricCard label={tx("Minimum interval", "Minimum aralık")} value={policy ? tx(`${policy.minimumIntervalHours} hours`, `${policy.minimumIntervalHours} saat`) : "—"} hint={tx("Measured from recorded impressions", "Kaydedilen gösterimlerden ölçülür")} />
      <MetricCard label={tx("24-hour cap", "24 saatlik limit")} value={String(policy?.maxImpressions24h ?? "—")} hint={tx("Maximum impressions per Free account", "Free hesap başına en fazla gösterim")} />
      <MetricCard label={tx("Campaign version", "Kampanya sürümü")} value={String(policy?.campaignVersion ?? "—")} hint={tx(`Policy row version ${policy?.version ?? "—"}`, `Politika satırı sürümü ${policy?.version ?? "—"}`)} />
    </div>
    <Panel title={tx("Eligibility and frequency policy", "Uygunluk ve sıklık politikası")} description={tx("Paid accounts, pending purchases, the first session, unsafe screens, and open dialogs are always suppressed outside these controls.", "Ücretli hesaplar, bekleyen satın almalar, ilk oturum, güvenli olmayan ekranlar ve açık diyaloglar bu kontroller dışında her zaman engellenir.")}>
      {draft ? <form className="meal-reminder-policy-form" onSubmit={(event) => void save(event)}>
        <label className="toggle-field span-2"><input disabled={!canManage} type="checkbox" checked={draft.enabled} onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })} />{tx("Enable automatic Free-user plan introduction", "Free kullanıcı plan tanıtımını otomatik etkinleştir")}</label>
        <label>{tx("Minimum interval (hours)", "Minimum aralık (saat)")}<input disabled={!canManage} min={1} max={720} type="number" value={draft.minimumIntervalHours} onChange={(event) => setDraft({ ...draft, minimumIntervalHours: Number(event.target.value) })} /></label>
        <label>{tx("Maximum per rolling 24h", "Kayan 24 saatte maksimum")}<input disabled={!canManage} min={1} max={24} type="number" value={draft.maxImpressions24h} onChange={(event) => setDraft({ ...draft, maxImpressions24h: Number(event.target.value) })} /></label>
        <label>{tx("Dismiss cooldown (hours)", "Kapatma bekleme süresi (saat)")}<input disabled={!canManage} min={1} max={720} type="number" value={draft.dismissCooldownHours} onChange={(event) => setDraft({ ...draft, dismissCooldownHours: Number(event.target.value) })} /></label>
        <label>{tx("Minimum cold-start session", "Minimum soğuk başlangıç oturumu")}<input disabled={!canManage} min={1} max={100} type="number" value={draft.minimumSessionNumber} onChange={(event) => setDraft({ ...draft, minimumSessionNumber: Number(event.target.value) })} /></label>
        <label>{tx("Rollout percentage", "Dağıtım yüzdesi")}<input disabled={!canManage} min={0} max={100} type="number" value={draft.rolloutPercentage} onChange={(event) => setDraft({ ...draft, rolloutPercentage: Number(event.target.value) })} /><small>{tx("0% is a second kill switch even when enabled.", "%0, etkin olduğunda bile ikinci kapatma anahtarıdır.")}</small></label>
        <label className="span-2">{tx("Required audit reason", "Zorunlu denetim nedeni")}<textarea disabled={!canManage} maxLength={500} required value={draft.changeReason} onChange={(event) => setDraft({ ...draft, changeReason: event.target.value })} placeholder={tx("Explain why this frequency or rollout is changing.", "Bu sıklığın veya dağıtımın neden değiştiğini açıklayın.")} /><small>{draft.changeReason.length}/500</small></label>
        {draft.enabled && <div className="form-notice warning span-2" role="note"><strong>{tx("Activation warning:", "Etkinleştirme uyarısı:")}</strong> {tx("Saving will make eligible Free users in the selected rollout cohort see the paywall on a safe home return.", "Kaydettiğinizde seçilen dağıtım grubundaki uygun Free kullanıcılar güvenli bir ana ekran dönüşünde ödeme duvarını görür.")}</div>}
        <div className="inline-actions span-2"><button className="primary-button" disabled={!canManage || !draft.changeReason.trim() || actionState === "loading"} type="submit">{actionState === "loading" ? tx("Saving...", "Kaydediliyor...") : tx("Save policy", "Politikayı kaydet")}</button></div>
      </form> : <p>{tx("No policy configuration returned.", "Politika yapılandırması alınamadı.")}</p>}
    </Panel>
    <div className="metric-grid">
      <MetricCard label={tx("Quota offer state", "Kota teklifi durumu")} value={quotaPolicy?.enabled ? "ACTIVE" : "DISABLED"} hint={quotaPolicy?.enabled ? tx(`${quotaPolicy.rolloutPercentage}% deterministic rollout`, `%${quotaPolicy.rolloutPercentage} belirlenmiş dağıtım`) : tx("Paid quota-exhausted banner is off", "Ücretli kota tükenme bannerı kapalı")} />
      <MetricCard label={tx("Quota offer interval", "Kota teklifi aralığı")} value={quotaPolicy ? tx(`${quotaPolicy.minimumIntervalHours} hours`, `${quotaPolicy.minimumIntervalHours} saat`) : "—"} hint={tx("Measured from recorded impressions", "Kaydedilen gösterimlerden ölçülür")} />
      <MetricCard label={tx("Quota offer 24h cap", "Kota teklifi 24 saat limiti")} value={String(quotaPolicy?.maxImpressions24h ?? "—")} hint={tx("Plus and Pro accounts only", "Yalnız Plus ve Pro hesapları")} />
      <MetricCard label={tx("Quota campaign", "Kota kampanyası")} value={String(quotaPolicy?.campaignVersion ?? "—")} hint={tx("Independent from the Free campaign", "Free kampanyasından bağımsız")} />
    </div>
    <Panel title={tx("AI quota exhausted offers", "YZ kotası tükenen kullanıcı teklifleri")} description={tx("Offer store-priced 15/50 credit packs to active Plus and Pro users with zero remaining credits. Plus users may also be directed to Pro.", "Kalan kredisi sıfır olan aktif Plus ve Pro kullanıcılarına mağaza fiyatlı 15/50 kredi paketleri sunun. Plus kullanıcıları ayrıca Pro'ya yönlendirilebilir.")}>
      {quotaDraft ? <form className="meal-reminder-policy-form" onSubmit={(event) => void saveQuotaPolicy(event)}>
        <label className="toggle-field span-2"><input disabled={!canManage} type="checkbox" checked={quotaDraft.enabled} onChange={(event) => setQuotaDraft({ ...quotaDraft, enabled: event.target.checked })} />{tx("Enable quota-exhausted offers", "Kota tükenme tekliflerini etkinleştir")}</label>
        <label>{tx("Minimum interval (hours)", "Minimum aralık (saat)")}<input disabled={!canManage} min={1} max={720} type="number" value={quotaDraft.minimumIntervalHours} onChange={(event) => setQuotaDraft({ ...quotaDraft, minimumIntervalHours: Number(event.target.value) })} /></label>
        <label>{tx("Maximum per rolling 24h", "Kayan 24 saatte maksimum")}<input disabled={!canManage} min={1} max={24} type="number" value={quotaDraft.maxImpressions24h} onChange={(event) => setQuotaDraft({ ...quotaDraft, maxImpressions24h: Number(event.target.value) })} /></label>
        <label>{tx("Dismiss cooldown (hours)", "Kapatma bekleme süresi (saat)")}<input disabled={!canManage} min={1} max={720} type="number" value={quotaDraft.dismissCooldownHours} onChange={(event) => setQuotaDraft({ ...quotaDraft, dismissCooldownHours: Number(event.target.value) })} /></label>
        <label>{tx("Rollout percentage", "Dağıtım yüzdesi")}<input disabled={!canManage} min={0} max={100} type="number" value={quotaDraft.rolloutPercentage} onChange={(event) => setQuotaDraft({ ...quotaDraft, rolloutPercentage: Number(event.target.value) })} /><small>{tx("0% remains a second kill switch.", "%0 ikinci kapatma anahtarı olarak kalır.")}</small></label>
        <label className="span-2">{tx("Required audit reason", "Zorunlu denetim nedeni")}<textarea disabled={!canManage} maxLength={500} required value={quotaDraft.changeReason} onChange={(event) => setQuotaDraft({ ...quotaDraft, changeReason: event.target.value })} placeholder={tx("Explain why quota-exhausted offer delivery is changing.", "Kota tükenme tekliflerinin neden değiştirildiğini açıklayın.")} /><small>{quotaDraft.changeReason.length}/500</small></label>
        {quotaDraft.enabled && <div className="form-notice warning span-2" role="note"><strong>{tx("Activation warning:", "Etkinleştirme uyarısı:")}</strong> {tx("Only backend-verified active Plus/Pro accounts with zero remaining credits are eligible. Store prices still come from RevenueCat on the device.", "Yalnız backend tarafından doğrulanmış, kalan kredisi sıfır aktif Plus/Pro hesapları uygundur. Mağaza fiyatları cihazda RevenueCat üzerinden alınmaya devam eder.")}</div>}
        <div className="inline-actions span-2"><button className="primary-button" disabled={!canManage || !quotaDraft.changeReason.trim() || actionState === "loading"} type="submit">{actionState === "loading" ? tx("Saving...", "Kaydediliyor...") : tx("Save quota policy", "Kota politikasını kaydet")}</button></div>
      </form> : <p>{tx("No quota promotion policy returned.", "Kota promosyon politikası alınamadı.")}</p>}
    </Panel>
    <div className="form-notice" role="note"><strong>{tx("Runtime safeguards:", "Çalışma zamanı korumaları:")}</strong> {tx("one display per app session, server-issued reservation tokens, a five-minute reservation expiry, exact Free-plan verification, and an impression-time kill-switch recheck.", "uygulama oturumu başına tek gösterim, sunucu tarafından verilen rezervasyon belirteçleri, beş dakikalık rezervasyon süresi, kesin Free plan doğrulaması ve gösterim anında kapatma anahtarı kontrolü.")}</div>
  </div>;
}
