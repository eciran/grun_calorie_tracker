import { FormEvent, useState } from "react";

import { formatRequestError, request } from "../api";

import { AdminBrevoSender, AdminBrevoSenderList } from "../types";

import { EmptyState, LoadState, Panel, SectionToolbar } from "../AdminPrimitives";
import { CommunicationBars } from "../CommunicationsPrimitives";
import { useAdminLocale } from "../admin/locale";

import { Badge, combineStates, formatValue, useEndpoint } from "./../admin/shared";

export function BrevoSendersView({ onError, embedded = false }: { onError: (message: string | null) => void; embedded?: boolean }) {
  const [selected, setSelected] = useState<AdminBrevoSender | null>(null);
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({ name: "", email: "" });
  const [editForm, setEditForm] = useState({ name: "", email: "" });
  const [saveState, setSaveState] = useState<LoadState>("idle");
  const [notice, setNotice] = useState<string | null>(null);
  const { data, state, reload } = useEndpoint<AdminBrevoSenderList>("/api/v1/admin/mail/brevo/senders", onError);
  const rows = data?.senders ?? [];
  const readyCount = rows.filter((sender) => sender.active !== false && !sender.dkimError && !sender.spfError).length;
  const dnsAttentionCount = rows.filter((sender) => Boolean(sender.dkimError || sender.spfError)).length;
  const inactiveCount = rows.filter((sender) => sender.active === false).length;

  function startNewSender() {
    setSelected(null);
    setCreateForm({ name: "", email: "" });
    setNotice(null);
    setCreateOpen(true);
  }

  function editSender(sender: AdminBrevoSender) {
    setSelected(sender);
    setEditForm({ name: sender.name ?? "", email: sender.email ?? "" });
    setNotice(null);
  }

  async function createSender(event: FormEvent) {
    event.preventDefault();
    setSaveState("loading");
    setNotice(null);
    try {
      await request<AdminBrevoSender>("/api/v1/admin/mail/brevo/senders", {
        method: "POST",
        body: createForm
      });
      setCreateForm({ name: "", email: "" });
      setCreateOpen(false);
      setNotice(tx("Brevo sender created. Verification may be required in Brevo.", "Brevo göndericisi oluşturuldu. Brevo doğrulaması gerekebilir."));
      setSaveState("ready");
      void reload();
    } catch (error) {
      setSaveState("error");
      onError(formatRequestError(error));
    }
  }

  async function updateSender(event: FormEvent) {
    event.preventDefault();
    if (!selected?.id) return;
    setSaveState("loading");
    setNotice(null);
    try {
      await request<AdminBrevoSender>(`/api/v1/admin/mail/brevo/senders/${selected.id}`, {
        method: "PUT",
        body: editForm
      });
      setNotice(tx("Brevo sender updated.", "Brevo göndericisi güncellendi."));
      setSelected(null);
      setSaveState("ready");
      void reload();
    } catch (error) {
      setSaveState("error");
      onError(formatRequestError(error));
    }
  }


  return (
    <div className="stack communications-ops-view brevo-senders-page">
      {!embedded && <SectionToolbar title={tx("Brevo sender management", "Brevo gönderici yönetimi")} state={combineStates([state, saveState])} onReload={reload}>
        <button className="ghost-button" type="button" onClick={startNewSender}>{tx("New sender", "Yeni gönderici")}</button>
      </SectionToolbar>}
      {embedded && <div className="delivery-center-action-row"><button className="primary-button" type="button" onClick={startNewSender}>{tx("New sender", "Yeni gönderici")}</button><button className="ghost-button" type="button" onClick={reload}>{tx("Refresh", "Yenile")}</button></div>}
      {!embedded && <div className="brevo-sender-hero">
        <div>
          <p className="eyebrow">{tx("Sender identities", "Gönderici kimlikleri")}</p>
          <h2>{tx("Manage sender names and from addresses without exposing provider secrets.", "Sağlayıcı gizli bilgilerini açığa çıkarmadan gönderici adlarını ve adreslerini yönetin.")}</h2>
          <p>{tx("Sender removal stays inside Brevo.", "Gönderici kaldırma işlemi Brevo içinde yapılır.")}</p>
        </div>
        <Badge value={data?.providerReachable ? tx("Brevo reachable", "Brevo erişilebilir") : tx("Safe proxy", "Güvenli proxy")} tone={data?.providerReachable ? "good" : "neutral"} />
      </div>}
      <section className="communication-insight-strip"><div><p className="eyebrow">{tx("Sender readiness", "Gönderici hazırlığı")}</p><h3>{tx("Identity verification status", "Kimlik doğrulama durumu")}</h3><p>{tx("DNS attention and inactive identities stay separate from send-ready addresses.", "DNS uyarıları ve etkin olmayan kimlikler gönderime hazır adreslerden ayrı gösterilir.")}</p></div><CommunicationBars items={[{ label: tx("Ready", "Hazır"), value: readyCount, tone: "accent" }, { label: tx("DNS attention", "DNS kontrolü"), value: dnsAttentionCount, tone: "danger" }, { label: tx("Inactive", "Etkin değil"), value: inactiveCount }]} empty={tx("No sender identities returned.", "Gönderici kimliği bulunamadı.")} /></section>

      <Panel title={tx("Senders", "Göndericiler")}>
        {data?.statusMessage && <p className="form-note">{data.statusMessage}</p>}
        <div className="brevo-sender-list">
          <div className="brevo-sender-row head">
            <span>{tx("Name", "Ad")}</span>
            <span>{tx("Email", "E-posta")}</span>
            <span>{tx("Verification", "Doğrulama")}</span>
            <span>IPs</span>
          </div>
          {rows.map((sender) => (
            <button className="brevo-sender-row" key={sender.id ?? sender.email} type="button" onClick={() => editSender(sender)}>
              <strong>{formatValue(sender.name)}</strong>
              <span className="sender-email">{formatValue(sender.email)}</span>
              <span>{sender.dkimError || sender.spfError ? tx("DNS attention", "DNS kontrolü") : sender.active === false ? tx("Inactive", "Etkin değil") : tx("Looks ready", "Hazır görünüyor")}</span>
              <small>{formatValue(sender.ips?.length ?? 0)}</small>
            </button>
          ))}
          {!rows.length && <EmptyState message={tx("No Brevo sender returned.", "Brevo göndericisi bulunamadı.")} />}
        </div>
      </Panel>

      {createOpen && (
        <div className="modal-backdrop" role="presentation" onClick={() => setCreateOpen(false)}>
          <section className="modal-card compact communication-editor-modal" role="dialog" aria-modal="true" aria-labelledby="create-sender-title" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div><p className="eyebrow">{tx("Sender identity", "Gönderici kimliği")}</p><h2 id="create-sender-title">{tx("Create sender", "Gönderici oluştur")}</h2><span>{tx("Add a verified from identity without exposing provider credentials.", "Sağlayıcı bilgilerini göstermeden doğrulanabilir bir gönderici ekleyin.")}</span></div>
              <button className="icon-button" type="button" aria-label={tx("Close", "Kapat")} onClick={() => setCreateOpen(false)}>×</button>
            </header>
            {notice && <div className="form-notice">{notice}</div>}
            <form className="brevo-sender-form" onSubmit={createSender}>
          <label>
            {tx("Sender name", "Gönderici adı")}
            <input value={createForm.name} onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })} placeholder="GRUN Support" required />
          </label>
          <label>
            {tx("Sender email", "Gönderici e-postası")}
            <input value={createForm.email} onChange={(event) => setCreateForm({ ...createForm, email: event.target.value })} placeholder="support@grun.app" required />
          </label>
          <button className="primary-button" type="submit" disabled={saveState === "loading"}>{tx("Create sender", "Gönderici oluştur")}</button>
            </form>
          </section>
        </div>
      )}

      {selected && (
        <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
          <div className="modal-card compact" role="dialog" aria-modal="true" aria-label={tx("Update Brevo sender", "Brevo göndericisini güncelle")} onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div>
                <span>{tx("Brevo sender", "Brevo göndericisi")}</span>
                <h2>{tx("Update sender", "Göndericiyi güncelle")}</h2>
              </div>
              <button className="icon-button" type="button" onClick={() => setSelected(null)}>x</button>
            </header>
            <form className="sender-update-form" onSubmit={updateSender}>
              {notice && <div className="form-notice">{notice}</div>}
              <div className="sender-update-summary">
                <div>
                  <span>{tx("Sender ID", "Gönderici kimliği")}</span>
                  <strong>{formatValue(selected.id)}</strong>
                </div>
                <div>
                  <span>{tx("Status", "Durum")}</span>
                  <strong>{selected.dkimError || selected.spfError ? tx("DNS attention", "DNS kontrolü") : selected.active === false ? tx("Inactive", "Etkin değil") : tx("Looks ready", "Hazır görünüyor")}</strong>
                </div>
              </div>
              <div className="sender-update-fields">
                <label>
                  {tx("Sender name", "Gönderici adı")}
                <input value={editForm.name} onChange={(event) => setEditForm({ ...editForm, name: event.target.value })} placeholder="GRUN Support" required />
                </label>
                <label>
                  {tx("Sender email", "Gönderici e-postası")}
                <input value={editForm.email} onChange={(event) => setEditForm({ ...editForm, email: event.target.value })} placeholder="support@grun.app" required />
                </label>
              </div>
              <p className="form-note">{tx("Changing sender email may require verification in Brevo.", "Gönderici e-postasını değiştirmek Brevo doğrulaması gerektirebilir.")}</p>
              <div className="modal-actions">
                <button className="ghost-button" type="button" onClick={() => setSelected(null)}>{tx("Cancel", "İptal")}</button>
                <button className="primary-button" type="submit" disabled={saveState === "loading"}>{tx("Update sender", "Göndericiyi güncelle")}</button>
              </div>
          </form>
          </div>
        </div>
      )}
    </div>
  );
}
