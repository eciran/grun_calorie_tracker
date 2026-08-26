import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { formatRequestError, request } from "../../api";
import { LoadState, MetricCard, Panel, SectionToolbar } from "../../AdminPrimitives";
import { NotificationDefinition } from "../../types";

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

export function NotificationDefinitionsView({ onError }: { onError: (message: string | null) => void }) {
  const [definitions, setDefinitions] = useState<NotificationDefinition[]>([]);
  const [state, setState] = useState<LoadState>("idle");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [draft, setDraft] = useState<NotificationDefinitionDraft>(EMPTY_DRAFT);
  const [notice, setNotice] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const editorRef = useRef<HTMLFormElement | null>(null);
  const displayNameRef = useRef<HTMLInputElement | null>(null);

  const selected = definitions.find((definition) => definition.id === selectedId) ?? null;
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
      const saved = await request<NotificationDefinition>(
        selectedId ? `/api/v1/admin/notification-definitions/${selectedId}` : "/api/v1/admin/notification-definitions",
        { method: selectedId ? "PUT" : "POST", body: payload }
      );
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

  return (
    <div className="stack notification-definitions-view">
      <SectionToolbar
        title="Notification definitions"
        description="Manage system-event visibility, delivery channel, route, severity, and localized user-facing copy."
        state={state}
        onReload={() => void load()}
      >
        <button className="primary-button" type="button" onClick={startNewDefinition}>New definition</button>
      </SectionToolbar>

      <div className="metric-grid notification-definition-metrics">
        <MetricCard label="Definitions" value={String(definitions.length)} hint="Registered notification types" />
        <MetricCard label="Enabled" value={String(enabledCount)} hint="Visible in at least one channel" />
        <MetricCard label="Push eligible" value={String(pushCount)} hint="Push or combined channel" />
        <MetricCard label="Protected" value={String(protectedCount)} hint="Safety-critical definitions" />
      </div>

      <div className="form-notice warning notification-definition-boundary">
        <strong>Definitions do not create event triggers.</strong> Existing backend events use matching keys automatically. Use Campaigns for manual broadcasts; a brand-new automatic event still requires a backend producer.
      </div>
      <div className="notification-definition-workflow" aria-label="Notification editing steps">
        <span><strong>1</strong> Select a notification type from the list</span>
        <span><strong>2</strong> Edit its policy and EN/TR copy</span>
        <span><strong>3</strong> Select Save changes</span>
      </div>

      <div className="notification-definition-layout">
        <Panel
          title="Registered types"
          description="Select a type to edit its global policy and copy."
          actions={<input aria-label="Filter notification definitions" placeholder="Filter by name or key" value={filter} onChange={(event) => setFilter(event.target.value)} />}
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
                  <span className={`badge ${definition.enabled ? "good" : "neutral"}`}>{definition.enabled ? "Enabled" : "Disabled"}</span>
                </span>
                <code>{definition.key}</code>
                <span className="notification-definition-card-meta">
                  <span>{definition.channel.replaceAll("_", " + ")}</span>
                  {definition.protectedDefinition && <span className="badge warn">Protected</span>}
                  <span className="notification-definition-edit-label">Edit →</span>
                </span>
              </button>
            ))}
            {state !== "loading" && !visibleDefinitions.length && <p className="notification-definition-empty">No definitions match this filter.</p>}
          </div>
        </Panel>

        <Panel
          title={selectedId ? "Edit definition" : "New definition"}
          description={selectedId ? "The event key is immutable after creation." : "Use the exact lowercase key emitted by the backend event."}
          className="notification-definition-editor"
        >
          <form ref={editorRef} onSubmit={(event) => void save(event)}>
            {notice && <div className={`form-notice ${actionState === "error" ? "warning" : ""}`} role="status">{notice}</div>}
            {selected?.protectedDefinition && <div className="form-notice warning">This safety-critical definition cannot be disabled. Its copy and route can still be corrected.</div>}
            <div className="campaign-form-grid notification-definition-form-grid">
              <label className="span-2">Display name<input ref={displayNameRef} required maxLength={120} value={draft.displayName} onChange={(event) => update("displayName", event.target.value)} /></label>
              <label className="span-2">Event key<input required disabled={Boolean(selectedId)} maxLength={80} pattern="[a-z0-9_]+" value={draft.key} onChange={(event) => update("key", event.target.value.replace(/[^a-zA-Z0-9_]/g, "").toLocaleLowerCase())} /></label>
              <label className="span-4">Operational description<input maxLength={500} value={draft.description} onChange={(event) => update("description", event.target.value)} /></label>
              <label>Channel<select value={draft.channel} onChange={(event) => update("channel", event.target.value as NotificationDefinitionDraft["channel"])}><option value="IN_APP">In-app</option><option value="PUSH">Push</option><option value="IN_APP_AND_PUSH">In-app + push</option></select></label>
              <label>Severity<select value={draft.severity} onChange={(event) => update("severity", event.target.value as NotificationDefinitionDraft["severity"])}><option value="">Keep event default</option><option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option></select></label>
              <label className="span-2">Target route<input maxLength={255} placeholder="/notifications or app route" value={draft.targetRoute} onChange={(event) => update("targetRoute", event.target.value)} /></label>
              <label className="notification-definition-toggle span-4"><input checked={draft.enabled} disabled={Boolean(selected?.protectedDefinition)} type="checkbox" onChange={(event) => update("enabled", event.target.checked)} /><span><strong>Definition enabled</strong><small>Disabling suppresses both in-app visibility and push delivery for this event type.</small></span></label>
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
            <p className="notification-definition-template-help">Allowed placeholders: <code>{"{originalTitle}"}</code>, <code>{"{originalMessage}"}</code>, <code>{"{note}"}</code>. Empty localized fields preserve the producer's current copy.</p>
            <div className="inline-actions notification-definition-actions">
              {selectedId && <button className="ghost-button" type="button" onClick={reset}>Cancel editing</button>}
              <button className="primary-button" disabled={actionState === "loading"} type="submit">{actionState === "loading" ? "Saving..." : selectedId ? "Save changes" : "Create definition"}</button>
            </div>
          </form>
        </Panel>
      </div>
    </div>
  );
}
