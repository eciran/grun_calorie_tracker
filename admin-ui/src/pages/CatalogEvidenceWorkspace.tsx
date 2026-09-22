import { useState } from "react";
import { ProductIntakeView } from "../ProductIntakeView";
import type { AdminAccessProfile } from "../types";
import type { AdminTargetContext } from "../admin/shared";
import { useAdminLocale } from "../admin/locale";
import { CanonicalDuplicateWorkspace } from "./CanonicalDuplicateWorkspace";

type WorkspaceTab = "contributions" | "duplicates";

export function CatalogEvidenceWorkspace({
  initialTab = "contributions",
  accessProfile,
  onError,
  targetContext,
  onClearTarget
}: {
  initialTab?: WorkspaceTab;
  accessProfile: AdminAccessProfile | null;
  onError: (message: string | null) => void;
  targetContext?: AdminTargetContext | null;
  onClearTarget?: () => void;
}) {
  const { locale } = useAdminLocale();
  const [tab, setTab] = useState<WorkspaceTab>(initialTab);

  return <div className="stack catalog-evidence-workspace">
    <div className="catalog-evidence-tabs" role="tablist" aria-label={locale === "tr" ? "Katalog kanıt çalışma alanı" : "Catalog evidence workspace"}>
      <button type="button" role="tab" aria-selected={tab === "contributions"} className={tab === "contributions" ? "active" : ""} onClick={() => setTab("contributions")}>
        <span>{locale === "tr" ? "Etiket katkıları" : "Label contributions"}</span>
        <small>{locale === "tr" ? "OCR kanıtı ve ürün adayları" : "OCR evidence and product candidates"}</small>
      </button>
      <button type="button" role="tab" aria-selected={tab === "duplicates"} className={tab === "duplicates" ? "active" : ""} onClick={() => setTab("duplicates")}>
        <span>{locale === "tr" ? "Tekrarlanan ürünler" : "Duplicate products"}</span>
        <small>{locale === "tr" ? "Ana ürün ve kimlik kararları" : "Primary product and identity decisions"}</small>
      </button>
    </div>
    {tab === "contributions"
      ? <ProductIntakeView accessProfile={accessProfile} onError={onError} targetContext={targetContext} onClearTarget={onClearTarget} />
      : <CanonicalDuplicateWorkspace onError={onError} />}
  </div>;
}
