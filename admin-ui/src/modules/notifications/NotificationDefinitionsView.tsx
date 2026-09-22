import { CSSProperties, FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { formatRequestError, request } from "../../api";
import { LoadState, MetricCard, Panel, SectionToolbar } from "../../AdminPrimitives";
import { CommunicationBars, CommunicationHero } from "../../CommunicationsPrimitives";
import { useAdminLocale } from "../../admin/locale";
import { AdminAccessProfile, AdminApprovalRequest, NotificationDefinition } from "../../types";
import { ApprovalSubmissionNotice } from "../../admin/shared";

type NotificationDefinitionDraft = {
  key: string;
  displayName: string;
  description: string;
  enabled: boolean;
  channel: NotificationDefinition["channel"];
  severity: "" | NonNullable<NotificationDefinition["severity"]>;
  targetRoute: string;
  titleEn: string;
  messageEn: string;
  titleTr: string;
  messageTr: string;
};

const EMPTY_DRAFT: NotificationDefinitionDraft = {
  key: "",
  displayName: "",
  description: "",
  enabled: true,
  channel: "IN_APP_AND_PUSH",
  severity: "INFO",
  targetRoute: "",
  titleEn: "",
  messageEn: "",
  titleTr: "",
  messageTr: ""
};

export function NotificationDefinitionsView({ onError, accessProfile }: { onError: (message: string | null) => void; accessProfile: AdminAccessProfile | null }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const [definitions, setDefinitions] = useState<NotificationDefinition[]>([]);
  const [state, setState] = useState<LoadState>("idle");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [draft, setDraft] = useState<NotificationDefinitionDraft>(EMPTY_DRAFT);
  const [notice, setNotice] = useState<string | null>(null);
  const [approvalNotice, setApprovalNotice] = useState<{ request: AdminApprovalRequest; message: string } | null>(null);
  const [filter, setFilter] = useState("");
  const [approvalReason, setApprovalReason] = useState("");
  const editorRef = useRef<HTMLFormElement | null>(null);
  const displayNameRef = useRef<HTMLInputElement | null>(null);

  const selected = definitions.find((definition) => definition.id === selectedId) ?? null;
  const isMealReminderDefinition = Boolean(selected?.protectedDefinition && selected.key.startsWith("meal_reminder_"));
  const isSubscriptionDefinition = Boolean(selected?.protectedDefinition
    && (selected.key.startsWith("subscription_") || selected.key === "ai_addon_purchased")
    && selected.key !== "subscription_provider_alert");
  const requiresApproval = isMealReminderDefinition || isSubscriptionDefinition;
  useEffect(() => { if (notice) setApprovalNotice(null); }, [notice]);
  const visibleDefinitions = useMemo(() => {
    const query = filter.trim().toLocaleLowerCase();
    if (!query) return definitions;
    return definitions.filter((definition) => [definition.key, definition.displayName, definition.description]
      .some((value) => value?.toLocaleLowerCase().includes(query)));
  }, [definitions, filter]);

  const load = useCallback(async () => {
    setState("loading");
    try {
      const result = await request<NotificationDefinition[]>("/api/v1/admin/notification-definitions");
      setDefinitions(result);
      setState("ready");
      onError(null);
    } catch (error) {
      const message = formatRequestError(error);
      setState("error");
      onError(message);
    }
  }, [onError]);

  useEffect(() => { void load(); }, [load]);

  function edit(definition: NotificationDefinition) {
    setSelectedId(definition.id ?? null);
    setDraft({
      key: definition.key,
      displayName: definition.displayName,
      description: definition.description ?? "",
      enabled: definition.enabled,
      channel: definition.channel,
      severity: definition.severity ?? "",
      targetRoute: definition.targetRoute ?? "",
      titleEn: definition.titleEn ?? "",
      messageEn: definition.messageEn ?? "",
      titleTr: definition.titleTr ?? "",
      messageTr: definition.messageTr ?? ""
    });
    setNotice(null);
    setApprovalReason("");
    window.requestAnimationFrame(() => {
      editorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      displayNameRef.current?.focus({ preventScroll: true });
    });
  }

  function reset() {
    setSelectedId(null);
    setDraft(EMPTY_DRAFT);
    setNotice(null);
    setActionState("idle");
  }

  function startNewDefinition() {
    reset();
    setNotice("New definition form opened. Enter a backend event key, localized copy, and delivery policy.");
    window.requestAnimationFrame(() => {
      editorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      displayNameRef.current?.focus({ preventScroll: true });
    });
  }

  function update<K extends keyof NotificationDefinitionDraft>(field: K, value: NotificationDefinitionDraft[K]) {
    setDraft((current) => ({ ...current, [field]: value }));
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setActionState("loading");
    setNotice(null);
    const payload = {
      ...draft,
      key: draft.key.trim().toLocaleLowerCase(),
      displayName: draft.displayName.trim(),
      description: draft.description.trim() || null,
      severity: draft.severity || null,
      targetRoute: draft.targetRoute.trim() || null,
      titleEn: draft.titleEn.trim() || null,
      messageEn: draft.messageEn.trim() || null,
      titleTr: draft.titleTr.trim() || null,
      messageTr: draft.messageTr.trim() || null
    };
    try {
      if (isMealReminderDefinition && selectedId) {
        const approval = await request<AdminApprovalRequest>(`/api/v1/admin/meal-reminder-automation/definitions/${selectedId}/publish-request`, {
          method: "POST", body: { definition: payload, reason: approvalReason.trim() }
        });
        setApprovalNotice({ request: approval, message: "Meal reminder copy publication is pending maker-checker approval. Active copy has not changed." });
        setActionState("ready");
        setApprovalReason("");
        return;
      }
      if (isSubscriptionDefinition && selectedId) {
        const approval = await request<AdminApprovalRequest>(`/api/v1/admin/subscription-notifications/definitions/${selectedId}/publish-request`, {
          method: "POST", body: { definition: payload, reason: approvalReason.trim() }
        });
        setApprovalNotice({ request: approval, message: "Subscription notification publication is pending maker-checker approval. Active copy has not changed." });
        setActionState("ready");
        setApprovalReason("");
        return;
      }
      const saved = await request<NotificationDefinition>(selectedId ? `/api/v1/admin/notification-definitions/${selectedId}` : "/api/v1/admin/notification-definitions", { method: selectedId ? "PUT" : "POST", body: payload });
      setSelectedId(saved.id ?? null);
      setNotice(selectedId ? "Notification definition updated." : "Notification definition created.");
      setActionState("ready");
      await load();
      edit(saved);
      setNotice(selectedId ? "Notification definition updated." : "Notification definition created.");
    } catch (error) {
      const message = formatRequestError(error);
      setActionState("error");
      setNotice(message);
      onError(message);
    }
  }

  const enabledCount = definitions.filter((definition) => definition.enabled).length;
  const pushCount = definitions.filter((definition) => definition.enabled && definition.channel !== "IN_APP").length;
  const protectedCount = definitions.filter((definition) => definition.protectedDefinition).length;
  const channelDistribution = ["IN_APP", "PUSH", "IN_APP_AND_PUSH"].map((channel) => ({ label: channel === "IN_APP" ? tx("In-app", "Uygulama içi") : channel === "PUSH" ? "Push" : tx("In-app + push", "Uygulama içi + push"), value: definitions.filter((definition) => definition.enabled && definition.channel === channel).length })).filter((item) => item.value > 0);
  const severityDistribution = ["INFO", "WARNING", "CRITICAL"].map((severity) => ({ label: severity, value: definitions.filter((definition) => definition.enabled && (definition.severity ?? "INFO") === severity).length, tone: severity === "CRITICAL" ? "danger" as const : severity === "WARNING" ? undefined : "accent" as const })).filter((item) => item.value > 0);
  const localizedCount = definitions.filter((definition) => Boolean(definition.titleEn && definition.messageEn && definition.titleTr && definition.messageTr)).length;
  const routedCount = definitions.filter((definition) => Boolean(definition.targetRoute)).length;

  return (
    <div className="stack communications-ops-view notification-definitions-view notification-definitions-page">
      <SectionToolbar
        title={tx("Notification definitions", "Bildirim tanımları")}
        description={tx("Manage system-event visibility, delivery channel, route, severity, and localized user-facing copy.", "Sistem olaylarının görünürlüğünü, teslimat kanalını, rotasını, önem düzeyini ve yerelleştirilmiş metnini yönetin.")}
        state={state}
        onReload={() => void load()}
      >
        <button className="primary-button" type="button" onClick={startNewDefinition}>{tx("New definition", "Yeni tanım")}</button>
      </SectionToolbar>
      <CommunicationHero eyebrow={tx("Message contract", "Mesaj sözleşmesi")} title={tx("Keep system notifications consistent across channels and languages.", "Sistem bildirimlerini kanallar ve diller arasında tutarlı tutun.")} description={tx("Search definitions, review protected templates and update eligible rules without mixing campaign content into system messaging.", "Tanımları arayın, korumalı şablonları inceleyin ve kampanya içeriğini sistem mesajlarıyla karıştırmadan uygun kuralları güncelleyin.")} status={<span className="communication-status-pill good">{enabledCount} {tx("ENABLED", "ETKİN")}</span>} />

      <div className="metric-grid notification-definition-metrics">
        <MetricCard label={tx("Definitions", "Tanımlar")} value={String(definitions.length)} hint={tx("Registered notification types", "Kayıtlı bildirim türleri")} />
        <MetricCard label={tx("Enabled", "Etkin")} value={String(enabledCount)} hint={tx("Visible in at least one channel", "En az bir kanalda görünür")} />
        <MetricCard label={tx("Push eligible", "Push uygun")} value={String(pushCount)} hint={tx("Push or combined channel", "Push veya birleşik kanal")} />
        <MetricCard label={tx("Protected", "Korumalı")} value={String(protectedCount)} hint={tx("Safety-critical definitions", "Güvenlik açısından kritik tanımlar")} />
      </div>
      <section className="notification-definition-insights" aria-label={tx("Definition analytics", "Tanım analizleri")}>
        <article className="communication-insight-strip"><div><p className="eyebrow">{tx("Channel coverage", "Kanal kapsamı")}</p><h3>{tx("Enabled definitions by channel", "Kanala göre etkin tanımlar")}</h3><p>{tx("Shows where active system messages are eligible to appear.", "Etkin sistem mesajlarının hangi kanallarda gösterilebildiğini belirtir.")}</p></div><CommunicationBars items={channelDistribution} empty={tx("No enabled channel definitions.", "Etkin kanal tanımı yok.")} /></article>
        <article className="communication-insight-strip"><div><p className="eyebrow">{tx("Attention profile", "Önem profili")}</p><h3>{tx("Enabled definitions by severity", "Önem düzeyine göre etkin tanımlar")}</h3><p>{tx("Critical and warning rules remain visually distinct from routine information.", "Kritik ve uyarı kuralları rutin bilgilerden ayrı görünür.")}</p></div><CommunicationBars items={severityDistribution} empty={tx("No severity data returned.", "Önem düzeyi verisi bulunamadı.")} /></article>
        <article className="definition-readiness-card"><div className="definition-readiness-ring" style={{ "--coverage": `${definitions.length ? Math.round(localizedCount / definitions.length * 100) : 0}%` } as CSSProperties}><strong>{definitions.length ? Math.round(localizedCount / definitions.length * 100) : 0}%</strong><span>{tx("localized", "yerelleştirilmiş")}</span></div><div><p className="eyebrow">{tx("Copy readiness", "Metin hazırlığı")}</p><h3>{localizedCount} / {definitions.length}</h3><p>{tx("Definitions with complete EN and TR title/message pairs.", "Eksiksiz EN ve TR başlık/mesaj çiftine sahip tanımlar.")}</p></div></article>
        <article className="definition-readiness-card"><div className="definition-readiness-ring route" style={{ "--coverage": `${definitions.length ? Math.round(routedCount / definitions.length * 100) : 0}%` } as CSSProperties}><strong>{routedCount}</strong><span>{tx("routes", "rota")}</span></div><div><p className="eyebrow">{tx("Action coverage", "Eylem kapsamı")}</p><h3>{tx("Destination readiness", "Hedef hazırlığı")}</h3><p>{tx("Definitions with an explicit in-app destination route.", "Açık bir uygulama içi hedef rotası bulunan tanımlar.")}</p></div></article>
      </section>

      <div className="form-notice warning notification-definition-boundary">
        <strong>{tx("Definitions do not create event triggers.", "Tanımlar olay tetikleyicisi oluşturmaz.")}</strong> {tx("Existing backend events use matching keys automatically. Use Campaigns for manual broadcasts; a brand-new automatic event still requires a backend producer.", "Mevcut backend olayları eşleşen anahtarları otomatik kullanır. Manuel toplu gönderimler için Kampanyalar'ı kullanın; yeni bir otomatik olay yine backend üreticisi gerektirir.")}
      </div>
      <div className="notification-definition-workflow" aria-label={tx("Notification editing steps", "Bildirim düzenleme adımları")}>
        <span><strong>1</strong> {tx("Select a notification type from the list", "Listeden bir bildirim türü seçin")}</span>
        <span><strong>2</strong> {tx("Edit its policy and EN/TR copy", "Politikasını ve EN/TR metnini düzenleyin")}</span>
        <span><strong>3</strong> {tx("Select Save changes", "Değişiklikleri kaydet'i seçin")}</span>
      </div>

      <div className="notification-definition-layout">
        <Panel
          title={tx("Registered types", "Kayıtlı türler")}
          description={tx("Select a type to edit its global policy and copy.", "Genel politikasını ve metnini düzenlemek için bir tür seçin.")}
          actions={<input aria-label={tx("Filter notification definitions", "Bildirim tanımlarını filtrele")} placeholder={tx("Filter by name or key", "Ad veya anahtara göre filtrele")} value={filter} onChange={(event) => setFilter(event.target.value)} />}
          className="notification-definition-list-panel"
        >
          <div className="notification-definition-list">
            {visibleDefinitions.map((definition) => (
              <button
                className={`notification-definition-card ${selectedId === definition.id ? "is-selected" : ""}`}
                key={definition.key}
                onClick={() => edit(definition)}
                type="button"
              >
                <span className="notification-definition-card-title">
                  <strong>{definition.displayName}</strong>
                  <span className={`badge ${definition.enabled ? "good" : "neutral"}`}>{definition.enabled ? tx("Enabled", "Etkin") : tx("Disabled", "Devre dışı")}</span>
                </span>
                <code>{definition.key}</code>
                <span className="notification-definition-card-meta">
                  <span>{definition.channel.replaceAll("_", " + ")}</span>
                  {definition.protectedDefinition && <span className="badge warn">{tx("Protected", "Korumalı")}</span>}
                  <span className="notification-definition-edit-label">{tx("Edit", "Düzenle")} →</span>
                </span>
              </button>
            ))}
            {state !== "loading" && !visibleDefinitions.length && <p className="notification-definition-empty">{tx("No definitions match this filter.", "Bu filtreyle eşleşen tanım yok.")}</p>}
          </div>
        </Panel>

        <Panel
          title={selectedId ? tx("Edit definition", "Tanımı düzenle") : tx("New definition", "Yeni tanım")}
          description={selectedId ? tx("The event key is immutable after creation.", "Olay anahtarı oluşturulduktan sonra değiştirilemez.") : tx("Use the exact lowercase key emitted by the backend event.", "Backend olayının ürettiği küçük harfli anahtarı aynen kullanın.")}
          className="notification-definition-editor"
        >
          <form ref={editorRef} onSubmit={(event) => void save(event)}>
            {approvalNotice && <ApprovalSubmissionNotice {...approvalNotice} isOwner={accessProfile?.role === "OWNER"} />}
            {notice && <div className={`form-notice ${actionState === "error" ? "warning" : ""}`} role="status">{notice}</div>}
            {selected?.protectedDefinition && <div className="form-notice warning">{requiresApproval ? "This user-facing definition is protected. Changes create an approval request; active copy remains unchanged until a different authorized admin approves it." : "This safety-critical definition cannot be disabled."}</div>}
            <div className="campaign-form-grid notification-definition-form-grid">
              <label className="span-2">{tx("Display name", "Görünen ad")}<input ref={displayNameRef} required maxLength={120} value={draft.displayName} onChange={(event) => update("displayName", event.target.value)} /></label>
              <label className="span-2">{tx("Event key", "Olay anahtarı")}<input required disabled={Boolean(selectedId)} maxLength={80} pattern="[a-z0-9_]+" value={draft.key} onChange={(event) => update("key", event.target.value.replace(/[^a-zA-Z0-9_]/g, "").toLocaleLowerCase())} /></label>
              <label className="span-4">{tx("Operational description", "Operasyon açıklaması")}<input maxLength={500} value={draft.description} onChange={(event) => update("description", event.target.value)} /></label>
              <label>{tx("Channel", "Kanal")}<select disabled={isMealReminderDefinition} value={draft.channel} onChange={(event) => update("channel", event.target.value as NotificationDefinitionDraft["channel"])}><option value="IN_APP">In-app</option><option value="PUSH">Push</option><option value="IN_APP_AND_PUSH">In-app + push</option></select></label>
              <label>{tx("Severity", "Önem düzeyi")}<select disabled={isMealReminderDefinition} value={draft.severity} onChange={(event) => update("severity", event.target.value as NotificationDefinitionDraft["severity"])}><option value="">{tx("Keep event default", "Olay varsayılanını koru")}</option><option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option></select></label>
              <label className="span-2">{tx("Target route", "Hedef rota")}<input disabled={isMealReminderDefinition} maxLength={255} placeholder="/notifications or app route" value={draft.targetRoute} onChange={(event) => update("targetRoute", event.target.value)} /></label>
              <label className="notification-definition-toggle span-4"><input checked={draft.enabled} disabled={Boolean(selected?.protectedDefinition)} type="checkbox" onChange={(event) => update("enabled", event.target.checked)} /><span><strong>{tx("Definition enabled", "Tanım etkin")}</strong><small>{tx("Disabling suppresses both in-app visibility and push delivery for this event type.", "Devre dışı bırakmak bu olay türünün uygulama içi görünürlüğünü ve push teslimatını durdurur.")}</small></span></label>
              <div className="notification-definition-locale span-2">
                <h4>English (en)</h4>
                <label>Title override<input maxLength={120} placeholder="Keep hardcoded event title when empty" value={draft.titleEn} onChange={(event) => update("titleEn", event.target.value)} /></label>
                <label>Message override<textarea maxLength={1000} placeholder="Keep hardcoded event message when empty" value={draft.messageEn} onChange={(event) => update("messageEn", event.target.value)} /></label>
              </div>
              <div className="notification-definition-locale span-2">
                <h4>Türkçe (tr)</h4>
                <label>Başlık değişikliği<input maxLength={120} placeholder="Boşsa mevcut olay başlığı kullanılır" value={draft.titleTr} onChange={(event) => update("titleTr", event.target.value)} /></label>
                <label>Mesaj değişikliği<textarea maxLength={1000} placeholder="Boşsa mevcut olay mesajı kullanılır" value={draft.messageTr} onChange={(event) => update("messageTr", event.target.value)} /></label>
              </div>
            </div>
            <p className="notification-definition-template-help">{isMealReminderDefinition ? <>Meal reminder placeholders are typed: only dinner-kcal copy may use <code>{"{remainingKcal}"}</code>. TR and EN fields are required.</> : <>Allowed placeholders: <code>{"{originalTitle}"}</code>, <code>{"{originalMessage}"}</code>, <code>{"{note}"}</code>. Empty localized fields preserve the producer's current copy.</>}</p>
            {requiresApproval && <label className="wide-field">Required approval reason<textarea required maxLength={500} value={approvalReason} onChange={(event) => setApprovalReason(event.target.value)} placeholder="Explain why this user-facing copy should change." /><small>{approvalReason.length}/500</small></label>}
            <div className="inline-actions notification-definition-actions">
              {selectedId && <button className="ghost-button" type="button" onClick={reset}>{tx("Cancel editing", "Düzenlemeyi iptal et")}</button>}
              <button className="primary-button" disabled={actionState === "loading" || (requiresApproval && !approvalReason.trim())} type="submit">{actionState === "loading" ? tx("Saving...", "Kaydediliyor...") : requiresApproval ? tx("Request copy publication", "Metin yayını talep et") : selectedId ? tx("Save changes", "Değişiklikleri kaydet") : tx("Create definition", "Tanım oluştur")}</button>
            </div>
          </form>
        </Panel>
      </div>
    </div>
  );
}
