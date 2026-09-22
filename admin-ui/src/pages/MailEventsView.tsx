import { useState } from "react";

import { AdminMailMonitoring } from "../types";

import { EmptyState, MetricCard, Panel, SectionToolbar } from "../AdminPrimitives";
import { CommunicationBars } from "../CommunicationsPrimitives";
import { useAdminLocale } from "../admin/locale";

import { Badge, formatDate, formatValue, humanizeFeature, useEndpoint } from "./../admin/shared";

export function MailEventList({ events }: { events: NonNullable<AdminMailMonitoring["recentEvents"]> }) {
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const [eventFilter, setEventFilter] = useState("");

  if (!events.length) {
    return <EmptyState message={tx("No Brevo event returned for this period.", "Bu dönem için Brevo olayı bulunamadı.")} />;
  }

  const eventTypes = Array.from(new Set(events.map((event) => event.event).filter(Boolean))).sort();
  const filteredEvents = eventFilter ? events.filter((event) => event.event === eventFilter) : events;

  return (
    <div className="mail-event-manager">
      <div className="mail-event-toolbar">
        <div>
          <strong>{formatValue(filteredEvents.length)}</strong>
          <span>{eventFilter ? `${humanizeFeature(eventFilter)} ${tx("events", "olay")}` : tx("events returned", "olay getirildi")}</span>
        </div>
        <label>
          {tx("Event type", "Olay türü")}
          <select value={eventFilter} onChange={(event) => setEventFilter(event.target.value)}>
            <option value="">{tx("All events", "Tüm olaylar")}</option>
            {eventTypes.map((eventType) => (
              <option key={eventType} value={eventType}>{humanizeFeature(eventType)}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="mail-event-list" role="table" aria-label={tx("Recent Brevo events", "Son Brevo olayları")}>
        <div className="mail-event-row head" role="row">
          <span>{tx("Event", "Olay")}</span>
          <span>{tx("Recipient", "Alıcı")}</span>
          <span>{tx("Subject", "Konu")}</span>
          <span>{tx("Date", "Tarih")}</span>
        </div>
        {filteredEvents.map((event, index) => (
          <div className="mail-event-row" key={`${event.messageId ?? event.email ?? "mail-event"}-${index}`} role="row">
            <div>
              <Badge value={event.event} />
              {event.reason && <small>{event.reason}</small>}
            </div>
            <span>{formatValue(event.email)}</span>
            <div>
              <strong>{formatValue(event.subject)}</strong>
              {event.messageId && <small>{event.messageId}</small>}
            </div>
            <small>{formatValue(event.date)}</small>
          </div>
        ))}
        {!filteredEvents.length && <EmptyState message={tx("No event matches this filter.", "Bu filtreyle eşleşen olay yok.")} />}
      </div>
    </div>
  );
}

export function MailEventsView({ onError, embedded = false }: { onError: (message: string | null) => void; embedded?: boolean }) {
  const { data, state, reload } = useEndpoint<AdminMailMonitoring>("/api/v1/admin/mail/monitoring?days=7&limit=50", onError);
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const events = data?.recentEvents ?? [];
  const eventDistribution = Array.from(new Set(events.map((event) => event.event).filter(Boolean))).map((eventType) => ({ label: humanizeFeature(eventType), value: events.filter((event) => event.event === eventType).length })).sort((left, right) => right.value - left.value).slice(0, 6);


  return (
    <div className="stack communications-ops-view mail-events-page">
      {!embedded && <SectionToolbar title={tx("Recent Brevo mail events", "Son Brevo e-posta olayları")} state={state} onReload={reload} />}
      {!embedded && <div className="mail-events-hero">
        <div>
          <p className="eyebrow">{tx("Delivery audit", "Teslimat denetimi")}</p>
          <h2>{tx("Review recent transactional mail events separately from Mail Ops overview.", "Son işlemsel e-posta olaylarını ayrı bir çalışma alanında inceleyin.")}</h2>
          <p>{tx("This page is focused on provider events only. Monitoring counters and delivery policy remain on Mail Ops.", "Bu alan yalnızca sağlayıcı olaylarına odaklanır. İzleme sayaçları ve teslimat politikası E-posta İşlemleri sayfasında kalır.")}</p>
        </div>
        <Badge value={data?.providerReachable ? tx("Brevo reachable", "Brevo erişilebilir") : tx("Safe proxy", "Güvenli proxy")} tone={data?.providerReachable ? "good" : "neutral"} />
      </div>}
      <div className="metric-grid">
        <MetricCard label={tx("Returned events", "Getirilen olaylar")} value={formatValue(events.length)} hint={tx("Current provider response", "Güncel sağlayıcı yanıtı")} />
        <MetricCard label={tx("Provider", "Sağlayıcı")} value={formatValue(data?.provider)} hint={data?.statusMessage ?? tx("Backend proxy", "Backend proxy")} />
        <MetricCard label={tx("Checked", "Kontrol zamanı")} value={formatDate(data?.checkedAt)} hint={formatValue(data?.providerBaseUrl)} />
      </div>
      <section className="communication-insight-strip"><div><p className="eyebrow">{tx("Event distribution", "Olay dağılımı")}</p><h3>{tx("Provider outcomes in this response", "Bu yanıttaki sağlayıcı sonuçları")}</h3><p>{tx("The chart follows the same filtered provider window as the event list.", "Grafik, olay listesiyle aynı sağlayıcı zaman aralığını kullanır.")}</p></div><CommunicationBars items={eventDistribution} empty={tx("No provider events returned.", "Sağlayıcı olayı bulunamadı.")} /></section>
      <Panel title={tx("Recent Brevo events", "Son Brevo olayları")}>
        <MailEventList events={events} />
      </Panel>
    </div>
  );
}
