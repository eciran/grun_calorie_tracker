import { FormEvent, useState } from "react";
import { PageResponse, formatRequestError, request } from "../api";

import { AdminMailMonitoring, AdminMailboxAccount, Notification, SystemHealth } from "../types";

import { EmptyState, MetricCard, Panel, SectionToolbar } from "../AdminPrimitives";
import { useAdminLocale } from "../admin/locale";

import { AdminTargetContext, Badge, MiniNotificationList, combineStates, formatValue, percent, readNumber, useEndpoint } from "./../admin/shared";

export function MailOpsView({ onError, targetContext, onClearTarget }: { onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const { data: health, state: healthState, reload: reloadHealth } = useEndpoint<SystemHealth>("/api/v1/admin/system/health", onError);
  const { data: notifications, state: notificationState, reload: reloadNotifications } = useEndpoint<PageResponse<Notification>>("/api/v1/notifications?page=0&size=25&type=system", onError);
  const { data: mailMonitoring, state: mailState, reload: reloadMail } = useEndpoint<AdminMailMonitoring>("/api/v1/admin/mail/monitoring?days=7&limit=10", onError);
  const rows = notifications?.content ?? [];
  const mailRows = rows.filter((item) => {
    const text = `${item.type ?? ""} ${item.message ?? ""}`.toLowerCase();
    return text.includes("mail") || text.includes("email") || text.includes("brevo") || text.includes("verification") || text.includes("reset");
  });
  const alertCount = readNumber(health, "systemAlertsLast24h") ?? 0;
  const counters = Object.entries(mailMonitoring?.counters ?? {}).sort(([left], [right]) => left.localeCompare(right));
  const providerReady = mailMonitoring?.provider === "BREVO" && Boolean(mailMonitoring?.apiKeyConfigured);
  const providerHealthy = providerReady && Boolean(mailMonitoring?.providerReachable);
  const statusLabel = providerHealthy ? tx("Brevo reachable", "Brevo erişilebilir") : providerReady ? tx("Check provider", "Sağlayıcıyı kontrol edin") : tx("Setup required", "Kurulum gerekli");
  const statusTone = providerHealthy ? "good" : providerReady || alertCount > 0 ? "warn" : "neutral";
  const requests = readCounter(mailMonitoring, "requests");
  const delivered = readCounter(mailMonitoring, "delivered");
  const opened = readCounter(mailMonitoring, "opened");
  const clicked = readCounter(mailMonitoring, "clicked");
  const hardBounces = readCounter(mailMonitoring, "hardBounces");
  const softBounces = readCounter(mailMonitoring, "softBounces");
  const blocked = readCounter(mailMonitoring, "blocked");
  const spamReports = readCounter(mailMonitoring, "spamReports");
  const unsubscribed = readCounter(mailMonitoring, "unsubscribed");
  const failed = hardBounces + softBounces + blocked;
  const funnelItems = [
    { label: tx("Requests", "İstekler"), value: requests, percent: 100 },
    { label: tx("Delivered", "Teslim edildi"), value: delivered, percent: percent(delivered, requests) },
    { label: tx("Opened", "Açıldı"), value: opened, percent: percent(opened, requests) },
    { label: tx("Clicked", "Tıklandı"), value: clicked, percent: percent(clicked, requests) }
  ];
  const issueItems = [
    { label: tx("Hard bounce", "Kalıcı geri dönüş"), value: hardBounces },
    { label: tx("Soft bounce", "Geçici geri dönüş"), value: softBounces },
    { label: tx("Blocked", "Engellendi"), value: blocked },
    { label: tx("Spam", "Spam"), value: spamReports },
    { label: tx("Unsubscribed", "Abonelikten çıktı"), value: unsubscribed }
  ];

  function reloadAll() {
    void reloadHealth();
    void reloadNotifications();
    void reloadMail();
  }


  return (
    <div className="stack communications-ops-view mail-ops-page">
      <SectionToolbar title={tx("Mail operations", "E-posta işlemleri")} state={combineStates([healthState, notificationState, mailState])} onReload={reloadAll} />
      <div className="mail-hero">
        <div>
          <p className="eyebrow">{tx("Transactional delivery", "İşlemsel teslimat")}</p>
          <h2>{tx("Mail state is monitored without exposing provider secrets.", "E-posta durumu sağlayıcı gizli bilgileri gösterilmeden izlenir.")}</h2>
          <p>{tx("Verification and reset flows stay on backend config. Admin panel only shows delivery policy, alerts, and recent related notifications.", "Doğrulama ve sıfırlama akışları backend yapılandırmasında kalır. Yönetim paneli yalnızca teslimat politikasını, uyarıları ve ilgili son bildirimleri gösterir.")}</p>
        </div>
        <div className="mail-hero-status">
          <Badge value={statusLabel} tone={statusTone} />
          <span>{mailMonitoring?.statusMessage ?? tx("Backend monitoring endpoint is active.", "Backend izleme endpoint'i aktif.")}</span>
        </div>
      </div>

      <Panel title={tx("Monitoring overview", "İzleme özeti")}>
        <div className="mail-monitor-grid">
          <MetricCard label={tx("Provider", "Sağlayıcı")} value={formatValue(mailMonitoring?.provider)} hint={tx("Configured mail provider", "Yapılandırılmış e-posta sağlayıcısı")} />
          <MetricCard label={tx("Reachability", "Erişilebilirlik")} value={mailMonitoring?.providerReachable ? tx("Online", "Çevrimiçi") : tx("Offline", "Çevrimdışı")} hint={formatValue(mailMonitoring?.providerBaseUrl)} />
          <MetricCard label={tx("Delivered", "Teslim edildi")} value={formatValue(delivered)} hint={`${percent(delivered, requests)}% ${tx("of requests", "istek oranı")}`} />
          <MetricCard label={tx("Failed", "Başarısız")} value={formatValue(failed)} hint={`${percent(failed, requests)}% ${tx("of requests", "istek oranı")}`} />
        </div>
        <div className="mail-chart-grid">
          <MailFunnelChart items={funnelItems} />
          <MailIssueChart items={issueItems} total={requests} />
        </div>
        {!counters.length && <EmptyState message={tx("No Brevo counters returned.", "Brevo sayacı bulunamadı.")} />}
      </Panel>

      <Panel title={tx("Delivery policy", "Teslimat politikası")}>
        <div className="mail-policy-grid">
          <PolicyStep step="1" title={tx("First resend", "İlk yeniden gönderim")} value={tx("30 seconds", "30 saniye")} />
          <PolicyStep step="2" title={tx("Second resend", "İkinci yeniden gönderim")} value={tx("2 minutes", "2 dakika")} />
          <PolicyStep step="3" title={tx("Further resend", "Sonraki yeniden gönderim")} value={tx("5 minutes", "5 dakika")} />
          <PolicyStep step="4" title={tx("Token rule", "Token kuralı")} value={tx("Newest token active", "En yeni token aktif")} />
        </div>
      </Panel>

      <div className="mail-diagnostics-grid">
        <Panel title={tx("Mail related system notifications", "E-postayla ilişkili sistem bildirimleri")}>
          <MiniNotificationList notifications={mailRows} />
        </Panel>
      </div>
    </div>
  );
}

const emptyMailbox = { emailAddress:"", displayName:"", username:"", password:"", imapHost:"ni-kyrenia.guzelhosting.com", imapPort:993, imapSsl:true, smtpHost:"ni-kyrenia.guzelhosting.com", smtpPort:465, smtpSsl:true, enabled:false };

export function MailboxAccounts({ accounts, reload, onError }: { accounts: AdminMailboxAccount[]; reload: () => Promise<unknown>; onError: (message: string | null) => void }) {
  const { locale }=useAdminLocale(); const tx=(en:string,tr:string)=>locale==="tr"?tr:en;
  const [editing,setEditing]=useState<AdminMailboxAccount|null|"new">(null);
  const [form,setForm]=useState(emptyMailbox);
  const [busy,setBusy]=useState(false);
  const [notice,setNotice]=useState<string|null>(null);
  function open(account?:AdminMailboxAccount){setNotice(null);setEditing(account??"new");setForm(account?{emailAddress:account.emailAddress,displayName:account.displayName??"",username:account.username,password:"",imapHost:account.imapHost,imapPort:account.imapPort,imapSsl:account.imapSsl,smtpHost:account.smtpHost,smtpPort:account.smtpPort,smtpSsl:account.smtpSsl,enabled:account.enabled}:{...emptyMailbox});}
  async function save(event:FormEvent){event.preventDefault();setBusy(true);setNotice(null);try{const id=editing!=="new"&&editing?editing.id:null;await request(`/api/v1/admin/mail/mailboxes${id?`/${id}`:""}`,{method:id?"PUT":"POST",body:form});await reload();setEditing(null);}catch(error){setNotice(formatRequestError(error));}finally{setBusy(false);}}
  async function test(account:AdminMailboxAccount){setBusy(true);setNotice(null);try{const result=await request<AdminMailboxAccount>(`/api/v1/admin/mail/mailboxes/${account.id}/test`,{method:"POST"});setNotice(result.connectionStatus==="CONNECTED"?tx(`${account.emailAddress} connected successfully.`,`${account.emailAddress} bağlantısı başarılı.`):result.connectionError??tx("Connection failed.","Bağlantı başarısız."));await reload();}catch(error){onError(formatRequestError(error));}finally{setBusy(false);}}
  const connected=accounts.filter(item=>item.connectionStatus==="CONNECTED").length;
  return <Panel className="mailbox-account-panel" title={tx("Connected inboxes","Bağlı gelen kutuları")} description={tx("Manage cPanel IMAP and SMTP accounts without rebuilding the application.","cPanel IMAP ve SMTP hesaplarını uygulamayı yeniden build etmeden yönetin.")}>
    <div className="mailbox-account-toolbar"><div><strong>{accounts.length}</strong><span>{tx("mailboxes", "posta kutusu")}</span><small>{connected} {tx("connected","bağlı")}</small></div><button className="primary-button" type="button" onClick={()=>open()}>{tx("Add mailbox","Posta kutusu ekle")}</button></div>
    {notice&&<div className="form-notice" role="status">{notice}</div>}
    <div className="mailbox-account-grid">{accounts.map(account=><article key={account.id} className="mailbox-account-card"><header><div><span>{account.displayName||tx("Mailbox","Posta kutusu")}</span><strong>{account.emailAddress}</strong></div><Badge value={account.connectionStatus} tone={account.connectionStatus==="CONNECTED"?"good":account.connectionStatus==="FAILED"?"warn":"neutral"}/></header><dl><div><dt>IMAP</dt><dd>{account.imapHost}:{account.imapPort}</dd></div><div><dt>SMTP</dt><dd>{account.smtpHost}:{account.smtpPort}</dd></div><div><dt>{tx("Password","Parola")}</dt><dd>{account.passwordConfigured?tx("Configured","Tanımlı"):tx("Required","Gerekli")}</dd></div></dl>{account.connectionError&&<small className="mailbox-account-error">{account.connectionError}</small>}<footer><button className="ghost-button" type="button" onClick={()=>open(account)}>{tx("Configure","Yapılandır")}</button><button className="ghost-button" disabled={busy||!account.passwordConfigured} type="button" onClick={()=>void test(account)}>{tx("Test connection","Bağlantıyı test et")}</button></footer></article>)}</div>
    {!accounts.length&&<EmptyState message={tx("No mailbox configured.","Yapılandırılmış posta kutusu yok.")}/>}
    {editing&&<div className="modal-backdrop" role="presentation" onClick={()=>!busy&&setEditing(null)}><form className="modal-card mailbox-account-modal" role="dialog" aria-modal="true" onSubmit={save} onClick={event=>event.stopPropagation()}><header className="modal-header"><div><span>{tx("SECURE MAIL CONNECTION","GÜVENLİ E-POSTA BAĞLANTISI")}</span><h2>{editing==="new"?tx("Add mailbox","Posta kutusu ekle"):tx("Configure mailbox","Posta kutusunu yapılandır")}</h2><p>{tx("Credentials are encrypted and are never returned to the browser.","Kimlik bilgileri şifrelenir ve tarayıcıya geri gönderilmez.")}</p></div><button className="icon-button" type="button" onClick={()=>setEditing(null)}>×</button></header><div className="modal-body mailbox-account-form"><label>{tx("Email address","E-posta adresi")}<input required type="email" value={form.emailAddress} onChange={event=>setForm({...form,emailAddress:event.target.value,username:form.username||event.target.value})}/></label><label>{tx("Display name","Görünen ad")}<input value={form.displayName} onChange={event=>setForm({...form,displayName:event.target.value})}/></label><label>{tx("Username","Kullanıcı adı")}<input required value={form.username} onChange={event=>setForm({...form,username:event.target.value})}/></label><label>{tx("Mailbox password","Posta kutusu parolası")}<input type="password" autoComplete="new-password" placeholder={editing==="new"?tx("Required to enable","Etkinleştirmek için gerekli"):tx("Leave blank to keep current","Mevcut parolayı korumak için boş bırakın")} value={form.password} onChange={event=>setForm({...form,password:event.target.value})}/></label><fieldset><legend>{tx("Incoming mail · IMAP","Gelen posta · IMAP")}</legend><label>{tx("Server","Sunucu")}<input required value={form.imapHost} onChange={event=>setForm({...form,imapHost:event.target.value})}/></label><label>{tx("Port","Port")}<input required min="1" max="65535" type="number" value={form.imapPort} onChange={event=>setForm({...form,imapPort:Number(event.target.value)})}/></label><label className="inline-check"><input type="checkbox" checked={form.imapSsl} onChange={event=>setForm({...form,imapSsl:event.target.checked})}/>{tx("SSL/TLS","SSL/TLS")}</label></fieldset><fieldset><legend>{tx("Outgoing mail · SMTP","Giden posta · SMTP")}</legend><label>{tx("Server","Sunucu")}<input required value={form.smtpHost} onChange={event=>setForm({...form,smtpHost:event.target.value})}/></label><label>{tx("Port","Port")}<input required min="1" max="65535" type="number" value={form.smtpPort} onChange={event=>setForm({...form,smtpPort:Number(event.target.value)})}/></label><label className="inline-check"><input type="checkbox" checked={form.smtpSsl} onChange={event=>setForm({...form,smtpSsl:event.target.checked})}/>{tx("SSL/TLS","SSL/TLS")}</label></fieldset><label className="mailbox-enable-check"><input type="checkbox" checked={form.enabled} onChange={event=>setForm({...form,enabled:event.target.checked})}/><span><strong>{tx("Enable mailbox","Posta kutusunu etkinleştir")}</strong><small>{tx("Enable after the connection test succeeds.","Bağlantı testi başarılı olduktan sonra etkinleştirin.")}</small></span></label>{notice&&<div className="form-error" role="alert">{notice}</div>}</div><footer className="modal-actions"><button className="ghost-button" disabled={busy} type="button" onClick={()=>setEditing(null)}>{tx("Cancel","İptal")}</button><button className="primary-button" disabled={busy} type="submit">{busy?tx("Saving…","Kaydediliyor…"):tx("Save mailbox","Posta kutusunu kaydet")}</button></footer></form></div>}
  </Panel>;
}

export function MailFunnelChart({ items }: { items: Array<{ label: string; value: number; percent: number }> }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  return (
    <div className="mail-chart-card">
      <div className="mail-chart-heading">
        <strong>{tx("Delivery funnel", "Teslimat hunisi")}</strong>
        <span>{tx("Last 7 days", "Son 7 gün")}</span>
      </div>
      <div className="mail-funnel">
        {items.map((item) => (
          <div key={item.label}>
            <div>
              <span>{item.label}</span>
              <strong>{formatValue(item.value)}</strong>
            </div>
            <div className="mail-bar">
              <i style={{ width: `${item.percent}%` }} />
            </div>
            <small>{item.percent}%</small>
          </div>
        ))}
      </div>
    </div>
  );
}

export function MailIssueChart({ items, total }: { items: Array<{ label: string; value: number }>; total: number }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const issueTotal = items.reduce((sum, item) => sum + item.value, 0);
  return (
    <div className="mail-chart-card danger">
      <div className="mail-chart-heading">
        <strong>{tx("Issue breakdown", "Sorun dağılımı")}</strong>
        <span>{formatValue(issueTotal)} {tx("issue events", "sorun olayı")}</span>
      </div>
      <div className="mail-issues">
        {items.map((item) => (
          <div key={item.label}>
            <span>{item.label}</span>
            <div className="mail-bar">
              <i style={{ width: `${percent(item.value, Math.max(total, issueTotal))}%` }} />
            </div>
            <strong>{formatValue(item.value)}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}

export function PolicyStep({ step, title, value }: { step: string; title: string; value: string }) {
  return (
    <article className="policy-step">
      <span>{step}</span>
      <div>
        <strong>{title}</strong>
        <small>{value}</small>
      </div>
    </article>
  );
}

export function readCounter(data: AdminMailMonitoring | null, key: string): number {
  const value = data?.counters?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
