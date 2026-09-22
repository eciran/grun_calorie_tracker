import { createContext, ReactNode, useContext, useEffect, useState } from "react";
import { NavigationItem, SectionKey, SectionMeta } from "./navigation";

export type AdminLocale = "tr" | "en";
const messages = {
  en: { operations: "Operations", workspace: "Admin workspace", navigation: "Admin navigation", sections: "Admin sections", close: "Close navigation", open: "Open navigation", skip: "Skip to main content", signOut: "Sign out", loading: "Loading page…", access: "Loading access…", unavailable: "No accessible page", failed: "This page could not be loaded.", reload: "Reload application", search: "Find a page", noResults: "No matching pages", language: "Language", light: "Light", dark: "Dark", lightMode: "Switch to light mode", darkMode: "Switch to dark mode", restoring: "Restoring secure session…", retry: "Retry access check" },
  tr: { operations: "Operasyonlar", workspace: "Yönetim alanı", navigation: "Yönetim menüsü", sections: "Yönetim sayfaları", close: "Menüyü kapat", open: "Menüyü aç", skip: "Ana içeriğe geç", signOut: "Çıkış yap", loading: "Sayfa yükleniyor…", access: "Yetkiler yükleniyor…", unavailable: "Erişebileceğiniz sayfa bulunmuyor", failed: "Bu sayfa yüklenemedi.", reload: "Uygulamayı yeniden yükle", search: "Sayfa ara", noResults: "Eşleşen sayfa yok", language: "Dil", light: "Açık", dark: "Koyu", lightMode: "Açık temaya geç", darkMode: "Koyu temaya geç", restoring: "Güvenli oturum geri yükleniyor…", retry: "Yetki kontrolünü tekrar dene" }
};
type MessageKey = keyof typeof messages.en;
export const turkishSections: Record<SectionKey, [string, string]> = {
  errors: ["Hata merkezi", "Backend istek hataları · Owner"],
  dashboard: ["Genel bakış", "Operasyonların özeti"],
  integrations: ["Entegrasyonlar", "Servis bağlantılarının durumu"], integrationProviders: ["Entegrasyon servisleri", "Harici servisler"],
  revenueCatProduction: ["RevenueCat canlı", "Canlı API metrikleri"], revenueCatSandbox: ["RevenueCat test", "Webhook test verileri"],
  mail: ["E-posta işlemleri", "Brevo teslimat takibi"], mailInbox: ["E-posta merkezi", "Bağlı gelen kutuları"], brevoSenders: ["Gönderici adresleri", "Brevo gönderici hesapları"], mailEvents: ["E-posta olayları", "Teslimat olayları"],
  foodOps: ["Katalog özeti", "Katalog veri akışı"], foodImports: ["İçe aktarma işleri", "Toplu veri akışı"], foodRegions: ["Bölgeler", "Pazar grupları"], foodQuality: ["Kalite kuralları", "Katalog kontrolleri"],
  catalogExercises: ["Egzersiz kütüphanesi", "Teknik ve medya"],
  products: ["Ürün inceleme", "Katalog kalitesi"], productContributions: ["Kanıtlar ve tekrarlar", "Katkılar ve ürün kimliği kararları"], productDuplicates: ["Tekrarlanan ürünler", "Ürün kimliği kararları"], productImages: ["Ürün kalitesi", "Görsel, besin değeri ve reddedilenler"], productNutrition: ["Besin değeri inceleme", "Makro besin kalitesi"], productRejected: ["Reddedilen ürünler", "İnceleme arşivi"],
  recipes: ["Tarifler", "Kullanıcı tarifleri"], achievements: ["Başarımlar", "Rozet kuralları"], users: ["Kullanıcılar", "Kullanıcı hesapları"], admins: ["Yönetici ekibi", "Yönetici hesapları ve güvenlik"], approvals: ["Owner onayları", "Kritik finansal ve operasyonel kararlar"],
  subscriptions: ["Abonelikler", "Abonelik özeti"], subscriptionFeatures: ["Özellik matrisi", "Plan kuralları"], subscriptionMapping: ["Ürünler ve haklar", "Mağaza eşleştirmeleri ve snapshot politikası"], subscriptionEntitlements: ["Abonelik hakları", "Hak politikaları"], subscriptionAccess: ["Kullanıcı erişimi", "Hesaplanan erişim hakları"], subscriptionAiQuotas: ["AI kotaları", "Kredi yönetimi"], subscriptionEvents: ["Sağlayıcı olayları", "Webhook denetimi"], subscriptionNotifications: ["Hesap bildirimleri", "Abonelik yaşam döngüsü bildirimleri"], promotions: ["Promosyonlar", "Teklifler ve dönüşüm"], freePromotion: ["Ücretsiz plan teklifleri", "Gösterim sıklığı ve yayın"],
  ai: ["AI genel görünüm", "Performans, maliyet ve sonuçlar"], aiRequests: ["AI istekleri", "OCR, inceleme ve iadeler"], aiPolicy: ["AI sağlayıcı politikası", "Model, bütçe ve güvenilirlik"], settings: ["Ayarlar", "Uygulama yapılandırması"], audits: ["Denetim kayıtları", "Yönetici işlemleri"], testFeedback: ["Test geri bildirimleri", "Önizleme sürümü raporları"], retentionPolicies: ["Veri saklama politikaları", "Veri saklama kuralları"],
  notifications: ["İş Kutum", "Role göre kararlar ve incelemeler"], notificationDefinitions: ["Bildirim tanımları", "Sistem bildirim politikaları"], mealReminderAutomation: ["Öğün hatırlatmaları", "Zamanlama, önizleme ve yayın"], notificationCampaigns: ["Kampanyalar", "Toplu bildirimler"], pushDelivery: ["Teslimat merkezi", "Push, göndericiler ve e-posta olayları"], ownerAlerts: ["Owner uyarıları", "Kritik e-posta teslimatı · Owner"],
  engagement: ["Ürün analitiği", "Dönüşüm ve kullanım"], tracking: ["Kullanım takibi", "Takip özelliklerinin analizi"], trackingWater: ["Su takibi", "Su kaydı kullanımı"], trackingFasting: ["Oruç takibi", "Oturum kullanımı"], trackingSteps: ["Adım takibi", "Cihaz aktivitesi"],
  system: ["Sistem sağlığı", "Çalışma durumu"], systemRuntime: ["Uygulama süreci", "Çalışan uygulama"], systemDatabase: ["Veritabanı", "Postgres ve Flyway"], systemProviders: ["Servis sağlığı", "Harici servislerin durumu"], systemProduction: ["Canlıya hazırlık", "Üretim ortamı kontrolleri"]
};
const LocaleContext = createContext<{ locale: AdminLocale; setLocale: (locale: AdminLocale) => void }>({ locale: "en", setLocale: () => {} });
export function AdminLocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<AdminLocale>(() => {
    try { const stored = localStorage.getItem("grun.admin.locale"); if (stored === "tr" || stored === "en") return stored; } catch { /* Storage may be disabled. */ }
    return navigator.language.toLowerCase().startsWith("tr") ? "tr" : "en";
  });
  useEffect(() => {
    document.documentElement.lang = locale;
    try { localStorage.setItem("grun.admin.locale", locale); } catch { /* Keep the in-memory preference. */ }
  }, [locale]);
  return <LocaleContext.Provider value={{ locale, setLocale }}>{children}</LocaleContext.Provider>;
}
export function useAdminLocale() {
  const context = useContext(LocaleContext);
  return { ...context, t: (key: MessageKey) => messages[context.locale][key], sectionText: (section: SectionMeta): SectionMeta => context.locale === "tr" ? { ...section, label: turkishSections[section.key][0], hint: turkishSections[section.key][1] } : section };
}
const groupLabels: Partial<Record<SectionKey, [string, string]>> = {
  users: ["Kullanıcılar", "Users"], foodOps: ["Katalog", "Catalog"], subscriptions: ["Abonelikler ve gelir", "Subscriptions & revenue"], notificationDefinitions: ["İletişim", "Communications"], engagement: ["Raporlar", "Reports"], system: ["Sistem", "System"], admins: ["Yönetim", "Administration"]
};
export function localizeGroup(item: NavigationItem, locale: AdminLocale, sectionText: (section: SectionMeta) => SectionMeta): NavigationItem {
  const translated = sectionText(item);
  return { ...translated, label: item.children && groupLabels[item.key] ? groupLabels[item.key]![locale === "tr" ? 0 : 1] : translated.label, children: item.children?.map(sectionText) };
}
export function LanguageSelector() {
  const { locale, setLocale, t } = useAdminLocale();
  return <div className="admin-language" role="group" aria-label={t("language")}><button type="button" aria-pressed={locale==="tr"} className={locale==="tr"?"active":""} onClick={()=>setLocale("tr")}>TR</button><button type="button" aria-pressed={locale==="en"} className={locale==="en"?"active":""} onClick={()=>setLocale("en")}>EN</button></div>;
}
