import { useEffect, useMemo, useState } from "react";
import { formatRequestError, request } from "../api";
import { EmptyState, SectionToolbar } from "../AdminPrimitives";
import { useAdminLocale } from "../admin/locale";
import { AdminMailboxAccount, AdminMailboxMessage, AdminMailboxMessageDetail } from "../types";
import { Badge, useEndpoint } from "../admin/shared";
import { MailboxAccounts } from "./MailOpsView";

const folders = ["INBOX", "Sent", "Drafts", "Spam", "Trash"];

export function MailInboxView({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale(); const tx = (en:string,tr:string) => locale === "tr" ? tr : en;
  const { data: accounts, state, reload } = useEndpoint<AdminMailboxAccount[]>("/api/v1/admin/mail/mailboxes", onError);
  const ready = useMemo(() => (accounts ?? []).filter(item => item.passwordConfigured), [accounts]);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [folder, setFolder] = useState("INBOX");
  const [messages, setMessages] = useState<AdminMailboxMessage[]>([]);
  const [selected, setSelected] = useState<AdminMailboxMessageDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<"inbox"|"accounts">("inbox");
  useEffect(() => { if (!accountId && ready.length) setAccountId(ready[0].id); }, [ready, accountId]);
  useEffect(() => { if (accountId) void loadMessages(accountId, folder); else { setMessages([]); setSelected(null); } }, [accountId, folder]);
  async function loadMessages(id=accountId, selectedFolder=folder){if(!id)return;setLoading(true);onError(null);try{setMessages(await request<AdminMailboxMessage[]>(`/api/v1/admin/mail/mailboxes/${id}/messages?folder=${encodeURIComponent(selectedFolder)}&limit=50`));setSelected(null);}catch(error){onError(formatRequestError(error));setMessages([]);}finally{setLoading(false);}}
  async function openMessage(item:AdminMailboxMessage){setLoading(true);try{setSelected(await request<AdminMailboxMessageDetail>(`/api/v1/admin/mail/mailboxes/${item.mailboxId}/messages/${item.uid}?folder=${encodeURIComponent(item.folder)}`));}catch(error){onError(formatRequestError(error));}finally{setLoading(false);}}
  const date=(value?:string)=>value?new Intl.DateTimeFormat(locale==="tr"?"tr-TR":"en-IE",{dateStyle:"medium",timeStyle:"short"}).format(new Date(value)):"—";
  return <div className="stack mail-inbox-page">
    <SectionToolbar title={tx("Mail center","E-posta merkezi")} state={state} onReload={()=>{void reload();if(accountId)void loadMessages();}} />
    <section className="mail-inbox-hero"><div><p className="eyebrow">{tx("CPANEL MAIL WORKSPACE","CPANEL POSTA ÇALIŞMA ALANI")}</p><h2>{tx("Read and manage every connected inbox in one place.","Bağlı tüm posta kutularını tek yerden okuyun ve yönetin.")}</h2><p>{tx("Mailbox credentials stay encrypted on the server; message bodies are displayed as safe plain text.","Posta kutusu bilgileri sunucuda şifreli kalır; mesaj içerikleri güvenli düz metin olarak gösterilir.")}</p></div><div className="mail-inbox-tabs"><button className={tab==="inbox"?"active":""} onClick={()=>setTab("inbox")} type="button">{tx("Messages","Mesajlar")}</button><button className={tab==="accounts"?"active":""} onClick={()=>setTab("accounts")} type="button">{tx("Mailboxes","Posta kutuları")}</button></div></section>
    {tab==="accounts" ? <MailboxAccounts accounts={accounts??[]} reload={reload} onError={onError}/> : <section className="mail-reader-shell">
      <header className="mail-reader-controls"><label>{tx("Mailbox","Posta kutusu")}<select value={accountId??""} onChange={e=>setAccountId(Number(e.target.value)||null)}><option value="">{tx("Select a configured mailbox","Yapılandırılmış posta kutusu seçin")}</option>{ready.map(item=><option key={item.id} value={item.id}>{item.displayName||item.emailAddress} · {item.emailAddress}</option>)}</select></label><div className="mail-folder-tabs">{folders.map(item=><button type="button" className={folder===item?"active":""} key={item} onClick={()=>setFolder(item)}>{item==="INBOX"?tx("Inbox","Gelen"):item}</button>)}</div><button className="ghost-button" disabled={!accountId||loading} onClick={()=>void loadMessages()} type="button">{tx("Refresh","Yenile")}</button></header>
      {!ready.length ? <div className="mail-reader-empty"><EmptyState message={tx("Configure a mailbox password to start reading messages.","Mesajları okumak için bir posta kutusu parolası yapılandırın.")}/><button className="primary-button" onClick={()=>setTab("accounts")} type="button">{tx("Configure mailboxes","Posta kutularını yapılandır")}</button></div> : <div className="mail-reader-grid"><div className="mail-message-list" aria-busy={loading}>{messages.map(item=><button type="button" key={item.uid} className={`${selected?.uid===item.uid?"active ":""}${item.seen?"":"unread"}`} onClick={()=>void openMessage(item)}><span className="mail-message-row"><strong>{item.sender}</strong><time>{date(item.receivedAt)}</time></span><b>{item.subject}</b><small>{item.preview||tx("No preview available","Önizleme yok")}</small>{item.hasAttachments&&<span className="mail-attachment-chip">{tx("Attachment","Ek")}</span>}</button>)}{!loading&&!messages.length&&<EmptyState message={tx("There are no messages in this folder.","Bu klasörde mesaj yok.")}/>}</div><article className="mail-message-detail">{selected?<><header><div><Badge value={selected.folder}/><h2>{selected.subject}</h2><p>{selected.sender}</p><small>{tx("To","Alıcı")}: {selected.recipients.join(", ")||"—"} · {date(selected.receivedAt)}</small></div></header>{selected.attachments.length>0&&<div className="mail-attachments">{selected.attachments.map(name=><span key={name}>{name}</span>)}</div>}<pre>{selected.bodyText||tx("This message has no readable text body.","Bu mesajın okunabilir metin içeriği yok.")}</pre></>:<EmptyState message={tx("Select a message to read it.","Okumak için bir mesaj seçin.")}/>}</article></div>}
    </section>}
  </div>;
}
