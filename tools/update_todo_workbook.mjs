import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = path.resolve("outputs");
const outputPath = path.join(outputDir, "TODO_List_updated.xlsx");
const previewPath = path.join(outputDir, "TODO_List_updated_preview.png");
const preferredInputPath = "C:/Users/emrah/Downloads/TODO_List.xlsx";
const fallbackInputPath = outputPath;
const sheetName = "Yapilacaklar Listesi";

const rows = [
  [null, "Yapilacaklar Listesi", null, null, null, null, null],
  [null, "Proje Modulleri", "Yapilacaklar/Gelistirilecekler", "Aciklama", "TAMAMLANMA YUZDESI", "TAMAMLANDI MI?", "NOTLAR"],
  [null, "Backend Core & Hardening", "Security, transaction, migration, scale ve hata yonetimi", "Flyway, Open-In-View kapatma, read-only transaction cleanup, pagination/index, account login protection, optimistic locking ve global error handling guclendirildi.", 0.9, "Kismi", "Temel backend guclu. Kalan isler daha cok production ortam, full real smoke ve provider testleri."],
  [null, "Food ve Exercise Modulleri", "Yiyecek ve egzersiz giris/analiz altyapisi", "Food log, portion unit, custom food, favorites, recent meals, meal template, exercise log, source history ve dashboard summary akislari backend tarafinda hazir.", 0.92, "Kismi", "Genis ve dogrulanmis food catalog, gercek regional import, mobil UI entegrasyonu ve buyuk veri performans testi eksik."],
  [null, "Recipe Builder", "Custom recipe, recipe log, rating/favorite/save ve AI recipe temeli", "Recipe CRUD, nutrition calculation, diary log entegrasyonu, social interaction temeli, AI recipe generation altyapisi ve image moderation metadata kuruldu.", 0.9, "Kismi", "Public/community recipe library, final mobile ekran testi ve gercek visual moderation provider canli testi sonraya birakildi."],
  [null, "Water Tracking", "Su takibi, gunluk summary ve reminder tercihleri", "Water log, daily hydration summary, reminder settings ve account-level hydration notification preference hazir.", 0.9, "Kismi", "Production push provider baglantisi ve mobil notification permission flow eksik."],
  [null, "Fasting Tracking", "Intermittent fasting plan, session, summary ve reminder akisi", "Plan/session/start/finish/cancel, daily summary, streak ve timezone-aware in-app reminder altyapisi hazir.", 0.9, "Kismi", "5:2 urun davranisi netlesirse CUSTOM disinda ayri model tasarlanacak. Mobil ekran ve push provider testi eksik."],
  [null, "Market Region / Localized Food Catalog", "EU/UK_IE/TR/GLOBAL katalog ve arama onceligi", "User onboarding market region, localized search fallback ve product region modeli hazir.", 0.85, "Kismi", "Gercek EU/UK_IE/TR dataset importu ve bolgesel kalite/coverage olcumu bekliyor."],
  [null, "Food Database Bulk Import", "Open Food Facts import ve katalog buyutme", "CSV/import pipeline, admin import, kalite alanlari ve duplicate/review altyapisi hazir.", 0.35, "Kismi", "10k+ gercek regional import bilincli olarak ertelendi. Pilot dataset ile ilerleniyor."],
  [null, "Food Product Admin Review", "Katalog kalite, gorsel, audit, duplicate ve issue yonetimi", "Admin review queue, status/image review, audit history, quality issue, duplicate group/merge ve product detail endpointleri hazir.", 0.9, "Kismi", "Final admin UX, bulk curation workflow ve buyuk katalog operasyon testleri eksik."],
  [null, "Coklu Dil Destegi (i18n)", "TR/ENG hata ve mesaj altyapisi", "Temel i18n ve Accept-Language hata kategorileri var.", 0.75, "Kismi", "Yeni moduller, Swagger aciklamalari ve mobil gorunen metinler periyodik kontrol istiyor."],
  [null, "Mail Bildirimleri", "Password reset, email verification ve provider abstraction", "Password reset, email verification, token hash, resend cooldown, failed delivery admin alert ve configurable provider altyapisi hazir.", 0.92, "Kismi", "Final domain reputation, SPF/DKIM/DMARC production kontrolu ve periyodik deliverability izleme eksik."],
  [null, "Brevo Production Mail Setup", "Brevo transactional email ve admin monitoring", "Brevo mail gonderimi manuel Swagger/prod smoke ile dogrulandi; sender monitoring/admin ekranlari eklendi.", 0.9, "Kismi", "Canli domain/mail reputation izleme ve production alarm esikleri netlesecek."],
  [null, "Abonelik ve Promosyon Sistemi", "Free/Plus/Pro, entitlement, AI quota ve add-on quota", "Plan feature matrix, entitlement snapshot, AI kota, tek seferlik add-on quota, concurrency guard ve admin yonetim altyapisi hazir.", 0.88, "Kismi", "Fiyat/urun kararlari, store product mapping final ve mobil satin alma testi eksik."],
  [null, "RevenueCat Payment Integration", "Store subscription webhook, config ve monitoring", "RevenueCat config, product mapping, webhook, smoke script, sandbox/production monitoring ekranlari ve chart altyapisi hazir.", 0.86, "Kismi", "Gercek App Store / Google Play sandbox purchase/refund/cancel/renew testleri mobil build ile yapilacak."],
  [null, "AI Voice & Photo Meal Logging", "Sesli/fotografli meal draft ve quota guardrail", "Provider-agnostic AI draft, review-first confirmation, quota lock, rate limit, request history, photo reference contract, telemetry, safety guard, admin monitoring ve GDPR destegi tamamlandi.", 0.95, "Kismi", "Gercek provider/model/key secimi, provider-specific prompt/schema smoke ve mobil AI flow testi bekliyor."],
  [null, "Admin Paneli ve Analiz Ekranlari", "Operasyonel admin UI ve monitoring", "Local modern admin UI; users, food ops, product review, subscriptions, mail/Brevo, RevenueCat, health, audit ve system pages uzerinde calisiyor.", 0.82, "Kismi", "Final production admin auth/hosting, detayli financial analytics ve long-term abuse monitoring eksik."],
  [null, "Mobile API Contract", "Mobil uygulama icin backend sozlesmesi", "Auth, startup, onboarding, food/water/fasting, recipe, subscription, region, health, AI draft, notification preference ve product detail kontratlari guncel.", 0.96, "Kismi", "Gercek mobil entegrasyon, native health payload testi, push provider ve store purchase flow testleri eksik."],
  [null, "Google ve Apple ile Login/Register", "Federated auth ve identity linking", "Google/Apple token verification, account linking/unlink ve password setup akislari backend tarafinda hazir.", 0.78, "Kismi", "Production client id/audience/Apple nonce native flow ve canli mobil test eksik."],
  [null, "Onboarding ve Kullanici Hedef Akisi", "Register/login sonrasi profil, region, timezone, units ve goal kurulumu", "Tek onboarding complete endpointi profil, market region, language, timezone, unit preference ve kalori/makro goal akisini destekliyor.", 0.95, "Kismi", "Mobil ekran entegrasyonu ve canli kullanici flow testi eksik."],
  [null, "BMI ve Body Fat", "Profil metriklerinden BMI/body fat hesaplama", "Backend hesaplama ve profile response destegi var.", 0.9, "Kismi", "Mobil anlik feedback ve edge-case UX eksik."],
  [null, "Health Entegrasyonlari", "Apple Health / Health Connect backend sync", "Provider connection, batch sync, daily/range summary, delete/revoke, paid feature gating ve timezone destegi hazir.", 0.82, "Kismi", "Native iOS HealthKit, Android Health Connect ve gercek cihaz/store privacy testleri eksik."],
  [null, "Notifications", "In-app notification ve tercih altyapisi", "Notification list/read/read-all, system alerts, water/fasting in-app reminders ve account notification preferences hazir.", 0.82, "Kismi", "Push notification provider ve mobile permission/token lifecycle eksik."],
  [null, "GDPR / Legal", "Export, delete, consent ve retention", "Data export/delete, password-controlled anonymize/delete, consent history, retention policy admin API ve payment event scrub akislari var.", 0.88, "Kismi", "Final legal metinler, store declarations ve production acceptance kayitlari eksik."],
  [null, "Production Readiness", "Prod config, observability, scripts ve AWS hazirligi", "Prod config checklist, production gate, smoke scripts, Redis rate limit, JPA hardening, admin system health ve deployment notlari hazir.", 0.72, "Kismi", "AWS deploy, secret inventory, CloudWatch alarms, RDS sizing ve full staging smoke bekliyor."],
  [null, "Frontend Mobil/Web", "Mobil app ve final admin web UI", "Backend mobil kontrata hazir. Admin UI local olarak gelisti; asil mobil app ayri calisiliyor.", 0.1, "Hayir", "Mobil frontend repo/entegrasyon, native flows ve final design QA ana bekleyen alan."],
  [null, "Reklam Yonetimi", "Ad placement, frequency cap ve gelir modeli", "Sadece strateji olarak konusuldu.", 0, "Hayir", "MVP sonrasinda ve subscription dengesi netlesince degerlendirilmeli."],
  [null, "Trainer / Gym Marketplace", "Bolgesel trainer, diyetisyen ve salon modeli", "Kullanici bolgesine gore profesyonel destek ve B2B gelir modeli fikri kayitli.", 0, "Uzun Vade", "MVP disi. Professional verification, legal/privacy ve marketplace payment ayri tasarlanacak."],
  [null, "AI Workout Planner", "AI ile antrenman plani ve exercise library", "Uzun vadeli urun vizyonu olarak kayitli.", 0, "Uzun Vade", "Exercise library, safety layer, AI recommendation history ve provider maliyet modeli daha sonra tasarlanacak."],
  [null, "Exercise Technique Library", "Spor salonu/ev hareket anlatimi ve animasyonlu gosterim", "Kullanici bir egzersizi nasil yapacagini merak ettiginde bakabilecegi hareket detay sayfasi planlandi. Her hareket icin adim adim aciklama, hedef kas grubu, ekipman, zorluk, ev/salon uygunlugu, sik yapilan hatalar, guvenlik notlari ve animasyon/video/gif gosterimi desteklenecek.", 0, "Uzun Vade", "ExerciseItem modeli bu ihtiyaca gore genisletilecek. Ilk asamada admin tarafindan yonetilen dogrulanmis hareket katalogu kurulacak; AI workout planner ileride bu katalogdan referans verecek."],
];

await fs.mkdir(outputDir, { recursive: true });

const inputPath = await fs.access(preferredInputPath)
  .then(() => preferredInputPath)
  .catch(() => fallbackInputPath);
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
let sheet;
try {
  sheet = workbook.worksheets.getItem(sheetName);
} catch {
  sheet = workbook.worksheets.getItem("Yap\u0131lacaklar Listesi");
}

sheet.getRange("A1:G60").clear({ applyTo: "contents" });
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
  sheetName: sheet.name,
  range: `A1:G${rows.length}`,
  scale: 1,
  format: "png",
});
await fs.writeFile(previewPath, new Uint8Array(await preview.arrayBuffer()));

const exported = await SpreadsheetFile.exportXlsx(workbook);
await exported.save(outputPath);

console.log(JSON.stringify({ outputPath, previewPath, rows: rows.length }));
