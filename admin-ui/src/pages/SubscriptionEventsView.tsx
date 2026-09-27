import { useEffect, useState } from "react";

import { formatRequestError, request } from "../api";

import { SubscriptionProviderEvent, SubscriptionProviderEventFilterOptions, SubscriptionProviderEventPage } from "../types";

import { CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { AdminTargetContext, Badge, TargetAwareValue, TargetContextBanner, combineStates, formatDate, formatValue, isTargetMatch, shortFeature, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

export const SUBSCRIPTION_EVENT_STATUSES = ["RECEIVED", "PROCESSED", "REQUIRES_REVIEW", "FAILED", "IGNORED"];

export function SubscriptionEventsView({ onError, targetContext, onClearTarget }: { onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  const [status, setStatus] = useState("");
  const [eventType, setEventType] = useState("");
  const [eventId, setEventId] = useState("");
  const [productId, setProductId] = useState("");
  const [userId, setUserId] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [selected, setSelected] = useState<SubscriptionProviderEvent | null>(null);
  const [detailState, setDetailState] = useState<LoadState>("idle");
  const [retryState, setRetryState] = useState<LoadState>("idle");
  const path = buildSubscriptionEventsPath({ status, eventType, eventId, productId, userId, page, size: pageSize });
  const { data, state, reload } = useEndpoint<SubscriptionProviderEventPage>(path, onError);
  const { data: filterOptions, state: filterState, reload: reloadFilterOptions } = useEndpoint<SubscriptionProviderEventFilterOptions>("/api/v1/admin/subscription-events/filter-options", onError);
  const rows = data?.content ?? [];
  const focusedEventId = targetContext?.targetType === "SUBSCRIPTION_PROVIDER_EVENT" ? targetContext.targetId : undefined;

  useEffect(() => {
    if (targetContext?.targetType !== "SUBSCRIPTION_PROVIDER_EVENT" || !targetContext.targetId) return;
    setStatus("");
    setEventType("");
    setEventId("");
    setProductId("");
    setUserId("");
    setPage(0);
  }, [targetContext?.targetType, targetContext?.targetId]);

  function resetFilters() {
    setStatus("");
    setEventType("");
    setEventId("");
    setProductId("");
    setUserId("");
    setPage(0);
  }

  async function openDetail(item: SubscriptionProviderEvent) {
    if (!item.id) return;
    setSelected(item);
    setDetailState("loading");
    try {
      const detail = await request<SubscriptionProviderEvent>(`/api/v1/admin/subscription-events/${item.id}`);
      setSelected(detail);
      setDetailState("ready");
    } catch (error) {
      setDetailState("error");
      onError(formatRequestError(error));
    }
  }

  async function retrySelectedEvent() {
    if (!selected?.id) return;
    setRetryState("loading");
    try {
      await request<unknown>(`/api/v1/admin/subscription-events/${selected.id}/retry`, { method: "POST" });
      setRetryState("ready");
      await reload();
      await openDetail(selected);
    } catch (error) {
      setRetryState("error");
      onError(formatRequestError(error));
    }
  }


  return (
    <div className="stack subscription-events-view">
      <SectionToolbar title={tx("Provider event operations", "Sağlayıcı olay operasyonları")} description={tx("Inspect subscription lifecycle events, processing outcomes and recovery actions.", "Abonelik yaşam döngüsü olaylarını, işleme sonuçlarını ve kurtarma işlemlerini inceleyin.")} state={combineStates([state, filterState, detailState, retryState])} onReload={() => { void reload(); void reloadFilterOptions(); }}>
        <button className="ghost-button compact-action-button" onClick={resetFilters} type="button">{tx("Clear selection", "Seçimi temizle")}</button>
      </SectionToolbar>
      {targetContext && <TargetContextBanner context={targetContext} onClear={onClearTarget} />}
      <div className="subscription-event-summary">
        <MetricCard label={tx("Matching events", "Eşleşen olaylar")} value={formatValue(data?.totalElements ?? 0)} hint={tx("Current filter result", "Geçerli filtre sonucu")} />
        <MetricCard label={tx("Processed on page", "Sayfadaki işlenen")} value={String(rows.filter(item => item.status === "PROCESSED").length)} hint={tx("Successfully completed", "Başarıyla tamamlandı")} />
        <MetricCard label={tx("Requires review", "İnceleme gerekli")} value={String(rows.filter(item => item.status === "REQUIRES_REVIEW").length)} hint={tx("Ownership or policy review on this page", "Bu sayfadaki sahiplik veya politika incelemeleri")} />
      </div>
      <CollapsiblePanel className="subscription-event-filters" title={tx("Event filters", "Olay filtreleri")} description={activeFilterSummary({ status, eventType, eventId, productId, userId }, tx)} open={filtersOpen} onToggle={() => setFiltersOpen(value => !value)}>
        <div className="subscription-event-filter-grid">
          <label>
            {tx("Status", "Durum")}
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="">{tx("All statuses", "Tüm durumlar")}</option>
              {SUBSCRIPTION_EVENT_STATUSES.map((item) => <option key={item} value={item}>{shortFeature(item)}</option>)}
            </select>
          </label>
          <label>
            {tx("Event type", "Olay türü")}
            <select value={eventType} onChange={(event) => { setEventType(event.target.value); setPage(0); }}>
              <option value="">{tx("All event types", "Tüm olay türleri")}</option>
              {(filterOptions?.eventTypes ?? []).map(item => <option key={item} value={item}>{shortFeature(item)}</option>)}
            </select>
          </label>
          <label>
            {tx("Event id", "Olay kimliği")}
            <select value={eventId} onChange={(event) => { setEventId(event.target.value); setPage(0); }}>
              <option value="">{tx("All event ids", "Tüm olay kimlikleri")}</option>
              {(filterOptions?.eventIds ?? []).map(item => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label>
            {tx("Product id", "Ürün kimliği")}
            <select value={productId} onChange={(event) => { setProductId(event.target.value); setPage(0); }}>
              <option value="">{tx("All products", "Tüm ürünler")}</option>
              {(filterOptions?.productIds ?? []).map(item => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label>
            {tx("User id", "Kullanıcı kimliği")}
            <input value={userId} onChange={(event) => { setUserId(event.target.value.replace(/[^0-9]/g, "")); setPage(0); }} placeholder="123" />
          </label>
        </div>
      </CollapsiblePanel>
      <DataTable
        columns={["ID", tx("Provider", "Sağlayıcı"), tx("Event", "Olay"), tx("Product", "Ürün"), tx("User", "Kullanıcı"), tx("Environment", "Ortam"), tx("Reason", "Neden"), tx("Status", "Durum"), tx("Event time", "Olay zamanı"), tx("Processed", "İşlendi")]}
        rows={rows.map((item) => [
          <TargetAwareValue value={item.id ?? "-"} focused={isTargetMatch(focusedEventId, item.id)} />,
          item.provider ?? "-",
          item.eventType ?? item.providerEventId ?? "-",
          item.productId ?? "-",
          item.userEmail ?? item.userId ?? item.providerAppUserId ?? "-",
          item.environment ?? "-",
          item.cancelReason ?? item.expirationReason ?? "-",
          <Badge value={item.status} tone={subscriptionEventTone(item.status)} />,
          formatDate(item.providerEventAt ?? item.receivedAt),
          formatDate(item.processedAt)
        ])}
        rowData={rows}
        onRowClick={openDetail}
        empty={tx("No subscription provider events returned.", "Abonelik sağlayıcı olayı bulunamadı.")}
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
      {selected && <SubscriptionEventModal event={selected} retryState={retryState} onClose={() => setSelected(null)} onRetry={retrySelectedEvent} />}
    </div>
  );
}

export function SubscriptionEventModal({
  event,
  retryState,
  onClose,
  onRetry
}: {
  event: SubscriptionProviderEvent;
  retryState: LoadState;
  onClose: () => void;
  onRetry: () => void;
}) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  const canRetry = event.status === "FAILED";
  const eventTitle = event.eventType ? shortFeature(event.eventType) : tx("Provider event", "Sağlayıcı olayı");
  const userValue = event.userEmail ?? event.userId ?? event.providerAppUserId;
  return (
    <div className="modal-backdrop subscription-event-modal-backdrop" onClick={onClose}>
      <section className="modal-card subscription-event-modal" role="dialog" aria-modal="true" aria-label={tx("Subscription provider event detail", "Abonelik sağlayıcı olayı detayı")} onClick={(clickEvent) => clickEvent.stopPropagation()}>
        <header className="modal-header subscription-event-modal-header">
          <div>
            <p className="eyebrow">{tx("Provider event", "Sağlayıcı olayı")}</p>
            <div className="subscription-event-title-line"><h2>{eventTitle}</h2><Badge value={event.status} tone={subscriptionEventTone(event.status)} /></div>
            <p className="subscription-event-id" title={event.providerEventId}>{event.providerEventId ?? `Event #${formatValue(event.id)}`}</p>
          </div>
          <button className="modal-icon-close" onClick={onClose} type="button" aria-label={tx("Close", "Kapat")}>×</button>
        </header>
        <div className="subscription-event-modal-body">
          <section className="subscription-event-hero-grid" aria-label={tx("Event summary", "Olay özeti")}>
            <EventFact label={tx("Provider", "Sağlayıcı")} value={event.provider} emphasis />
            <EventFact label={tx("Product", "Ürün")} value={event.productId} emphasis />
            <EventFact label={tx("Environment", "Ortam")} value={event.environment} />
            <EventFact label={tx("Period", "Dönem")} value={event.periodType} />
          </section>
          <div className="subscription-event-section-grid">
            <section className="subscription-event-section">
              <header><span>{tx("ACCOUNT & ACCESS", "HESAP VE ERİŞİM")}</span><h3>{tx("Customer entitlement", "Kullanıcı hakkı")}</h3></header>
              <div className="subscription-event-fact-list">
                <EventFact label={tx("User", "Kullanıcı")} value={userValue} wide />
                <EventFact label={tx("Entitlements", "Haklar")} value={event.entitlementIds} />
                <EventFact label={tx("Delivered", "Teslim edildi")} value={formatValue(event.entitlementDeliveredSnapshot)} />
                <EventFact label={tx("Active before event", "Olay öncesinde aktif")} value={formatValue(event.entitlementActiveSnapshot)} />
              </div>
            </section>
            <section className="subscription-event-section">
              <header><span>{tx("TRANSACTION", "İŞLEM")}</span><h3>{tx("Provider references", "Sağlayıcı referansları")}</h3></header>
              <div className="subscription-event-fact-list">
                <EventFact label={tx("Transaction", "İşlem")} value={event.transactionId} wide />
                <EventFact label={tx("Original transaction", "Orijinal işlem")} value={event.originalTransactionId} wide />
                <EventFact label={tx("Recorded owner", "Kayıtlı sahip")} value={event.ownershipOwnerUserId == null ? undefined : `User #${event.ownershipOwnerUserId}`} />
                <EventFact label={tx("Historical review", "Geçmiş incelemesi")} value={event.ownershipRequiresReview == null ? undefined : event.ownershipRequiresReview ? tx("Required", "Gerekli") : tx("Clear", "Temiz")} />
                <EventFact label={tx("Cancellation reason", "İptal nedeni")} value={event.cancelReason} />
                <EventFact label={tx("Expiration reason", "Sona erme nedeni")} value={event.expirationReason} />
              </div>
            </section>
          </div>
          <section className="subscription-event-section">
            <header><span>{tx("LIFECYCLE", "YAŞAM DÖNGÜSÜ")}</span><h3>{tx("Event timeline", "Olay zaman çizelgesi")}</h3></header>
            <div className="subscription-event-timeline">
              <EventFact label={tx("Purchased", "Satın alındı")} value={formatDate(event.purchasedAt)} />
              <EventFact label={tx("Provider event", "Sağlayıcı olayı")} value={formatDate(event.providerEventAt)} />
              <EventFact label={tx("Received", "Alındı")} value={formatDate(event.receivedAt)} />
              <EventFact label={tx("Processed", "İşlendi")} value={formatDate(event.processedAt)} />
              <EventFact label={tx("Expires", "Sona erer")} value={formatDate(event.expirationAt)} />
            </div>
          </section>
          <section className="subscription-event-section">
            <header><span>{tx("USAGE SNAPSHOT", "KULLANIM ANLIK GÖRÜNTÜSÜ")}</span><h3>{tx("AI quota at event time", "Olay anındaki AI kotası")}</h3></header>
            <div className="subscription-event-usage-grid">
              <EventFact label={tx("Plan usage", "Plan kullanımı")} value={`${formatValue(event.planUsedSnapshot)} / ${formatValue(event.planQuotaSnapshot)}`} emphasis />
              <EventFact label={tx("Add-on usage", "Ek paket kullanımı")} value={`${formatValue(event.addonUsedSnapshot)} / ${formatValue(event.addonQuotaSnapshot)}`} emphasis />
              <EventFact label={tx("Apple refund consent", "Apple iade izni")} value={event.appleRefundConsentStatus} />
              <EventFact label={tx("Consent version", "İzin sürümü")} value={event.appleRefundConsentVersion} />
            </div>
          </section>
          {event.processingError && <section className="subscription-event-section danger"><header><span>{tx("PROCESSING", "İŞLEME")}</span><h3>{tx("Processing error", "İşleme hatası")}</h3></header><pre className="audit-value-block">{event.processingError}</pre></section>}
          {event.rawPayload && <details className="subscription-event-payload"><summary>{tx("View raw provider payload", "Ham sağlayıcı verisini görüntüle")}</summary><pre className="audit-value-block">{event.rawPayload}</pre></details>}
        </div>
        <footer className="modal-actions">
          <button className="ghost-button" onClick={onClose} type="button">{tx("Close", "Kapat")}</button>
          {canRetry && <button className="primary-button" disabled={retryState === "loading"} onClick={onRetry} type="button">{tx("Retry event", "Olayı yeniden dene")}</button>}
        </footer>
      </section>
    </div>
  );
}

function EventFact({ label, value, emphasis = false, wide = false }: { label: string; value: unknown; emphasis?: boolean; wide?: boolean }) {
  return <article className={`subscription-event-fact${emphasis ? " emphasis" : ""}${wide ? " wide" : ""}`}><span>{label}</span><strong title={value == null ? undefined : String(value)}>{value == null || value === "" ? "-" : String(value)}</strong></article>;
}

export function buildSubscriptionEventsPath(filters: {
  status: string;
  eventType: string;
  eventId: string;
  productId: string;
  userId: string;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.eventType.trim()) params.set("eventType", filters.eventType.trim());
  if (filters.eventId.trim()) params.set("eventId", filters.eventId.trim());
  if (filters.productId.trim()) params.set("productId", filters.productId.trim());
  if (filters.userId.trim()) params.set("userId", filters.userId.trim());
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/subscription-events?${params.toString()}`;
}

function activeFilterSummary(filters: { status: string; eventType: string; eventId: string; productId: string; userId: string }, tx: (english: string, turkish: string) => string) {
  const count = Object.values(filters).filter(Boolean).length;
  return count ? `${count} ${tx("active filter(s)", "aktif filtre")}` : tx("Status, event type, event id, product or user", "Durum, olay türü, olay kimliği, ürün veya kullanıcı");
}

export function subscriptionEventTone(value?: string): "default" | "good" | "warn" | "danger" | "neutral" {
  switch (value) {
    case "PROCESSED":
      return "good";
    case "FAILED":
      return "danger";
    case "REQUIRES_REVIEW":
      return "warn";
    case "IGNORED":
      return "neutral";
    case "RECEIVED":
      return "warn";
    default:
      return "default";
  }
}
