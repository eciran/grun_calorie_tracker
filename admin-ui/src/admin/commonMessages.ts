import type { AdminLocale } from "./locale";

const en = {
  idle: "Not loaded", loading: "Loading", ready: "Up to date", error: "Needs attention",
  refresh: "Refresh", refreshing: "Refreshing…", show: "Show", hide: "Hide", value: "Value",
  noResults: "No results", nothingHere: "Nothing here yet", empty: "No records to display.",
  loadFailed: "This data could not be loaded. Review the error above and retry.",
  pagination: "Table pagination", pageSize: "Page size", first: "First", previous: "Previous", next: "Next", last: "Last",
  firstPage: "First page", previousPage: "Previous page", nextPage: "Next page", lastPage: "Last page",
  cancel: "Cancel", saving: "Saving…", confirmationRequired: "Confirmation required", confirmAction: "Confirm action",
  sessionTitle: "Admin session expiring", signOut: "Sign out", signInAgain: "Sign out and sign in", continueSession: "Continue session", continuing: "Continuing…",
  items: (total: number, formatted: string) => `of ${formatted} ${total === 1 ? "item" : "items"}`,
  page: (current: string, total: string) => `Page ${current} / ${total}`,
  refreshLabel: (title: string) => `Refresh ${title}`,
  idleWarning: (seconds: number) => `No recent activity. Your admin session expires in ${seconds} ${seconds === 1 ? "second" : "seconds"}. Continue to keep working.`,
  absoluteWarning: (seconds: number) => `Your maximum session duration ends in ${seconds} ${seconds === 1 ? "second" : "seconds"}. Sign in again to start a new session.`
};

export const commonMessages: Record<AdminLocale, typeof en> = {
  en,
  tr: {
    idle: "Yüklenmedi", loading: "Yükleniyor", ready: "Güncel", error: "Kontrol gerekli",
    refresh: "Yenile", refreshing: "Yenileniyor…", show: "Göster", hide: "Gizle", value: "Değer",
    noResults: "Sonuç bulunamadı", nothingHere: "Henüz kayıt yok", empty: "Gösterilecek kayıt bulunmuyor.",
    loadFailed: "Veriler yüklenemedi. Yukarıdaki hatayı kontrol edip tekrar deneyin.",
    pagination: "Tablo sayfalama", pageSize: "Sayfa başına kayıt", first: "İlk", previous: "Önceki", next: "Sonraki", last: "Son",
    firstPage: "İlk sayfa", previousPage: "Önceki sayfa", nextPage: "Sonraki sayfa", lastPage: "Son sayfa",
    cancel: "Vazgeç", saving: "Kaydediliyor…", confirmationRequired: "Onay gerekli", confirmAction: "İşlemi onayla",
    sessionTitle: "Yönetici oturumunun süresi doluyor", signOut: "Çıkış yap", signInAgain: "Çıkış yap ve yeniden giriş yap", continueSession: "Oturuma devam et", continuing: "Devam ediliyor…",
    items: (_total, formatted) => `/ toplam ${formatted} kayıt`,
    page: (current, total) => `Sayfa ${current} / ${total}`,
    refreshLabel: title => `${title} verilerini yenile`,
    idleWarning: seconds => `Yakın zamanda etkileşim algılanmadı. Yönetici oturumunuz ${seconds} saniye sonra kapanacak. Çalışmaya devam etmek için oturumu sürdürün.`,
    absoluteWarning: seconds => `Oturumunuzun azami süresi ${seconds} saniye sonra dolacak. Yeni oturum başlatmak için yeniden giriş yapın.`
  }
};

export function formatAdminNumber(value: number, locale: AdminLocale) {
  return new Intl.NumberFormat(locale === "tr" ? "tr-TR" : "en-GB").format(value);
}
