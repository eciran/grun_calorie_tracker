import { useEffect, useState } from "react";

import { formatRequestError, request } from "../api";

import { SubscriptionProviderEvent, SubscriptionProviderEventFilterOptions, SubscriptionProviderEventPage } from "../types";

import { CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { AdminTargetContext, Badge, DetailItem, TargetAwareValue, TargetContextBanner, combineStates, formatDate, formatValue, isTargetMatch, shortFeature, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

export const SUBSCRIPTION_EVENT_STATUSES = ["RECEIVED", "PROCESSED", "FAILED", "IGNORED"];

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
        <MetricCard label={tx("Requires review", "İnceleme gerekli")} value={String(rows.filter(item => item.status === "FAILED" || item.status === "RECEIVED").length)} hint={tx("Failed or waiting on this page", "Bu sayfadaki başarısız veya bekleyen")} />
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
  return (
    <div className="modal-backdrop">
      <section className="audit-modal" role="dialog" aria-modal="true" aria-label={tx("Subscription provider event detail", "Abonelik sağlayıcı olayı detayı")} onClick={(event) => event.stopPropagation()}>
        <header>
          <div>
            <p className="eyebrow">{tx("Provider event", "Sağlayıcı olayı")}</p>
            <h2>{event.providerEventId ?? `Event #${formatValue(event.id)}`}</h2>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label={tx("Close", "Kapat")}>x</button>
        </header>
        <div className="modal-grid">
          <DetailItem label={tx("Provider", "Sağlayıcı")} value={event.provider} />
          <DetailItem label={tx("Event type", "Olay türü")} value={event.eventType} />
          <DetailItem label={tx("Product", "Ürün")} value={event.productId} />
          <DetailItem label={tx("Entitlements", "Haklar")} value={event.entitlementIds} />
          <DetailItem label={tx("User", "Kullanıcı")} value={event.userEmail ?? event.userId ?? event.providerAppUserId} />
          <DetailItem label={tx("Transaction", "İşlem")} value={event.transactionId} />
          <DetailItem label={tx("Original transaction", "Orijinal işlem")} value={event.originalTransactionId} />
          <DetailItem label={tx("Period type", "Dönem türü")} value={event.periodType} />
          <DetailItem label={tx("Environment", "Ortam")} value={event.environment} />
          <DetailItem label={tx("Cancellation reason", "İptal nedeni")} value={event.cancelReason} />
          <DetailItem label={tx("Expiration reason", "Sona erme nedeni")} value={event.expirationReason} />
          <DetailItem label={tx("Status", "Durum")} value={event.status} />
          <DetailItem label={tx("Provider event time", "Sağlayıcı olay zamanı")} value={formatDate(event.providerEventAt)} />
          <DetailItem label={tx("Purchased", "Satın alındı")} value={formatDate(event.purchasedAt)} />
          <DetailItem label={tx("Expires", "Sona erer")} value={formatDate(event.expirationAt)} />
          <DetailItem label="Apple refund consent" value={event.appleRefundConsentStatus} />
          <DetailItem label="Consent version" value={event.appleRefundConsentVersion} />
          <DetailItem label="Entitlement delivered" value={formatValue(event.entitlementDeliveredSnapshot)} />
          <DetailItem label="Entitlement active before event" value={formatValue(event.entitlementActiveSnapshot)} />
          <DetailItem label="Plan AI usage snapshot" value={`${formatValue(event.planUsedSnapshot)} / ${formatValue(event.planQuotaSnapshot)}`} />
          <DetailItem label="Add-on AI usage snapshot" value={`${formatValue(event.addonUsedSnapshot)} / ${formatValue(event.addonQuotaSnapshot)}`} />
          <DetailItem label="Received" value={formatDate(event.receivedAt)} />
          <DetailItem label="Processed" value={formatDate(event.processedAt)} />
        </div>
        {event.processingError && <Panel title={tx("Processing error", "İşleme hatası")}>
          <pre className="audit-value-block">{event.processingError}</pre>
        </Panel>}
        {event.rawPayload && <Panel title={tx("Raw payload", "Ham veri")}>
          <pre className="audit-value-block">{event.rawPayload}</pre>
        </Panel>}
        <footer className="modal-actions">
          <button className="ghost-button" onClick={onClose} type="button">{tx("Close", "Kapat")}</button>
          {canRetry && <button className="primary-button" disabled={retryState === "loading"} onClick={onRetry} type="button">{tx("Retry event", "Olayı yeniden dene")}</button>}
        </footer>
      </section>
    </div>
  );
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
    case "IGNORED":
      return "neutral";
    case "RECEIVED":
      return "warn";
    default:
      return "default";
  }
}
