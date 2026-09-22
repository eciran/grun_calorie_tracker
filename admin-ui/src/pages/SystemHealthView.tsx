import { CSSProperties } from "react";

import { SystemHealth } from "../types";

import { AsyncState, Panel, SectionToolbar } from "../AdminPrimitives";

import { MiniBarChart, clamp, formatDate, formatDurationMs, formatValue, percent, readNumber, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

export type SystemHealthMode = "overview" | "runtime" | "database" | "providers" | "production";

export function SystemHealthView({ mode, onError }: { mode: SystemHealthMode; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, trText: string) => tr ? trText : en;
  const { data, state, reload } = useEndpoint<SystemHealth>("/api/v1/admin/system/health", onError);
  const heapPercent = percent(readNumber(data, "heapUsedMb"), readNumber(data, "heapMaxMb"));
  const aiFailurePercent = Math.round((readNumber(data, "aiFailureRateLast24h") ?? 0) * 100);
  const aiConfirmationPercent = Math.round((readNumber(data, "aiDraftConfirmationRateLast7d") ?? 0) * 100);
  const warnings = Array.isArray(data?.warnings) ? data.warnings : [];
  const categories = data ? healthCategories(data, tr) : [];
  const visibleCategories = categories.filter((category) => {
    if (mode === "overview") return ["Application Runtime", "Memory", "Database", "Subscriptions", "AI Provider", "AI Meal Drafts"].includes(category.title);
    if (mode === "runtime") return ["Application Runtime", "Memory"].includes(category.title);
    if (mode === "database") return category.title === "Database";
    if (mode === "providers") return ["Subscriptions", "AI Provider", "AI Meal Drafts"].includes(category.title);
    return ["Alerts", "Application Runtime", "Database"].includes(category.title);
  });



  const title = {
    overview: tx("System health", "Sistem sağlığı"),
    runtime: tx("Runtime health", "Çalışma zamanı sağlığı"),
    database: tx("Database health", "Veritabanı sağlığı"),
    providers: tx("Provider health", "Sağlayıcı sağlığı"),
    production: tx("Production readiness", "Canlıya hazırlık")
  }[mode];

  return (
    <div className="stack">
      <SectionToolbar
        title={title}
        description={tx("Monitor runtime, database, provider, and production readiness signals from backend-owned health data.", "Backend tarafından sağlanan çalışma zamanı, veritabanı, sağlayıcı ve canlıya hazırlık sinyallerini izleyin.")}
        state={state}
        onReload={reload}
      />
      <AsyncState
        state={state}
        hasData={Boolean(data)}
        loadingMessage={tx("Loading system health...", "Sistem sağlığı yükleniyor...")}
        emptyMessage={tx("No system health payload was returned.", "Sistem sağlığı verisi dönmedi.")}
      />
      {data && mode === "overview" && (
        <>
          <div className="health-summary-grid">
            <HealthStatusCard label={tx("Application", "Uygulama")} value={String(data.status ?? "-")} detail={String(data.appVersion ?? data.appName ?? "-")} />
            <HealthStatusCard label={tx("Database", "Veritabanı")} value={String(data.databaseStatus ?? "-")} detail={`${formatValue(data.databaseLatencyMs)} ms ${tx("latency", "gecikme")}`} />
            <HealthStatusCard label={tx("AI provider", "AI sağlayıcısı")} value={String(data.aiProvider ?? "-")} detail={data.aiEnabled ? tx("Enabled", "Etkin") : tx("Disabled", "Devre dışı")} />
            <HealthStatusCard label={tx("Alerts 24h", "24 saatlik uyarılar")} value={formatValue(data.systemAlertsLast24h)} detail={`${warnings.length} ${tx("active warning(s)", "aktif uyarı")}`} tone={warnings.length ? "warn" : "good"} />
          </div>
          <div className="chart-grid">
            <GaugeChart label={tx("Heap usage", "Heap kullanımı")} value={heapPercent} detail={`${formatValue(data.heapUsedMb)} / ${formatValue(data.heapMaxMb)} MB`} />
            <GaugeChart label={tx("AI failure rate", "AI hata oranı")} value={aiFailurePercent} detail={`${formatValue(data.failedAiRequestsLast24h)} ${tx("failed", "hatalı")} / ${formatValue(data.aiRequestsLast24h)} ${tx("requests", "istek")}`} tone={aiFailurePercent > 5 ? "danger" : "good"} />
            <GaugeChart label={tx("AI confirmation", "AI onay oranı")} value={aiConfirmationPercent} detail={`${formatValue(data.confirmedAiDraftsLast7d)} ${tx("confirmed", "onaylı")} / ${formatValue(data.aiDraftsLast7d)} ${tx("drafts", "taslak")}`} />
            <MiniBarChart
              label={tx("RevenueCat events", "RevenueCat olayları")}
              items={[
                [tx("24h events", "24 saatlik olay"), readNumber(data, "revenueCatEventsLast24h") ?? 0],
                [tx("Failed", "Başarısız"), readNumber(data, "failedRevenueCatEvents") ?? 0],
                [tx("Active subs", "Aktif abonelik"), readNumber(data, "activeSubscriptions") ?? 0],
                [tx("AI quota done", "AI kotası dolu"), readNumber(data, "exhaustedAiQuotaSubscriptions") ?? 0]
              ]}
            />
          </div>
          {warnings.length > 0 && (
            <Panel title={tx("Warnings", "Uyarılar")}>
              <div className="warning-list">
                {warnings.map((warning, index) => <span key={`${warning}-${index}`}>{String(warning)}</span>)}
              </div>
            </Panel>
          )}
        </>
      )}
      {data && mode === "production" && (
        <Panel title={tx("Production readiness focus", "Canlıya hazırlık odağı")}>
          <div className="roadmap-strip">
            <span>Runtime: {String(data.status ?? "-")}</span>
            <span>Database: {String(data.databaseStatus ?? "-")}</span>
            <span>Warnings: {formatValue(warnings.length)}</span>
            <span>Alerts 24h: {formatValue(data.systemAlertsLast24h)}</span>
            <span>RevenueCat failed events: {formatValue(data.failedRevenueCatEvents)}</span>
          </div>
        </Panel>
      )}
      {data && visibleCategories.length > 0 && (
        <div className="health-category-grid">
          {visibleCategories.map((category) => (
            <HealthCategoryCard key={category.title} category={category} />
          ))}
        </div>
      )}
    </div>
  );
}

export function HealthStatusCard({ label, value, detail, tone = "good" }: { label: string; value: string; detail: string; tone?: "good" | "warn" }) {
  return (
    <article className={`health-status-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

export function GaugeChart({ label, value, detail, tone = "good" }: { label: string; value: number; detail: string; tone?: "good" | "danger" }) {
  const safeValue = clamp(value, 0, 100);
  return (
    <article className={`chart-card ${tone}`}>
      <div className="gauge" style={{ "--value": safeValue } as CSSProperties}>
        <strong>{safeValue}%</strong>
      </div>
      <div>
        <h3>{label}</h3>
        <p>{detail}</p>
      </div>
    </article>
  );
}

export type HealthCategory = {
  title: string;
  label?: string;
  description: string;
  tone?: "default" | "good" | "warn" | "danger";
  items: Array<[string, unknown]>;
};

export function HealthCategoryCard({ category }: { category: HealthCategory }) {
  return (
    <article className={`health-category-card ${category.tone ?? "default"}`}>
      <header>
        <div>
          <h3>{category.label ?? category.title}</h3>
          <p>{category.description}</p>
        </div>
        <span>{category.items.length}</span>
      </header>
      <div className="health-kv-list">
        {category.items.map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{formatHealthValue(value)}</strong>
          </div>
        ))}
      </div>
    </article>
  );
}

export function formatHealthValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (Array.isArray(value)) return value.length ? value.map(String).join(", ") : "-";
  if (typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>);
    if (!entries.length) return "-";
    return entries.map(([key, item]) => `${key}: ${formatValue(item)}`).join(" | ");
  }
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number" && value > 0 && value < 1) return `${Math.round(value * 100)}%`;
  return formatValue(value);
}

export function healthCategories(data: SystemHealth, tr = false): HealthCategory[] {
  const warnings = Array.isArray(data.warnings) ? data.warnings : [];
  const categories: HealthCategory[] = [
    {
      title: "Application Runtime",
      description: "Application identity, active profile, uptime, and JVM capacity.",
      tone: data.status === "UP" ? "good" : "danger",
      items: [
        ["Status", data.status],
        ["Application", data.appName],
        ["Version", data.appVersion],
        ["Profiles", data.activeProfiles],
        ["Uptime", formatDurationMs(readNumber(data, "uptimeMs"))],
        ["Processors", data.availableProcessors],
        ["Checked at", formatDate(typeof data.checkedAt === "string" ? data.checkedAt : undefined)]
      ]
    },
    {
      title: "Database",
      description: "Primary database connectivity and response latency.",
      tone: data.databaseStatus === "UP" ? "good" : "danger",
      items: [
        ["Status", data.databaseStatus],
        ["Latency", `${formatValue(data.databaseLatencyMs)} ms`]
      ]
    },
    {
      title: "Memory",
      description: "Heap usage and available runtime memory envelope.",
      tone: percent(readNumber(data, "heapUsedMb"), readNumber(data, "heapMaxMb")) > 85 ? "warn" : "good",
      items: [
        ["Heap used", `${formatValue(data.heapUsedMb)} MB`],
        ["Heap max", `${formatValue(data.heapMaxMb)} MB`],
        ["Heap usage", `${percent(readNumber(data, "heapUsedMb"), readNumber(data, "heapMaxMb"))}%`]
      ]
    },
    {
      title: "Subscriptions",
      description: "RevenueCat provider events, paid state, and quota pressure.",
      tone: readNumber(data, "failedRevenueCatEvents") ? "warn" : "good",
      items: [
        ["RevenueCat events 24h", data.revenueCatEventsLast24h],
        ["Failed events", data.failedRevenueCatEvents],
        ["Active subscriptions", data.activeSubscriptions],
        ["Exhausted AI quota", data.exhaustedAiQuotaSubscriptions]
      ]
    },
    {
      title: "AI Provider",
      description: "AI provider switch, model selection, and request reliability.",
      tone: readNumber(data, "failedAiRequestsLast24h") ? "warn" : "good",
      items: [
        ["Enabled", data.aiEnabled],
        ["Provider", data.aiProvider],
        ["Model", data.aiModel],
        ["Requests 24h", data.aiRequestsLast24h],
        ["Failed requests 24h", data.failedAiRequestsLast24h],
        ["Failure rate 24h", data.aiFailureRateLast24h]
      ]
    },
    {
      title: "AI Meal Drafts",
      description: "User review behavior and recent AI draft lifecycle quality.",
      tone: readNumber(data, "aiDraftConfirmationRateLast7d") && (readNumber(data, "aiDraftConfirmationRateLast7d") ?? 0) < 0.5 ? "warn" : "good",
      items: [
        ["Drafts 7d", data.aiDraftsLast7d],
        ["Confirmed 7d", data.confirmedAiDraftsLast7d],
        ["Rejected 7d", data.rejectedAiDraftsLast7d],
        ["Open 7d", data.openAiDraftsLast7d],
        ["Confirmation rate", data.aiDraftConfirmationRateLast7d],
        ["Rejection reasons", data.aiRejectionReasonsLast7d]
      ]
    },
    {
      title: "Alerts",
      description: "Operational warnings and system alert notification volume.",
      tone: warnings.length || readNumber(data, "systemAlertsLast24h") ? "warn" : "good",
      items: [
        ["System alerts 24h", data.systemAlertsLast24h],
        ["Warnings", warnings]
      ]
    }
  ];
  if (!tr) return categories;
  const categoryText: Record<string, [string, string]> = {
    "Application Runtime": ["Uygulama çalışma zamanı", "Uygulama kimliği, aktif profil, çalışma süresi ve JVM kapasitesi."],
    Database: ["Veritabanı", "Ana veritabanı bağlantısı ve yanıt gecikmesi."],
    Memory: ["Bellek", "Heap kullanımı ve kullanılabilir çalışma zamanı belleği."],
    Subscriptions: ["Abonelikler", "RevenueCat olayları, ücretli durum ve kota baskısı."],
    "AI Provider": ["AI sağlayıcısı", "AI sağlayıcı durumu, model seçimi ve istek güvenilirliği."],
    "AI Meal Drafts": ["AI öğün taslakları", "Kullanıcı inceleme davranışı ve yakın dönem taslak kalitesi."],
    Alerts: ["Uyarılar", "Operasyonel uyarılar ve sistem bildirimi hacmi."]
  };
  const labels: Record<string, string> = {
    Status: "Durum", Application: "Uygulama", Version: "Sürüm", Profiles: "Profiller", Uptime: "Çalışma süresi", Processors: "İşlemciler", "Checked at": "Kontrol zamanı",
    Latency: "Gecikme", "Heap used": "Kullanılan heap", "Heap max": "Azami heap", "Heap usage": "Heap kullanımı", "RevenueCat events 24h": "24 saatlik RevenueCat olayları",
    "Failed events": "Başarısız olaylar", "Active subscriptions": "Aktif abonelikler", "Exhausted AI quota": "AI kotası dolanlar", Enabled: "Etkin", Provider: "Sağlayıcı", Model: "Model",
    "Requests 24h": "24 saatlik istekler", "Failed requests 24h": "24 saatlik başarısız istekler", "Failure rate 24h": "24 saatlik hata oranı", "Drafts 7d": "7 günlük taslaklar",
    "Confirmed 7d": "7 günlük onaylanan", "Rejected 7d": "7 günlük reddedilen", "Open 7d": "7 günlük açık", "Confirmation rate": "Onay oranı", "Rejection reasons": "Ret nedenleri",
    "System alerts 24h": "24 saatlik sistem uyarıları", Warnings: "Uyarılar"
  };
  return categories.map((category) => ({
    ...category,
    label: categoryText[category.title]?.[0] ?? category.title,
    description: categoryText[category.title]?.[1] ?? category.description,
    items: category.items.map(([label, value]) => [labels[label] ?? label, value])
  }));
}
