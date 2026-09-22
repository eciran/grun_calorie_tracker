import { useState } from "react";
import { useAdminLocale } from "../admin/locale";
import { CommunicationHero } from "../CommunicationsPrimitives";
import { BrevoSendersView } from "./BrevoSendersView";
import { MailEventsView } from "./MailEventsView";
import { PushDeliveryView } from "./PushDeliveryView";

type DeliveryTab = "push" | "senders" | "events";

export function DeliveryCenterView({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, turkish: string) => tr ? turkish : en;
  const [tab, setTab] = useState<DeliveryTab>("push");
  return <div className="stack communications-ops-view delivery-center-page">
    <CommunicationHero eyebrow={tx("Omnichannel delivery", "Çok kanallı teslimat")} title={tx("Manage push delivery, sender identities and provider events together.", "Push teslimatını, gönderici kimliklerini ve sağlayıcı olaylarını birlikte yönetin.")} description={tx("One operational workspace keeps channel health, Brevo identities and delivery evidence connected.", "Tek operasyon alanı kanal sağlığını, Brevo kimliklerini ve teslimat kanıtlarını bir arada tutar.")} />
    <div className="delivery-center-tabs" role="tablist" aria-label={tx("Delivery workspaces", "Teslimat çalışma alanları")}>
      <button className={tab === "push" ? "active" : ""} role="tab" aria-selected={tab === "push"} onClick={() => setTab("push")} type="button">{tx("Push delivery", "Push teslimatı")}</button>
      <button className={tab === "senders" ? "active" : ""} role="tab" aria-selected={tab === "senders"} onClick={() => setTab("senders")} type="button">{tx("Sender identities", "Gönderici adresleri")}</button>
      <button className={tab === "events" ? "active" : ""} role="tab" aria-selected={tab === "events"} onClick={() => setTab("events")} type="button">{tx("Mail events", "E-posta olayları")}</button>
    </div>
    <section className="delivery-center-content" role="tabpanel">
      {tab === "push" && <PushDeliveryView embedded onError={onError} />}
      {tab === "senders" && <BrevoSendersView embedded onError={onError} />}
      {tab === "events" && <MailEventsView embedded onError={onError} />}
    </section>
  </div>;
}
