import { RevenueCatConfigStatus, SystemHealth } from "../types";
import { CollapsiblePanel, MetricCard, SectionToolbar } from "../AdminPrimitives";
import { combineStates, formatValue, readNumber, useEndpoint } from "../admin/shared";
import { useAdminLocale } from "../admin/locale";
import { useState } from "react";

export type IntegrationMode = "overview" | "providers";
type Tone = "good" | "warn" | "neutral";
type Item = { name: string; category: string; status: string; tone: Tone; description: string; route: string; routeLabel: string; facts: Array<[string, string]> };

export function IntegrationsView({ mode, onError }: { mode: IntegrationMode; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, trText: string) => tr ? trText : en;
  const [attentionOpen, setAttentionOpen] = useState(false);
  const { data: health, state: healthState, reload: reloadHealth } = useEndpoint<SystemHealth>("/api/v1/admin/system/health", onError);
  const { data: revenueCat, state: revenueCatState, reload: reloadRevenueCat } = useEndpoint<RevenueCatConfigStatus>("/api/v1/admin/revenuecat/config", onError);
  const warnings = Array.isArray(health?.warnings) ? health.warnings.map(String) : [];
  const providerIssues = [...(revenueCat?.missingRequiredConfig ?? []), ...(revenueCat?.warnings ?? [])];
  const reloadAll = () => { void reloadHealth(); void reloadRevenueCat(); };

  const items: Item[] = [
    { name: "RevenueCat", category: tx("Subscriptions", "Abonelikler"), status: revenueCat?.productionReady ? tx("Ready", "Hazır") : tx("Action needed", "İşlem gerekli"), tone: revenueCat?.productionReady ? "good" : "warn", description: tx("Store subscription events and entitlement synchronization.", "Mağaza abonelik olayları ve hak eşitleme akışı."), route: "/admin/subscriptions/events", routeLabel: tx("Open provider events", "Sağlayıcı olaylarını aç"), facts: [["Webhook", revenueCat?.webhookAuthorizationConfigured ? tx("Configured", "Yapılandırıldı") : tx("Missing", "Eksik")], [tx("Product mapping", "Ürün eşleştirmesi"), revenueCat?.strictProductMapping ? tx("Strict", "Katı") : tx("Flexible", "Esnek")]] },
    { name: "Brevo", category: tx("Transactional email", "İşlemsel e-posta"), status: readNumber(health, "systemAlertsLast24h") ? tx("Monitor", "İzlenmeli") : tx("Operational", "Çalışıyor"), tone: readNumber(health, "systemAlertsLast24h") ? "warn" : "good", description: tx("Verification, password reset and account lifecycle delivery.", "Doğrulama, parola sıfırlama ve hesap yaşam döngüsü teslimatı."), route: "/admin/mail", routeLabel: tx("Open delivery center", "Teslimat merkezini aç"), facts: [[tx("Delivery center", "Teslimat merkezi"), tx("Available", "Kullanılabilir")], [tx("System alerts · 24h", "Sistem uyarıları · 24 sa"), formatValue(health?.systemAlertsLast24h)]] },
    { name: "Open Food Facts", category: tx("Catalog source", "Katalog kaynağı"), status: tx("Fallback", "Yedek kaynak"), tone: "neutral", description: tx("Barcode lookup only when the local catalog has no matching product.", "Yalnızca yerel katalogda eşleşme olmadığında barkod sorgusu yapar."), route: "/admin/catalog", routeLabel: tx("Open catalog operations", "Katalog operasyonlarını aç"), facts: [[tx("Priority", "Öncelik"), tx("Local catalog first", "Önce yerel katalog")], [tx("Imported records", "Aktarılan kayıtlar"), tx("Review required", "İnceleme gerekli")]] },
    { name: tx("Cloud platform", "Bulut platformu"), category: tx("Infrastructure", "Altyapı"), status: tx("Planned", "Planlandı"), tone: "neutral", description: tx("Hosting, storage, backup and monitoring integration boundary.", "Barındırma, depolama, yedekleme ve izleme entegrasyon sınırı."), route: "/admin/system/production", routeLabel: tx("Open production controls", "Canlı ortam kontrollerini aç"), facts: [[tx("Secret exposure", "Gizli değer görünümü"), tx("Backend only", "Yalnızca backend")], [tx("Connection", "Bağlantı"), tx("Not active", "Etkin değil")]] }
  ];
  const ready = items.filter(item => item.tone === "good").length;
  const attention = items.filter(item => item.tone === "warn").length;
  const configured = items.filter(item => item.tone !== "neutral" || item.name === "Open Food Facts").length;

  return <div className="stack integrations-command-view">
    <SectionToolbar title={mode === "providers" ? tx("External providers", "Harici sağlayıcılar") : tx("Integration control center", "Entegrasyon kontrol merkezi")} description={tx("Review connection ownership, readiness and the correct operational destination without repeating provider analytics.", "Sağlayıcı analizlerini tekrar etmeden bağlantı sahipliğini, hazırlık durumunu ve doğru operasyon ekranını inceleyin.")} state={combineStates([healthState, revenueCatState])} onReload={reloadAll} />
    <div className="integration-summary-grid">
      <MetricCard label={tx("Configured", "Yapılandırılmış")} value={`${configured}/${items.length}`} hint={tx("Active integration boundaries", "Etkin entegrasyon sınırları")} />
      <MetricCard label={tx("Operational", "Çalışıyor")} value={String(ready)} hint={tx("No action currently required", "Şu anda işlem gerekmiyor")} />
      <MetricCard label={tx("Needs attention", "İşlem gerekli")} value={String(attention)} hint={tx("Configuration or delivery signal", "Yapılandırma veya teslimat sinyali")} />
    </div>
    <section className="integration-registry">
      <header className="integration-registry-heading"><div><span>{tx("SERVICE REGISTRY", "SERVİS LİSTESİ")}</span><h3>{tx("Connected systems", "Bağlı sistemler")}</h3></div><p>{tx("Each service appears once. Detailed events, costs and delivery records remain in their dedicated pages.", "Her servis bir kez gösterilir. Ayrıntılı olaylar, maliyetler ve teslimat kayıtları kendi sayfalarında kalır.")}</p></header>
      <div className="integration-registry-list">{items.map(item => <RegistryItem key={item.name} item={item} />)}</div>
    </section>
    <CollapsiblePanel className="integration-attention-panel" title={tx("Configuration attention", "Yapılandırma uyarıları")} description={providerIssues.length || warnings.length ? `${providerIssues.length + warnings.length} ${tx("active signal(s)", "aktif sinyal")}` : tx("No active configuration warning", "Aktif yapılandırma uyarısı yok")} open={attentionOpen} onToggle={() => setAttentionOpen(value => !value)}>
      <div className="integration-attention-list">{providerIssues.map(item => <div key={item}><strong>RevenueCat</strong><span>{item}</span></div>)}{warnings.map((item, index) => <div key={`${item}-${index}`}><strong>{tx("System", "Sistem")}</strong><span>{item}</span></div>)}{!providerIssues.length && !warnings.length && <p>{tx("All reported integration checks are clear.", "Bildirilen tüm entegrasyon kontrolleri temiz.")}</p>}</div>
    </CollapsiblePanel>
  </div>;
}

function RegistryItem({ item }: { item: Item }) {
  return <article className={`integration-registry-item ${item.tone}`}>
    <div className="integration-identity"><i aria-hidden="true" /><div><span>{item.category}</span><h3>{item.name}</h3><p>{item.description}</p></div></div>
    <div className="integration-facts">{item.facts.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>
    <div className="integration-destination"><span className={`status-pill integration-status-${item.tone}`}>{item.status}</span><a className="ghost-button" href={item.route}>{item.routeLabel} <span aria-hidden="true">→</span></a></div>
  </article>;
}
