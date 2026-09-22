import { useAdminLocale } from "../admin/locale";
import { AdminAccessProfile } from "../types";
import { AdminApprovalQueue } from "./AdminSecurityView";
import type { AdminTargetContext } from "../admin/shared";

export function ApprovalQueueView({ accessProfile, onError, targetContext, onClearTarget }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { locale } = useAdminLocale();
  return <div className="stack owner-approval-view">
    <section className="owner-approval-hero">
      <div><span>{locale === "tr" ? "KONTROLLÜ KARARLAR" : "CONTROLLED DECISIONS"}</span><h2>{locale === "tr" ? "Kritik değişiklikleri uygulanmadan önce doğrulayın." : "Verify critical changes before they are applied."}</h2><p>{locale === "tr"
        ? "Finansal ve operasyonel talepler, güncel MFA doğrulaması ve denetim kaydıyla tek akışta değerlendirilir."
        : "Financial and operational requests are reviewed with fresh MFA verification and a complete audit trail."}</p></div>
      <aside><strong>{locale === "tr" ? "Karar kontrolü" : "Decision control"}</strong><span>{locale === "tr" ? "Hedefi, gerekçeyi ve izin verilen değişiklik yükünü doğrulayın." : "Verify the target, reason and allowlisted change payload."}</span></aside>
    </section>
    <AdminApprovalQueue accessProfile={accessProfile} onError={onError} targetContext={targetContext} onClearTarget={onClearTarget} />
  </div>;
}
