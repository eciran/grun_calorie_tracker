import { SectionToolbar } from "../AdminPrimitives";
import { CatalogProductFinder } from "../CatalogProductFinder";
import { useAdminLocale } from "../admin/locale";
import { useState } from "react";

export function CatalogSearchView({ onError, canManage = false }: { onError: (message: string | null) => void; canManage?: boolean }) {
  const { locale } = useAdminLocale();
  const [refreshToken, setRefreshToken] = useState(0);
  return <div className="stack catalog-search-page">
    <SectionToolbar title={locale === "tr" ? "Katalogda ürün bul" : "Find catalog product"} description={locale === "tr" ? "Tüm ürün veritabanını arayın, katalog özellikleriyle daraltın veya barkodu kamerayla okutun." : "Search the complete product database, refine by catalog attributes, or scan a barcode with the camera."} state="ready" onReload={() => setRefreshToken(value => value + 1)} />
    <section className="panel catalog-search-workspace"><div className="modern-section-heading"><div><span className="eyebrow">{locale === "tr" ? "TÜM VERİTABANI" : "FULL DATABASE"}</span><h2>{locale === "tr" ? "Tek katalog, doğrulanabilir ürün kimliği" : "One catalog, verifiable product identity"}</h2><p>{locale === "tr" ? "Arama ve filtre sonuçlarında katalog durumu, bölge, ürün kimliği ve barkod birlikte gösterilir." : "Search and filter results show catalog status, region, product ID and barcode together."}</p></div></div><CatalogProductFinder onError={onError} refreshToken={refreshToken} canManage={canManage} /></section>
  </div>;
}
