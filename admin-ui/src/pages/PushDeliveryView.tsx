import { AdminPushMonitoring } from "../types";

import { EmptyState, MetricCard, Panel, SectionToolbar } from "../AdminPrimitives";
import { CommunicationHero } from "../CommunicationsPrimitives";
import { useAdminLocale } from "../admin/locale";

import { formatValue, percent, useEndpoint } from "./../admin/shared";

export function PushDeliveryView({ onError, embedded = false }: { onError: (message: string | null) => void; embedded?: boolean }) {
  const { data, state, reload } = useEndpoint<AdminPushMonitoring>("/api/v1/admin/system/push-monitoring", onError);
  const { locale } = useAdminLocale(); const tx = (en: string, tr: string) => locale === "tr" ? tr : en;
  const providerEntries = Object.entries(data?.activeTokensByProvider ?? {}).sort(([left], [right]) => left.localeCompare(right));
  const configuredProviders = [
    ["Expo", data?.expoConfigured],
    ["FCM", data?.fcmConfigured],
    ["OneSignal", data?.oneSignalConfigured]
  ];
  const failed = data?.failedLast24h ?? 0;
  const sent = data?.sentLast24h ?? 0;
  const totalDelivery = sent + failed;


  return (
    <div className="stack communications-ops-view push-delivery-page">
      {!embedded && <SectionToolbar title={tx("Push delivery monitoring", "Push teslimatı izleme")} state={state} onReload={reload} />}
      {!embedded && <CommunicationHero eyebrow={tx("Mobile delivery", "Mobil teslimat")} title={tx("Push channel health at a glance.", "Push kanalının sağlığını tek bakışta izleyin.")} description={tx("Track delivery volume, provider readiness and active device reach without exposing tokens or credentials.", "Tokenları veya kimlik bilgilerini göstermeden teslimat hacmini, sağlayıcı hazırlığını ve aktif cihaz erişimini izleyin.")} status={<span className={`communication-status-pill ${data?.enabled ? "good" : "blocked"}`}>{data?.enabled ? tx("DELIVERY READY", "TESLİMATA HAZIR") : tx("DELIVERY BLOCKED", "TESLİMAT KAPALI")}</span>} />}
      <div className="review-workspace-summary">
        <MetricCard label={tx("Delivery state", "Teslimat durumu")} value={data?.enabled ? tx("Enabled", "Açık") : tx("Disabled", "Kapalı")} hint={`${tx("Selected provider", "Seçili sağlayıcı")}: ${formatValue(data?.provider)}`} />
        <MetricCard label={tx("Active tokens", "Aktif tokenlar")} value={formatValue(data?.activeTokenCount)} hint={tx("Enabled device token records", "Etkin cihaz token kayıtları")} />
        <MetricCard label={tx("Sent 24h", "24 saatte gönderilen")} value={formatValue(sent)} hint={tx("Push delivery logs marked SENT", "SENT durumundaki push kayıtları")} />
        <MetricCard label={tx("Failed 24h", "24 saatte başarısız")} value={formatValue(failed)} hint={`${tx("Logged attempts", "Kayıtlı denemeler")}: ${percent(failed, totalDelivery)}%`} />
      </div>

      <div className="ops-grid">
        <Panel title={tx("Provider token distribution", "Sağlayıcı token dağılımı")}>
          <div className="distribution-list">
            {providerEntries.map(([provider, count]) => (
              <div key={provider}>
                <div>
                  <span>{provider}</span>
                  <strong>{formatValue(count)}</strong>
                </div>
                <div className="progress-track">
                  <span className="good" style={{ width: `${percent(count, data?.activeTokenCount ?? 0)}%` }} />
                </div>
              </div>
            ))}
            {!providerEntries.length && <EmptyState message={tx("No active push token returned.", "Aktif push tokenı bulunamadı.")} />}
          </div>
        </Panel>
        <Panel title={tx("Provider configuration", "Sağlayıcı yapılandırması")}>
          <div className="config-grid">
            {configuredProviders.map(([provider, configured]) => (
              <div className="config-block" key={String(provider)}>
                <span>{provider}</span>
                <strong>{configured ? tx("Configured", "Yapılandırıldı") : tx("Missing", "Eksik")}</strong>
              </div>
            ))}
          </div>
        </Panel>
      </div>

      <Panel title={tx("Operational policy", "Operasyon politikası")}>
        <div className="roadmap-strip">
          <span>{tx("Raw device tokens are never exposed in admin responses", "Ham cihaz tokenları yönetim yanıtlarında gösterilmez")}</span>
          <span>{tx("Invalid provider-token responses disable the stored token", "Geçersiz sağlayıcı yanıtları kayıtlı tokenı devre dışı bırakır")}</span>
          <span>{tx("Provider credentials stay in environment configuration", "Sağlayıcı kimlik bilgileri ortam yapılandırmasında kalır")}</span>
          <span>{tx("Real delivery validation requires mobile device tokens", "Gerçek teslimat doğrulaması mobil cihaz tokenı gerektirir")}</span>
        </div>
      </Panel>
    </div>
  );
}
