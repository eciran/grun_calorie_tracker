import { useEffect, useMemo, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminAccessProfile, Notification } from "../types";

import { DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { SectionKey, isSectionKey } from "./../admin/navigation";

import { AdminTargetContext, Badge, combineStates, formatDate, formatValue, notificationSeverityTone, notificationTargetLabel, shortFeature, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";
import { InboxPriorityChart, InboxQueueChart } from "../InboxCharts";

type WorkItem = { key: string; title: string; detail: string; priority: "CRITICAL"|"HIGH"|"NORMAL"; status: string; createdAt?: string; route: SectionKey; next: string; category: string; targetType: string; targetId: string };
type QueuePage = { content?: Array<Record<string, unknown>>; totalElements?: number };

export function NotificationsView({ accessProfile, onError, onNavigate }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void; onNavigate: (section: SectionKey, context?: Omit<AdminTargetContext, "section">) => void }) {
  const { locale } = useAdminLocale();
  const [typeFilter, setTypeFilter] = useState("");
  const [severityFilter, setSeverityFilter] = useState("");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [actionNotice, setActionNotice] = useState<string | null>(null);
  const [queueItems,setQueueItems]=useState<WorkItem[]>([]); const [queueState,setQueueState]=useState<LoadState>("idle"); const [queueReload,setQueueReload]=useState(0);
  const path = buildNotificationPath({ type: typeFilter, severity: severityFilter, unreadOnly, page, size: pageSize });
  const { data, state, reload } = useEndpoint<PageResponse<Notification>>(path, onError);
  const rows = data?.content ?? [];
  const unread = rows.filter((item) => !item.read).length;
  const permissions = useMemo(() => new Set(accessProfile?.permissions ?? []), [accessProfile]);
  const owner=accessProfile?.role==="OWNER";
  useEffect(()=>{let cancelled=false; const sources:Array<Promise<WorkItem[]>>=[];
    if(permissions.has("CATALOG_READ")) sources.push(request<QueuePage>("/api/v1/admin/products/contributions?status=PENDING_REVIEW&page=0&size=10").then(page=>(page.content??[]).map(item=>({key:`catalog-${item.id}`,title:locale==="tr"?"Ürün etiketi incelemesi":"Product label review",detail:String(item.productName??item.barcode??`#${item.id}`),priority:"NORMAL",status:"PENDING",createdAt:item.createdAt as string|undefined,route:"productContributions",next:locale==="tr"?"Kanıtı incele":"Review evidence",category:locale==="tr"?"Katalog":"Catalog",targetType:"PRODUCT_CONTRIBUTION",targetId:String(item.id)}))));
    if(permissions.has("CATALOG_READ")) sources.push(request<QueuePage>("/api/v1/admin/product-intakes?status=SUBMITTED&page=0&size=10").then(page=>(page.content??[]).map(item=>({key:`ocr-${item.id}`,title:locale==="tr"?"OCR ürün adayı":"OCR product candidate",detail:String(item.barcode??`#${item.id}`),priority:item.riskLevel==="HIGH"?"HIGH":"NORMAL",status:String(item.status??"SUBMITTED"),createdAt:item.createdAt as string|undefined,route:"productContributions",next:locale==="tr"?"OCR kanıtını incele":"Review OCR evidence",category:"OCR",targetType:"PRODUCT_INTAKE",targetId:String(item.id)}))));
    if(permissions.has("TECHNICAL_READ")) sources.push(request<QueuePage>("/api/v1/admin/ai/requests?status=REJECTED&refundableOnly=true&page=0&size=10").then(page=>(page.content??[]).map(item=>({key:`ai-${item.requestId??item.id}`,title:locale==="tr"?"AI iade incelemesi":"AI refund review",detail:String(item.requestType??`#${item.requestId??item.id}`),priority:"HIGH",status:"REJECTED",createdAt:(item.completedAt??item.createdAt) as string|undefined,route:"aiRequests",next:locale==="tr"?"İsteği incele":"Inspect request",category:locale==="tr"?"AI işlemleri":"AI operations",targetType:"AI_REQUEST",targetId:String(item.requestId??item.id)}))));
    if(owner&&permissions.has("ADMIN_TEAM_MANAGE")) sources.push(request<QueuePage>("/api/v1/admin/approvals?status=PENDING&page=0&size=10").then(page=>(page.content??[]).map(item=>({key:`approval-${item.id}`,title:locale==="tr"?"Owner kararı bekliyor":"Waiting for owner decision",detail:`${item.actionType??"APPROVAL"} · #${item.id}`,priority:"CRITICAL",status:"PENDING",createdAt:item.createdAt as string|undefined,route:"approvals",next:locale==="tr"?"Karar ver":"Review decision",category:locale==="tr"?"Onaylar":"Approvals",targetType:"ADMIN_APPROVAL",targetId:String(item.id)}))));
    if(owner&&permissions.has("TECHNICAL_READ")) sources.push(request<QueuePage>("/api/v1/admin/owner-alerts?status=FAILED&page=0&size=10").then(page=>(page.content??[]).map(item=>({key:`alert-${item.id}`,title:locale==="tr"?"Owner uyarısı teslim edilemedi":"Owner alert delivery failed",detail:String(item.category??`#${item.id}`),priority:"CRITICAL",status:"FAILED",createdAt:item.lastOccurredAt as string|undefined,route:"ownerAlerts",next:locale==="tr"?"Teslimatı incele":"Inspect delivery",category:locale==="tr"?"Uyarılar":"Alerts",targetType:"OWNER_ALERT",targetId:String(item.id)}))));
    if(owner&&permissions.has("TECHNICAL_READ")) sources.push(request<Array<Record<string,unknown>>>("/api/v1/admin/errors/groups?limit=10").then(groups=>groups.filter(item=>item.lifecycleStatus==="INVESTIGATING"||item.lifecycleStatus==="REOPENED").map(item=>({key:`error-${item.fingerprint}`,title:locale==="tr"?"Açık hata grubu":"Open error group",detail:`${item.method??""} ${item.route??""}`.trim(),priority:Number(item.status)>=500?"CRITICAL":"HIGH",status:String(item.lifecycleStatus),createdAt:item.lastOccurredAt as string|undefined,route:"errors",next:locale==="tr"?"Hata grubunu incele":"Inspect error group",category:locale==="tr"?"Hata merkezi":"Error center",targetType:"ERROR_GROUP",targetId:String(item.fingerprint)}))));
    setQueueState("loading"); Promise.allSettled(sources).then(results=>{if(cancelled)return;setQueueItems(results.flatMap(result=>result.status==="fulfilled"?result.value:[]).sort((a,b)=>(b.createdAt??"").localeCompare(a.createdAt??"")));setQueueState(results.some(result=>result.status==="rejected")?"error":"ready");}); return()=>{cancelled=true;};
  },[accessProfile,queueReload,locale,owner,permissions]);
  const criticalWork=queueItems.filter(item=>item.priority==="CRITICAL").length;
  const highWork=queueItems.filter(item=>item.priority==="HIGH").length;
  const queueChartData=Object.entries(queueItems.reduce<Record<string,number>>((result,item)=>{result[item.category]=(result[item.category]??0)+1;return result;},{})).sort((a,b)=>b[1]-a[1]).map(([name,value])=>({name,value}));

  function resetFilters() {
    setTypeFilter("");
    setSeverityFilter("");
    setUnreadOnly(false);
    setPage(0);
    setActionNotice(null);
  }

  async function markAllRead() {
    setActionState("loading");
    try {
      await request<unknown>("/api/v1/notifications/read-all", { method: "PATCH" });
      await reload();
      setActionNotice(locale==="tr"?"Bildirim işlemi tamamlandı.":"Notification action completed.");
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function markRead(item: Notification) {
    if (!item.id) return;
    setActionState("loading");
    try {
      await request<Notification>(`/api/v1/notifications/${item.id}/read`, { method: "PATCH" });
      setActionNotice(locale==="tr"?"Bildirim işlemi tamamlandı.":"Notification action completed.");
      await reload();
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  function goToTarget(item: Notification) {
    const target = notificationTargetSection(item);
    if (target) onNavigate(target, notificationTargetContext(item));
  }


  return (
    <div className="stack inbox-page">
      <SectionToolbar title={locale==="tr"?"İş Kutum":"My work inbox"} description={locale==="tr"?"Karar bekleyen işleri ve operasyon bildirimlerini tek çalışma alanından yönetin.":"Manage decisions and operational notifications from one focused workspace."} state={combineStates([state, actionState,queueState])} onReload={()=>{void reload();setQueueReload(value=>value+1);}}>
        <button className="ghost-button" type="button" onClick={() => onNavigate("notificationDefinitions")}>{locale==="tr"?"Bildirim kurallarını yönet":"Manage notification rules"}</button>
      </SectionToolbar>
      {actionNotice && <div className="form-notice">{actionNotice}</div>}
      <div className="metric-grid inbox-metric-grid">
        <MetricCard label={locale==="tr"?"Açık iş":"Open work"} value={formatValue(queueItems.length)} hint={locale==="tr"?"Erişebildiğiniz operasyon kuyrukları":"Operational queues available to you"}/>
        <MetricCard label={locale==="tr"?"Kritik karar":"Critical decisions"} value={formatValue(criticalWork)} hint={locale==="tr"?"Öncelikli müdahale gerektirir":"Require priority attention"}/>
        <MetricCard label={locale==="tr"?"Sayfadaki okunmamış":"Unread on page"} value={formatValue(unread)} hint={locale==="tr"?"Bu sonuç sayfasındaki okunmamış operasyon uyarıları":"Unread operational alerts in this result page"} />
        <MetricCard label={locale==="tr"?"Eşleşen":"Returned"} value={formatValue(data?.totalElements ?? rows.length)} hint={locale==="tr"?"Filtrelere uyan bildirimler":"Notifications matching the current filters"} />
      </div>
      <div className="inbox-overview-grid">
        <Panel title={locale==="tr"?"Öncelik dengesi":"Priority balance"} description={locale==="tr"?"Açık işlerin müdahale önceliği.":"Required response level across open work."}><InboxPriorityChart critical={criticalWork} high={highWork} normal={Math.max(0,queueItems.length-criticalWork-highWork)} locale={locale}/></Panel>
        <Panel title={locale==="tr"?"Kuyruk iş yükü":"Queue workload"} description={locale==="tr"?"İşlerin uzmanlık alanlarına dağılımı.":"Open work distributed across specialist areas."}><InboxQueueChart data={queueChartData} locale={locale}/></Panel>
      </div>
      <Panel className="inbox-work-panel" title={locale==="tr"?"Öncelikli işler":"Priority work"} description={locale==="tr"?"Karar veya inceleme bekleyen kayıtlar; bildirimler bu listeye tekrar eklenmez.":"Records awaiting a decision or review; notifications are kept out of this queue."}>
        <DataTable rowKeys={queueItems.map(item=>item.key)} columns={[locale==="tr"?"Öncelik":"Priority",locale==="tr"?"İş":"Work",locale==="tr"?"Alan":"Area",locale==="tr"?"Durum":"Status",locale==="tr"?"Oluşturuldu":"Created",locale==="tr"?"İşlem":"Action"]} rows={queueItems.map(item=>[<Badge value={item.priority} tone={item.priority==="CRITICAL"?"danger":item.priority==="HIGH"?"warn":"neutral"}/>,<div className="table-stack"><strong>{item.title}</strong><small>{item.detail}</small></div>,item.category,<Badge value={item.status} tone={item.status==="FAILED"?"danger":"warn"}/>,item.createdAt?formatDate(item.createdAt):"-",<button className="ghost-button" type="button" onClick={()=>onNavigate(item.route,{source:"inbox",targetType:item.targetType,targetId:item.targetId})}>{item.next}</button>])} empty={locale==="tr"?"Rolünüz için bekleyen açık iş bulunmuyor.":"No pending work is available for your role."}/>
      </Panel>
      <Panel className="inbox-feed-panel" title={locale==="tr"?"Bildirim akışı":"Notification feed"} description={locale==="tr"?"Sistem olaylarını filtreleyin, ilgili kaydı açın veya okundu olarak işaretleyin.":"Filter system events, open the related record, or mark it as read."} actions={<button className="ghost-button" type="button" disabled={actionState==="loading"||unread===0} onClick={()=>void markAllRead()}>{locale==="tr"?"Tümünü okundu yap":"Mark all read"}</button>}>
        <div className="inbox-filter-bar">
          <label>
            {locale==="tr"?"Tür":"Type"}
            <select value={typeFilter} onChange={(event) => { setTypeFilter(event.target.value); setPage(0); }}>
              <option value="">{locale==="tr"?"Tüm türler":"All types"}</option>
              <option value="ai_rejection_alert">{locale==="tr"?"AI ret uyarısı":"AI rejection alert"}</option>
              <option value="system_alert">{locale==="tr"?"Sistem uyarısı":"System alert"}</option>
              <option value="subscription_provider_alert">{locale==="tr"?"Abonelik sağlayıcı uyarısı":"Subscription provider alert"}</option>
              <option value="subscription">{locale==="tr"?"Abonelik":"Subscription"}</option>
            </select>
          </label>
          <label>
            {locale==="tr"?"Önem derecesi":"Severity"}
            <select value={severityFilter} onChange={(event) => { setSeverityFilter(event.target.value); setPage(0); }}>
              <option value="">{locale==="tr"?"Tüm dereceler":"All severities"}</option>
              <option value="CRITICAL">{locale==="tr"?"Kritik":"Critical"}</option>
              <option value="WARNING">{locale==="tr"?"Uyarı":"Warning"}</option>
              <option value="INFO">{locale==="tr"?"Bilgi":"Info"}</option>
            </select>
          </label>
          <label className="inbox-unread-toggle">
            <input checked={unreadOnly} onChange={(event) => { setUnreadOnly(event.target.checked); setPage(0); }} type="checkbox" />
            {locale==="tr"?"Yalnız okunmamış":"Unread only"}
          </label>
          <button className="ghost-button" type="button" disabled={!typeFilter&&!severityFilter&&!unreadOnly} onClick={resetFilters}>{locale==="tr"?"Temizle":"Clear"}</button>
        </div>
      <DataTable
        columns={[locale==="tr"?"Önem":"Severity", locale==="tr"?"Tür":"Type", locale==="tr"?"Mesaj":"Message", locale==="tr"?"Hedef":"Target", locale==="tr"?"Okunma":"Read", locale==="tr"?"Oluşturuldu":"Created", locale==="tr"?"İşlemler":"Actions"]}
        rows={rows.map((item) => [
          <Badge value={item.severity ?? "INFO"} tone={notificationSeverityTone(item.severity)} />,
          <div className="badge-stack"><Badge value={notificationTypeLabel(item.type,locale)} /><Badge value={notificationSourceLabel(item.source,locale)} tone="neutral" /></div>,
          <span className="notification-message-cell">{item.message ?? "-"}</span>,
          notificationTargetLabel(item),
          <Badge value={item.read ? (locale==="tr"?"Okundu":"Read") : (locale==="tr"?"Okunmadı":"Unread")} tone={item.read ? "neutral" : "warn"} />,
          formatDate(item.createdAt),
          <div className="table-stack notification-actions">
            {notificationTargetSection(item) && <button className="ghost-button" type="button" onClick={(event) => { event.stopPropagation(); goToTarget(item); }}>{locale==="tr"?"Hedefi aç":"Open target"}</button>}
            {!item.read && <button className="ghost-button" type="button" disabled={actionState === "loading"} onClick={(event) => { event.stopPropagation(); void markRead(item); }}>{locale==="tr"?"Okundu işaretle":"Mark read"}</button>}
          </div>
        ])}
        empty={locale==="tr"?"Bildirim bulunamadı.":"No notifications returned."}
      />
      <PaginationControls
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? rows.length}
        totalPages={data?.totalPages ?? 1}
        first={Boolean(data?.first)}
        last={Boolean(data?.last)}
        onPageChange={setPage}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
      />
      </Panel>
    </div>
  );
}

export function notificationTypeLabel(value?: string,locale:"tr"|"en"="en"): string {
  switch (value) {
    case "subscription_provider_alert":
      return locale==="tr"?"Sağlayıcı hatası":"Provider failure";
    case "system_alert":
      return locale==="tr"?"Sistem uyarısı":"System alert";
    case "ai_rejection_alert":
      return locale==="tr"?"AI reddi":"AI rejection";
    case "subscription":
      return locale==="tr"?"Abonelik":"Subscription";
    default:
      return shortFeature(value);
  }
}

export function notificationSourceLabel(value?: string,_locale:"tr"|"en"="en"): string {
  switch (value) {
    case "REVENUECAT":
      return "RevenueCat";
    case "MAIL_PROVIDER":
      return "Mail provider";
    case "AI_OPS":
      return "AI Ops";
    default:
      return shortFeature(value);
  }
}

export function buildNotificationPath(filters: { type: string; severity: string; unreadOnly: boolean; page: number; size: number }): string {
  const params = new URLSearchParams();
  if (filters.type) params.set("type", filters.type);
  if (filters.severity) params.set("severity", filters.severity);
  if (filters.unreadOnly) params.set("unreadOnly", "true");
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/notifications?${params.toString()}`;
}

export function notificationTargetSection(item: Notification): SectionKey | null {
  const exactTargets = new Set(["AI_REQUEST", "MAIL_MONITORING", "SUBSCRIPTION_PROVIDER_EVENT", "ADMIN_ACCOUNT"]);
  if (item.targetType && !exactTargets.has(item.targetType)) return null;
  const candidate = item.targetRoute || fallbackNotificationTargetRoute(item.type, item.targetType);
  return isSectionKey(candidate) ? candidate : null;
}

export function notificationTargetContext(item: Notification): Omit<AdminTargetContext, "section"> {
  return {
    source: "notification",
    notificationId: item.id,
    severity: item.severity,
    type: item.type,
    message: item.message,
    targetType: item.targetType,
    targetId: item.targetId,
    targetRoute: item.targetRoute
  };
}

export function fallbackNotificationTargetRoute(type?: string, targetType?: string): string | undefined {
  if (targetType === "AI_REQUEST" || type === "ai_rejection_alert") return "ai";
  if (targetType === "MAIL_MONITORING" || type === "system_alert") return "mail";
  if (targetType === "SUBSCRIPTION_PROVIDER_EVENT" || type === "subscription_provider_alert") return "subscriptionEvents";
  return undefined;
}
