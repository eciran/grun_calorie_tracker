import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "C:/Users/emrah/Downloads/TODO_List.xlsx";
const outputDir = path.resolve("outputs");
const outputPath = path.join(outputDir, "TODO_List_updated.xlsx");
const previewPath = path.join(outputDir, "TODO_List_updated_preview.png");
const sheetName = "Yapılacaklar Listesi";

const rows = [
  [null, "Yapılacaklar Listesi", null, null, null, null, null],
  [null, "Proje Modülleri", "Yapılacaklar/Geliştirilecekler", "Açıklama", "TAMAMLANMA YÜZDESİ", "TAMAMLANDI MI?", "NOTLAR"],
  [null, "Food ve Exercise Modülleri", "Yiyecek ve egzersiz giriş/analiz altyapısı", "Food log, custom food, meal template, portion unit, exercise log ve ürün arama akışları backend tarafında hazır.", 0.85, "Kısmi", "Geniş ve doğrulanmış food catalog, gerçek regional import ve mobil UI entegrasyonu eksik."],
  [null, "Market Region / Localized Food Catalog", "IRL/TR/UK bölgesel katalog ve arama önceliği", "Kullanıcı onboarding sırasında market region seçer; food ürünleri IRL/TR/UK region bilgisi taşır.", 0.75, "Kısmi", "Backend region enum, migration, onboarding, search default region, admin filter ve CSV import desteği hazır. Gerçek regional pilot import ve daha geniş katalog bekliyor."],
  [null, "Food Database Bulk Import", "Bölgesel Open Food Facts import ve katalog büyütme", "IRL/TR/UK için küçük pilot importlardan başlayıp kontrollü şekilde 500 ve sonra 10.000+ ürüne çıkma süreci.", 0.3, "Kısmi", "Convert script, admin import ve pilot CSV hazır. Gerçek OFF regional dataset importu yapılmadı."],
  [null, "Çoklu Dil Desteği (i18n)", "Tüm endpoint ve hata mesajları için TR/ENG", "API validasyon ve hata mesajlarının Türkçe/İngilizce desteklenmesi.", 0.7, "Kısmi", "Temel i18n var. Swagger açıklamaları, yeni modüller ve mobil görünen metinler için periyodik kontrol gerekli."],
  [null, "Mail Bildirimleri", "Şifre yenileme, doğrulama, bilgi e-postası için altyapı", "Şifre sıfırlama, email verification ve configurable mail provider altyapısı.", 0.85, "Kısmi", "Token tabloları, request/confirm endpointleri ve provider abstraction hazır. Production domain/API key/smoke test eksik."],
  [null, "Brevo Production Mail Setup", "Brevo transactional email production kurulumu", "Email verification ve password reset maillerinin Brevo üzerinden gerçek gönderimi.", 0.75, "Kısmi", "Brevo branch development'a merge edildi. Sender/domain doğrulama, SPF/DKIM/DMARC ve gerçek mail smoke testleri bekliyor."],
  [null, "Abonelik ve Promosyon Sistemi", "Free/Plus/Pro, AI quota, ek kota ve ödeme entegrasyonu", "Kullanıcı planı, AI kota, tek seferlik ek quota ve admin bridge akışları.", 0.75, "Kısmi", "Backend entitlement/quota/add-on quota hazır. RevenueCat/payment provider production entegrasyonu ve gerçek store receipt/webhook akışı eksik."],
  [null, "RevenueCat Payment Integration", "App Store / Google Play subscription webhook ve entitlement eşleşmesi", "Mobil subscription ödemelerinin RevenueCat ile backend subscription modeline bağlanması.", 0.7, "Kısmi", "RevenueCat branch development'a merge edildi. Product mapping, webhook secret ve sandbox purchase testi bekliyor."],
  [null, "Admin Paneli ve Analiz Ekranları", "Kullanıcı takibi, katalog review ve operasyon ekranları", "Admin user list, dashboard summary, product review queue, audit history, duplicate merge ve geçici admin UI.", 0.65, "Kısmi", "Backend admin API ve local geçici admin UI var. Final web admin panel, finansal raporlar ve detaylı metrikler eksik."],
  [null, "Mobile API Contract", "Mobil uygulama için backend sözleşmesi", "Auth, startup, onboarding, food tracking, subscription, region ve health davranışlarının mobil için sabitlenmesi.", 0.8, "Kısmi", "Mobile API contract güncel. Kontrat testleri, frontend entegrasyonu ve native health payload doğrulaması arttırılmalı."],
  [null, "Google ve Apple ile Login/Register", "Google/Apple kimlikleri ile giriş/kayıt", "Mobil social login için Google ve Apple token verification ve identity linking.", 0.7, "Kısmi", "Backend endpointleri ve verification servisleri eklendi. Production client id/audience/Apple setup ve mobil native flow testi eksik."],
  [null, "Onboarding ve Kullanıcı Hedef Akışı", "Register/login sonrası profil, region ve hedef kurulumu", "Profil, market region, hedef tipi, aktivite seviyesi ve hedef kilo tek onboarding request ile alınır; kalori/makro hedefi hesaplanır.", 0.9, "Kısmi", "POST /api/v1/onboarding/complete, startup state ve region zorunluluğu hazır. Mobil ekran entegrasyonu ve canlı kullanıcı testi eksik."],
  [null, "Vücut Kitle İndeksi (BMI) ve Vücut Yağ Oranı Hesaplama", "Profil bilgilerinden BMI/body fat hesaplama", "Boy, kilo, yaş ve cinsiyet ile BMI/body fat hesaplama ve profil response desteği.", 0.85, "Kısmi", "Backend hesaplama var. Mobil anlık feedback ve edge-case UX eksik."],
  [null, "Üçüncü Parti Health Entegrasyonları", "Apple Health, Apple Watch, Google Fit, Health Connect", "Wearable ve health platformlarından mobil izinle veri okuyup backend'e normalize metric sync etme.", 0.8, "Kısmi", "Backend provider connection, batch sync, daily/range summary, delete/revoke ve paid feature gating hazır. Native iOS HealthKit, Android Health Connect, store privacy beyanları ve canlı cihaz testi eksik."],
  [null, "Frontend (mobil/web) geliştirme", "Kullanıcı dostu mobil/web arayüz", "Mobil uygulama ekranlarının ve varsa web/admin UI'ın geliştirilmesi.", 0.05, "Hayır", "Backend mobil kontrata hazırlanıyor. Asıl mobil frontend henüz repo içinde başlamadı."],
  [null, "Reklam Yönetimi", "Standart kullanıcılara reklam ve gelir modeli", "Reklam placement, frequency cap ve kullanıcıyı rahatsız etmeyen gelir modeli.", 0, "Hayır", "Sadece strateji konuşuldu. MVP sonrasında değerlendirilmesi öneriliyor."],
  [null, "Veri Anonimleştirme & GDPR Uyumlu Altyapı", "Kullanıcı verilerinin gizliliği ve yasal uyum", "GDPR/UK GDPR/KVKK için veri silme, export, consent ve audit süreçleri.", 0.35, "Kısmi", "JWT, role ayrımı, health data delete/revoke ve bazı güvenlik kontrolleri var. Genel kullanıcı data export/delete/consent akışları eksik."],
  [null, "Production Readiness", "Prod profile, logging, secrets, deployment checklist", "Production ortamına çıkış için config, observability, secret ve deployment hazırlığı.", 0.3, "Kısmi", "Docker/local run ve Flyway var. Prod profile, structured logging, deployment secret inventory ve monitoring eksik."],
  [null, "Trainer / Gym Marketplace", "Bölgesel trainer, diyetisyen ve salon listeleme modeli", "Kullanıcının bölgesine göre profesyonel destek alabilmesi ve trainer/salonlardan ek gelir modeli.", 0, "Uzun Vade", "MVP dışı uzun vadeli fikir. Professional profile, verification, legal/privacy ve B2B payment modeli ayrıca tasarlanmalı."],
  [null, "AI Workout Planner", "AI ile kişisel antrenman planı ve egzersiz kütüphanesi", "Kullanıcı hedefi, seviyesi, ekipmanı ve geçmiş loglarına göre AI plan üretimi.", 0, "Uzun Vade", "Uzun vadeli vizyon olarak kayıtlı. Exercise library, safety layer, AI recommendation history ve provider maliyet modeli ileride tasarlanacak."],
];

await fs.mkdir(outputDir, { recursive: true });

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const sheet = workbook.worksheets.getItem(sheetName);

sheet.getRange("A1:G40").clear({ applyTo: "contents" });
sheet.getRangeByIndexes(0, 0, rows.length, 7).values = rows;

sheet.getRange("B1:G1").merge();
sheet.getRange("B1:G1").format = {
  fill: "#0F172A",
  font: { bold: true, color: "#FFFFFF", size: 16 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
sheet.getRange("B2:G2").format = {
  fill: "#1D4ED8",
  font: { bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
};
sheet.getRange(`B3:G${rows.length}`).format = {
  wrapText: true,
  verticalAlignment: "top",
};
sheet.getRange(`E3:E${rows.length}`).format.numberFormat = "0%";

sheet.getRange("A:A").format.columnWidthPx = 24;
sheet.getRange("B:B").format.columnWidthPx = 230;
sheet.getRange("C:C").format.columnWidthPx = 290;
sheet.getRange("D:D").format.columnWidthPx = 360;
sheet.getRange("E:E").format.columnWidthPx = 120;
sheet.getRange("F:F").format.columnWidthPx = 130;
sheet.getRange("G:G").format.columnWidthPx = 520;
sheet.getRange("1:1").format.rowHeightPx = 34;
sheet.getRange("2:2").format.rowHeightPx = 44;
sheet.getRange(`3:${rows.length}`).format.rowHeightPx = 72;

sheet.freezePanes.freezeRows(2);

const preview = await workbook.render({
  sheetName,
  range: `A1:G${rows.length}`,
  scale: 1,
  format: "png",
});
await fs.writeFile(previewPath, new Uint8Array(await preview.arrayBuffer()));

const exported = await SpreadsheetFile.exportXlsx(workbook);
await exported.save(outputPath);

console.log(JSON.stringify({ outputPath, previewPath, rows: rows.length }));
