import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const outputDir = path.resolve("outputs", "todo-status-2026-09-01");
const outputPath = path.join(outputDir, "TODO_List_updated.xlsx");
const previewPath = path.join(outputDir, "TODO_List_updated_preview.png");
const preferredInputPath = "C:/Users/emrah/Downloads/TODO_List.xlsx";
const fallbackInputPath = path.resolve("outputs", "TODO_List_updated.xlsx");
const sheetName = "Yapilacaklar Listesi";

const rows = [
  [null, "Yapilacaklar Listesi", null, null, null, null, null],
  [null, "Proje Modulleri", "Yapilacaklar/Gelistirilecekler", "Aciklama", "TAMAMLANMA YUZDESI", "TAMAMLANDI MI?", "NOTLAR"],
  [null, "Backend Core & Hardening", "Security, transaction, migration, scale ve hata yonetimi", "Flyway, validation, Open-In-View kapatma, read-only transaction cleanup, account login protection, timezone modeli, optimistic locking, global error handling, Redis opsiyonlu rate limit ve production guard katmanlari kuruldu. Son migration ve test seviyesi artik sabit bir numaraya baglanmadan CI/production gate uzerinden izleniyor.", 0.97, "Kismi", "Core backend kodu buyuk olcude kapandi. Kalanlar AWS staging/prod smoke, gercek provider/store testleri, production load testi ve CloudWatch alarm dogrulamalaridir."],
  [null, "Food ve Exercise Modulleri", "Yiyecek ve egzersiz giris/analiz altyapisi", "Food log, portion/serving, custom food, favorites, recent meals ve meal templates; exercise duration/reps/sets/weight/distance modeli, measurement restriction, AI exercise catalog resolution ve plan egzersizini gunluge kaydetme akislari backend ve mobilde hazir.", 0.97, "Kismi", "Kalanlar genis ve lisansli food/exercise katalogu, Technique Library kontrat birlestirmesi, gercek cihaz akislari ve production-equivalent buyuk veri testidir."],
  [null, "Meal Templates / Quick Reuse", "Kayitli ogun sablonu ve tekrar kullanma", "Template mevcut logdan veya dogrudan secilen urun item listesinden olusturulabilir. Template/item response kalori ve makro toplamlarini dondurur. Apply endpoint hedef gune ogun ekler; diary event ve analytics revision sayesinde dashboard/diary ozeti yenilenir.", 0.98, "Kismi", "Backend apply ve cache invalidation hatasi kapatildi. Kalan is gercek mobil create/apply/delete UX ve saved meal template detay ekran QA'sidir."],
  [null, "Serving / Portion Model", "Urun bazli porsiyon secenekleri ve dogru hesaplama", "GRAM, MILLILITER, SERVING, PIECE ana log birimleri; urun response icinde allowedPortionUnits/defaultPortionUnit; product-specific serving options endpointi; servingOptionId ile slice/cup/bottle/package gibi seceneklerin grama cevrilmesi hazir.", 0.86, "Kismi", "Admin tarafinda serving option kalite yonetimi ve gercek katalog icin dogru option importu buyuk olcekte tamamlanmadi."],
  [null, "Recipe Builder", "Custom recipe, public recipe, recipe log, rating/favorite/save ve admin review", "Recipe CRUD, nutrition calculation, recipe log, diary entegrasyonu, rating/favorite/save, public/community recipe library, kategori/allergen filtreleri, admin recipe create/review/archive, image upload ve image moderation metadata UI + backend olarak tamamlandi.", 1, "Tamamlandi", "Recipe MVP akisi backend ve UI tarafinda istenen seviyeye cekildi. Production S3/CDN image storage, gercek visual moderation provider ve genis public seed recipe katalogu ileriki production hardening kapsaminda takip edilecek."],
  [null, "Water Tracking", "Su takibi, gunluk summary, hedef ve reminder tercihleri", "Water log, gecmise donuk kayit, gelecek tarih engeli, user-specific daily water goal GET/PUT endpointleri, daily hydration summary, reminder settings, account-level hydration notification preference ve push delivery baglantisi MVP seviyesinde hazir.", 1, "Tamamlandi", "Water MVP tamamlandi. Gercek push provider smoke ve mobil permission/token lifecycle production QA kapsaminda takip edilecek."],
  [null, "Step Tracking", "Device/health step sync, gunluk hedef, trend ve reminder", "Health provider steps verisi uzerinden daily/range summary, step goal, manual step log, dashboard stepSummary, reminder scheduler, achievement metric ve push delivery baglantilari hazir.", 0.88, "Kismi", "Mobil cihazdan gercek HealthKit/Health Connect step sync testi, chart UI ve gercek provider smoke testi eksik."],
  [null, "Push Notification Provider", "Provider bagimsiz push token ve delivery altyapisi", "User push token register/revoke/list endpointleri, delivery log, LOG/Expo/FCM/OneSignal provider clientlari, invalid-token cleanup, admin monitoring ve prod config guard hazir.", 0.9, "Kismi", "FCM service-account/OAuth karari, gercek provider credentialleriyle sandbox/live smoke, mobil cihaz token lifecycle testi ve store build notification QA eksik."],
  [null, "Fasting Tracking", "Intermittent fasting plan, session, summary ve reminder akisi", "Plan/session/start/finish/cancel, daily summary, streak ve timezone-aware in-app reminder altyapisi hazir.", 0.9, "Kismi", "5:2 urun davranisi netlesirse CUSTOM disinda ayri model tasarlanacak. Mobil ekran ve push provider testi eksik."],
  [null, "Advanced Fasting", "Gelismis fasting urun tasarimi ve uygulamasi", "Basic/advanced urun siniri, eligibility/onay, 24 saat guvenlik tavani, 5:2 dusuk kalorili gun modeli, versiyonlu haftalik program, lifecycle, diary conflict, tarihce duzeltme, schedule exception, reminder, reduced-day nutrition, analytics, admin governance, GDPR ve release hardening sprintleri tamamlandi.", 1, "Tamamlandi", "Kod ve kontrat kapsami kapandi; final mobil handoff mevcut. Gercek cihaz push, staging PostgreSQL/Flyway smoke, production entitlement smoke ve klinik/legal metin onayi release operasyonu olarak takip edilecek."],
  [null, "Weekly Body Measurement Check-in", "Kullanicidan haftalik vucut olcumu isteme ve ilerleme takibi", "Body measurement history ve summary backend altyapisi mevcut. Kullaniciya haftalik kilo, body fat ve tercih edilen cevre olcumlerini girmesini hatirlatan check-in akisi eklenecek.", 0.35, "Planlandi", "Hatirlatma gunu ve saati, timezone, erteleme/atlama, notification preference, son olcume gore tekrar istememe, haftalik trend ve push/in-app delivery kurallari tasarlanacak."],
  [null, "Sleep Tracking", "Uyku kaydi, sure/kalite trendi ve diger saglik metrikleriyle iliski", "Sleep domain, body measurement ve progress analytics veri temeli backend tarafinda olusturuldu; mobil kontrat ve handoff hazirlandi.", 0.72, "Kismi", "Gercek HealthKit/Health Connect uyku payload testi, manuel uyku girisi UX dogrulamasi, timezone/overnight edge-case QA ve production cihaz testi eksik."],
  [null, "AI Next Meal Consistency", "Next Meal onerilerinin kalan hedeflerle tutarliligi", "Deterministik Next Meal kalori butcesi ve recipe yonlendirme altyapisi mevcut. Onerilerin gunluk kalan kcal, makro hedefleri, girilmis ogunler, dietary preference ve allergen bilgileriyle uyumu analiz edilip guclendirilecek.", 0.55, "Kismi", "Kalan kcal/makro toleranslari, porsiyon olcekleme, uygun recipe secimi, veri eksikligi fallback'i, gun sonu davranisi ve AI recipe prefill dogrulama senaryolari test edilecek."],
  [null, "Micronutrient Progress Analytics", "Plus/Pro dashboard micro detaylari ve Pro Progress analitigi", "MICRONUTRIENT_DETAILS Plus/Pro icin gunluk consumed, target, remaining ve data-quality alanlarini acar. Ayri MICRONUTRIENT_ANALYTICS hakki yalnizca Pro icin tarih araligi, 11 nutrient trendi, hedef uyumu, onceki donem karsilastirmasi, coverage, confidence ve guvenli insight kodlari sunar.", 0.96, "Kismi", "Backend ve mobil handoff tamamlandi. Kalanlar mevcut Progress tasarimina onayli UI entegrasyonu, EN/TR metin ve accessibility QA, gercek mobil entitlement/partial-data/403 akislari ile production cihaz testidir."],
  [null, "Pro Energy Balance Analytics", "Pro kullanici icin kanonik enerji dengesi ve modellenmis kilo degisimi", "Sprint 1-6 backend kapsaminda tamamlandi: Pro entitlement guard, timezone-aware endpoint, kanonik health/profile expenditure zinciri, versioned kilo modeli, coverage, gunluk seri, meal/activity breakdown, guvenli insight kodlari, optimize range sorgulari ve OpenAPI kontrati hazir.", 0.97, "Kismi", "Backend tamamlandi. Kalanlar mobil UI entegrasyonu, EN/TR localization-accessibility QA ve gercek cihazda entitlement, timezone, partial-data ve 403 senaryolarinin production oncesi dogrulanmasidir."],
  [null, "Custom Food Serving Units", "Custom food serving size alanini gram disina genisletme", "Genel food log tarafinda gram, milliliter, serving ve piece modeli bulunuyor. Custom food olusturma ve duzenleme akisi urune uygun serving unit ve donusum bilgilerini kabul edecek sekilde genisletilecek.", 0.25, "Planlandi", "GRAM zorunlulugu kaldirilacak; MILLILITER, SERVING ve PIECE icin canonical gram/ml karsiligi, allowed units, validation, nutrition scaling, migration uyumu ve mobil kontrat ele alinacak."],
  [null, "Security Transactional Emails", "Guvenlik ve temel hesap islemlerinde kullaniciya mail bildirme", "Password reset ve email verification mail altyapisi mevcut. Kritik hesap ve guvenlik olaylari icin merkezi, denetlenebilir transactional email event sistemi kurulacak.", 0.3, "Planlandi", "Sifre degisikligi, yeni provider baglama/kaldirma, supheli veya yeni cihaz girisi, hesap kilidi/acilmasi, email degisikligi ve kritik hesap islemleri icin event matrisi; tekrar gonderim, rate limit, audit, localization ve Brevo template yonetimi tasarlanacak."],
  [null, "Test User Feedback Program", "Test kullanicilarindan uygulama ici geri bildirim toplama", "Test-user gate, sonuc/puan/metin formlari, privacy-safe cihaz ve route baglami, izinli ekran goruntusu yukleme/retry, backend kayit API'leri ve admin filtreleme/triage ekrani tamamlandi; TestFlight akisi ile baglandi.", 0.92, "Kismi", "Kod ve admin/mobil kontratlari tamamlandi. Kalanlar fiziksel cihaz kaniti, pilot kullanici operasyonu, screenshot storage/provider smoke ve canli geri bildirim esiklerinin ayarlanmasidir."],
  [null, "Market Region / Localized Food Catalog", "EU/UK_IE/TR/GLOBAL katalog ve arama onceligi", "User onboarding market region, localized search fallback, preparation state, tek urun kimliginde coklu market availability ve bolgesel arama modeli hazir. UK/IE ve EU 25k kapilari gecti; OFF tam taramasinda TR icin 1.595 strict urun bulundu.", 0.9, "Kismi", "S10/S11 mevcut onayli artifactlarla ilerleyebilir. TR 5k/25k kaynak kapasitesi S12 production importu ve final %100 onayindan once kapatilacak."],
  [null, "Food Database Bulk Import", "Open Food Facts import ve katalog buyutme", "UTF-8 guvenli CSV/TSV import pipeline, manifest/checksum, admin import, duplicate/review, kalite raporu, regional batch ve cross-market overlap korumasi hazir. S10 disposable PostgreSQL provasinda 51.746 girdi 49.705 canonical urune sifir hata ile donustu; tekrar importta tablo buyumesi olmadi ve clone restore basarili oldu.", 0.82, "Kismi", "Gercek AWS staging snapshot/import/restore ve imzali rapor bekliyor. TR icin yeni lisansli kaynak programa alindi; production DB importu yapilmadi."],
  [null, "TR Licensed Catalog Source Program", "TR branded katalogu strict 5k pilot ve 25k gate seviyesine cikarma", "Internet kaynak/lisans registry, OFF tam kapasite taramasi, strict-review-quarantine ayrimi ve 3.807 satirlik ikinci-kaynak kanit kuyrugu hazir. OFF 1.595 strict urun sagliyor. GS1 Turkiye/TOBBsenkron GDSN, lisansli retailer/uretici feedleri, TurKomp kosullari ve review-first kullanici katkisi sonraki kaynak fazidir.", 0.3, "Kismi", "5k icin kuyruktan en az 3.405 urun yetkili ikinci kaynakla dogrulanmali. Tum kuyruk onaylansa bile 25k icin 19.598 ek strict urun gerekir. Izinsiz retailer scraping, prefix-only market karari ve AI-only nutrition kabul edilmeyecek."],
  [null, "Food Product Review Export/Import", "Filtrelenmis urunleri disa aktar, duzelt, dry-run ve verified import", "Admin review filter export CSV, nutrition correction import, dry-run, markVerified=true ile duzeltilen kayitlari verified yapma ve query/filter destekleri hazir.", 0.82, "Kismi", "Admin UI uzerinden toplu operasyon QA, buyuk CSV limit/performance testi ve AI destekli toplu dogrulama workflow'u henuz canli denenmedi."],
  [null, "Food Product Admin Review", "Katalog kalite, gorsel, audit, duplicate ve issue yonetimi", "Admin review queue, status/image review, audit history, quality issue, duplicate group/merge, quality suggestions ve product detail endpointleri hazir.", 0.92, "Kismi", "Final admin UX, bulk curation workflow, AI destekli display name/alias onerileri ve buyuk katalog operasyon testleri eksik."],
  [null, "Unified Product Intake & Review", "Mobil katkidan guvenli katalog kararina tek inceleme akisi", "Mobil urun katkisi, legacy submission birlestirme, admin review workbench, private evidence storage, atomik karar/apply, accepted evidence, high-impact confirmation, concurrency kontrolu, dogfood/staged rollout telemetry, pilot gate ve native OCR kalite dogrulamasi tamamlandi.", 0.95, "Kismi", "Kod sprintleri tamamlandi. Kalanlar gercek S3 credential smoke, staging rollout, native cihaz OCR pilotu, operasyon esiklerinin canli veride ayarlanmasi ve production rollout onayidir."],
  [null, "Product Search / Cache / Scale", "Buyuk katalogda arama ve barcode performansi", "Product search pagination/index, barcode lookup cache, Redis opsiyonlu cache stratejisi, PostgreSQL bounded candidate secimi ve trigram indexleri hazir. S11 25k/50k/100k/200k kapilari gecti; p95 sirasiyla 53/169/218/210 ms, concurrent batch 501 ms ve EXPLAIN index kullanimi basarili.", 0.94, "Kismi", "AWS staging uzerinde production-equivalent tekrar kosusu ve sonrasinda gercek trafik telemetry tuning kaldi. Staging minimum db.t4g.small, ilk production onerisi db.t4g.medium; scale triggerlari S11 raporunda."],
  [null, "AI Product Catalog Assistant", "Display name, alias ve kalite onerilerini AI ile admin review akisina hazirlama", "AI karar verici olmayacak; problemli urunler icin displayName, shortDisplayName, TR/EN alias, kategori, preparation state ve duplicate/quality issue onerileri uretecek. Admin kabul/duzenle/reddet akisi ve audit ile uygulanacak.", 0, "Planlandi", "Admin dashboard chatinde tasarlanacak. Ilk faz sadece problemli/review gereken urunlerde calisacak; toplu otomatik update yok."],
  [null, "Meal Planner", "Haftalik yemek plani, aktif plan ve diary tracking", "Meal plan CRUD, duplicate/copy, active/today lifecycle, planned-versus-consumed logging, skip/replace, snapshot nutrition scaling ve food/recipe/AI snapshot item destegi hazir. Mobil AI nutrition plan duzenlemeleri tamamlandi.", 0.97, "Kismi", "Production cihaz QA ve gercek kullanici pilotu release dogrulamasi olarak kaldi."],
  [null, "Grocery List", "Meal plan kaynakli kalici ve ucretli alisveris listesi", "Ayri GROCERY_LIST entitlement, FREE kapali/PLUS-PRO acik guvenlik, kalici liste ve checklist domaini, manuel urun, kategori, miktar override, exclude, meal-plan refresh/merge, API, audit, observability ve mobil handoff sprintleri tamamlandi.", 0.96, "Kismi", "Backend MVP ve docs/FRONTEND_HANDOFF_GROCERY_LIST_MVP_2026-07-29.md tamamlandi. Kalanlar mobil ekran entegrasyonu, gercek entitlement E2E, production telemetry ve kullanici pilotudur."],
  [null, "Coklu Dil Destegi (i18n)", "TR/ENG hata ve mesaj altyapisi", "Temel i18n ve Accept-Language hata kategorileri var.", 0.76, "Kismi", "Yeni moduller, Swagger aciklamalari ve mobil gorunen metinler periyodik kontrol istiyor."],
  [null, "Mail Bildirimleri", "Password reset, email verification ve provider abstraction", "Password reset, email verification, token hash, resend cooldown, failed delivery admin alert ve configurable provider altyapisi hazir.", 0.92, "Kismi", "Final domain reputation, SPF/DKIM/DMARC production kontrolu ve periyodik deliverability izleme eksik."],
  [null, "Brevo Production Mail Setup", "Brevo transactional email ve admin monitoring", "Brevo mail gonderimi manuel Swagger/prod smoke ile dogrulandi; sender monitoring/admin ekranlari eklendi.", 0.9, "Kismi", "Canli domain/mail reputation izleme ve production alarm esikleri netlesecek."],
  [null, "Abonelik ve Promosyon Sistemi", "Free/Plus/Pro, entitlement, AI quota ve add-on quota", "Plan feature matrix, entitlement snapshot, AI kota, tek seferlik add-on quota, concurrency guard ve admin yonetim altyapisi hazir.", 0.88, "Kismi", "Fiyat/urun kararlari, store product mapping final ve mobil satin alma testi eksik."],
  [null, "RevenueCat Payment Integration", "Store subscription webhook, config ve monitoring", "RevenueCat config, product mapping, webhook, strict mapping, smoke script, sandbox/production monitoring ekranlari ve chart altyapisi hazir.", 0.87, "Kismi", "Gercek App Store / Google Play sandbox purchase/refund/cancel/renew testleri mobil build ile yapilacak."],
  [null, "AI Voice & Photo Meal Logging", "Sesli/fotografli meal draft ve quota guardrail", "Provider-agnostic AI draft, review-first snapshot confirmation, quota lock, rate limit, request history, safe media reference contract, telemetry, safety guard, admin monitoring, GDPR ve OpenAI canli smoke tamamlandi. UI uyarlamalari uygulandi.", 0.99, "Kismi", "Yalnizca production cihaz/media QA ve maliyet-alarm tuning release asamasinda kaldi."],
  [null, "AI Recipe Generation", "AI ile detayli recipe draft ve preparation guide uretimi", "AI recipe draft, macro/micro degerler, cooking steps, review-first akisi ve ayri kotali preparation guide backend/UI kontrati tamamlandi.", 0.96, "Kismi", "Production prompt kalite tuning, gercek image moderation provider ve cihaz QA kaldi."],
  [null, "Admin Paneli ve Analiz Ekranlari", "Operasyonel admin UI ve monitoring", "Customer 360, growth/engagement, promotions, campaigns, katalog/recipe, subscription, RevenueCat, AI economics, runtime/system reliability ekranlarina ek olarak gercek TOTP MFA, maker-checker onaylari, GDPR request queue, provider verification ledger, notification governance, meal reminder ve free-promotion operasyonlari eklendi.", 0.99, "Kismi", "Admin roadmap kod kapsami buyuk olcude kapandi. Kalanlar Chrome disi browser/rol E2E matrisi, buyuk dataset-accessibility regresyonu ve dis provider/production kanitlarinin ledger'da tamamlanmasidir."],
  [null, "Mobile API Contract", "Mobil uygulama icin backend sozlesmesi", "Auth, startup, onboarding, food/water/fasting, recipe, subscription, region, health, AI draft, meal planner, target micros, notification preference, serving options, product detail ve template kontratlari guncel.", 0.97, "Kismi", "Gercek mobil entegrasyon, native health payload testi, push provider ve store purchase flow testleri eksik."],
  [null, "Google ve Apple ile Login/Register", "Federated auth ve guvenli identity linking", "Google/Apple token verification, account linking/unlink ve password setup akislari hazir. Link authorization JWT yerine DB'de hash saklanan, kisa omurlu, tek kullanimlik opaque token ile sertlestirildi; standart API hata kontrati eklendi.", 0.88, "Kismi", "Production client id/audience, Apple nonce native flow ve canli mobil provider-linking testi eksik."],
  [null, "Onboarding ve Kullanici Hedef Akisi", "Register/login sonrasi kalici, devam ettirilebilir profil ve hedef kurulumu", "Kalici/resumable onboarding; profil, bolge, dil, timezone, birim, aktivite, beslenme tercihleri, diger cinsiyet secenegi, hedef kilo ve haftalik degisim hizi akislariyla genisletildi. Hedef preview/save validasyonu lokalize edildi ve staging deploy saglik kontrolu gecti.", 0.98, "Kismi", "Kalanlar authenticated TestFlight uzerinden normal/kisisel hedef replacement, yarim-kalma/resume, accessibility ve cihaz klavye/ondalik giris E2E kanitidir."],
  [null, "BMI ve Body Fat", "Profil metriklerinden BMI/body fat hesaplama", "Backend hesaplama ve profile response destegi var.", 0.9, "Kismi", "Mobil anlik feedback, bel/boyun/kalca gibi body-fat input UX ve edge-case kontrol eksik."],
  [null, "Health Entegrasyonlari", "Apple Health / Health Connect backend sync", "Provider connection, batch sync, daily/range summary, delete/revoke, paid feature gating ve timezone destegi hazir.", 0.82, "Kismi", "Native iOS HealthKit, Android Health Connect ve gercek cihaz/store privacy testleri eksik."],
  [null, "Notifications", "In-app notification, tercih, orchestration ve operasyon altyapisi", "Typed notification contract, occurrence/outbox/attempt/engagement kayitlari, quiet-hours/preference/expiry/idempotency kurallari, subscription lifecycle bildirimleri, admin definition/policy/ledger ekranlari ve mobil deep-link routing tamamlandi. Water/step/basic-fasting producerleri kontrollu migration flagleriyle ortak platforma alinabilir.", 0.97, "Kismi", "Lokal implementasyon ve regresyon paketleri basarili. V240/V242 gercek PostgreSQL, iki-instance claim, provider receipt, signed-in admin, fiziksel cihaz ve OFF/DRY/PILOT/LIVE rollout kanitlari bekliyor."],
  [null, "Meal Reminder Automation", "Ogun ve gunluk tamamlama hatirlatmalarini guvenli otomatiklestirme", "MR-01-MR-08 lokal kapsaminda snapshot, karar motoru, kcal guvenligi, quiet-hours/fasting engelleri, dry-run, occurrence/outbox, idempotent delivery, interaction/conversion, admin policy-preview-ledger ve mobil routing tamamlandi. Canli gonderim varsayilan olarak kapali.", 0.86, "Kismi", "Release NOT READY: gercek PostgreSQL multi-instance, signed-in admin, provider receipt/failure drill, fiziksel cihaz cold-start/deep-link, load testi ve DRY/PILOT/LIVE gozlem kapilari bekliyor."],
  [null, "Notification Orchestration Platform", "Tum urun bildirimleri icin ortak typed occurrence/outbox/delivery katmani", "V240/V242 ile typed event/parameter kontrati, paylasilan occurrence-outbox-attempt altyapisi, RevenueCat lifecycle entegrasyonu, maker-checker admin governance, subscription mobile akislari ve staged behavior-producer migration kodu tamamlandi.", 0.9, "Kismi", "Platform OFF/NOT DEPLOYED durumunda. Meal, AI-result, campaign ve advanced fasting producerleri parity kanitiyla kademeli tasinacak; dis DB/provider/cihaz ve rollout kabul kapilari kapanacak."],
  [null, "GDPR / Legal", "Export, delete, consent ve retention", "Data export/delete, password-controlled anonymize/delete, consent history, retention policy admin API ve payment event scrub akislari var.", 0.88, "Kismi", "Final legal metinler, store declarations ve production acceptance kayitlari eksik."],
  [null, "Test Build Native Configuration Gate", "iOS-first, sonra Android internal test build native config kontrolu", "AWS staging API deploy edildi ve health 200/UP; iOS TestFlight build staging API'ye baglandi. Apple Vision OCR, auth/provider tanilari, feedback gorunurlugu, HealthKit aciklamalari, RevenueCat mapping ve onboarding stabilizasyonlari build zincirine alindi.", 0.82, "Kismi", "Kalan cihaz kapilari: authenticated TestFlight tam yolculuklari, HealthKit senkronu, RevenueCat sandbox lifecycle, push receipt/deep-link, screenshot upload ve tarihli performans/OCR kaniti; ardindan Android internal build matrisi."],
  [null, "Release Test & Quality Gate", "Backend, mobil, provider ve production oncesi zorunlu test kapisi", "Maven unit/integration testleri, admin kontrat testleri, cache/load scriptleri ve moduler QA altyapisi mevcut. Tum kritik kullanici yolculuklarini tek release karari altinda birlestiren otomatik kalite kapisi tamamlanacak.", 0.58, "Kismi", "Release icin zorunlu kalanlar: register-login-onboarding-resume E2E; food diary CRUD/template/cache E2E; RevenueCat sandbox purchase-renew-cancel-refund ve entitlement yenileme; Google/Apple linking; HealthKit/Health Connect cihaz testi; FCM token/push lifecycle; AI timeout/quota/fallback; GDPR export/delete; temiz PostgreSQL Flyway smoke; managed Redis failover ve 50k-200k katalog load testi. Basarisiz kritik senaryo release'i durdurmali."],
  [null, "Production Readiness", "Prod config, observability, scripts ve AWS hazirligi", "Prod config checklist, production gate, smoke scripts, Redis rate limit, JPA hardening, admin system health ve AWS staging runbook hazir.", 0.74, "Kismi", "AWS deploy, secret inventory, CloudWatch alarms, RDS sizing, cache topology ve full staging smoke bekliyor."],
  [null, "User Analytics Redis Cache", "Progress ve tracking analizlerinde revision tabanli cache/invalidation", "Phase 1-8 tamamlandi: revisioned cache keys, merkezi invalidation, Redis fail-open davranisi, authorization-first okuma, stampede/single-flight korumasi, integration gate, load scripti ve production release gate kuruldu.", 0.88, "Kismi", "Kod tarafi tamamlandi. Phase 9 managed Redis staging aktivasyonu, gercek load/metric/alarm dogrulamasi ve Phase 10 kontrollu production rollout/rollback AWS ortami hazir olana kadar bekliyor. UI kontrat degisikligi yok."],
  [null, "Frontend Performance & Regression", "Expo/React Native release performans olcumu ve regresyon kapisi", "Frontend kaynak/asset performans butcesi, cache request audit ve gercek cihaz test protokolu olusturuldu. Ilk statik baseline Home 144792 byte, Progress 128628 byte, Diary 48058 byte ve toplam asset 1601037 byte ile gecti.", 0.25, "Kismi", "Zorunlu kalanlar: Android/iOS release build cold-warm start p95, navigation/render profiler, FPS ve frozen-frame olcumu, 10 dongu memory testi, mutation feedback, unchanged Progress return request sayisi ve agir veri seti testi. Ilk risk: Home 6 ve Progress 3 ScrollView kullaniyor; profiler kaniti olmadan refactor yapilmayacak."],
  [null, "Frontend Mobil/Web", "Mobil app ve admin web UI", "AI nutrition/meal plan handoff kapsamindaki mobil UI ve admin monitoring duzenlemeleri tamamlandi. Food create/update/delete ve meal-template apply sonuclarinda React Query cache aninda uzlastiriliyor; ilgili gun arka planda backend ile dogrulaniyor.", 0.74, "Kismi", "Cache sonrasi gorunur 1-2 saniyelik diary/dashboard gecikmesi azaltildi ve typecheck gecti. Native login, health, push, store purchase, grocery ve gercek cihaz release QA akislari devam ediyor."],
  [null, "External App Data Import", "Rakip uygulamalardan export edilen kullanici datasini iceri alma", "Fikir olarak kabul edildi: CSV/JSON import ile gecmis kalori/makro/diary datasi aktarimi kullanici kazanimi icin degerli olabilir.", 0, "Ertelendi", "Formatlar net degil. Ilk fazda food eslestirmesi zorunlu olmayan snapshot import modeli tasarlanacak."],
  [null, "Reklam Yonetimi", "Ad placement, frequency cap ve gelir modeli", "Sadece strateji olarak konusuldu.", 0, "Ertelendi", "MVP sonrasinda ve subscription dengesi netlesince degerlendirilmeli."],
  [null, "Trainer / Gym Marketplace", "Bolgesel trainer, diyetisyen ve salon modeli", "Kullanici bolgesine gore profesyonel destek ve B2B gelir modeli fikri kayitli.", 0, "Uzun Vade", "MVP disi. Professional verification, legal/privacy ve marketplace payment ayri tasarlanacak."],
  [null, "AI Nutrition Plan", "AI ile genel ve workout-aligned beslenme plani", "Snapshot tabanli GENERAL ve WORKOUT_ALIGNED akislar, trusted workout schedule, kalici allergen/preference profili, target consistency, diary tracking, ayri ucretli preparation guide, opsiyonel recipe link, GDPR, monitoring, UI/admin uyumu, PostgreSQL V130 ve canli OpenAI smoke tamamlandi.", 1, "Tamamlandi", "Feature implementasyonu kapandi. Production cihaz QA, gercek trafik maliyet/alarm tuning ve nihai kredi fiyatlari release operasyonu kapsaminda takip edilecek."],
  [null, "AI Workout Planner", "AI ile antrenman plani ve exercise library", "Draft/confirm/reject, owned snapshot, schedule, nutrition alignment, detayli set/rep/rest/form/safety, canonical exercise resolution/alias queue ve reviewRequired modeli hazir. Mobilde katalog eslestirme, eslesmeyen hareket secimi, gercek set-rep-sure-agirlik girisi, duplicate korumasi ve exercise log'a toplu kayit eklendi.", 0.97, "Kismi", "Kalanlar genis ve dogrulanmis technique/media katalogu, unmatched-resolution operasyon pilotu, gercek cihaz QA, provider kalite/maliyet tuning ve uzun sureli plan kullanimi telemetrisidir."],
  [null, "Exercise Technique Library", "Spor salonu/ev hareket anlatimi ve animasyonlu gosterim", "Mobil Technique Library liste/arama/filtre ve detay ekranlari; kaslar, adimlar, nefes, form ipuclari, hatalar, guvenlik, kontrendikasyon ve tam ekran medya UX'i hazirlandi. Backend active+APPROVED public gate, admin CRUD/review/assignment/SLA, kaynak-lisans kaniti ve baslangic technique/media migrationlari mevcut.", 0.68, "Kismi", "Kritik kalan: frontend techniqueStatus/mediaStatus/environment alanlari ile guncel backend techniqueReviewStatus DTO'su birlestirilmeli; dedicated public endpoint, gercek lisansli 20-30 hareket katalogu, onayli medya/CDN, MP4 player ve cihaz E2E tamamlanmali."],
  [null, "Advanced Macro Targets", "AUTO, kontrollu ve manuel makro hedef yonetimi", "Versiyonlu hedef/history/audit modeli; AUTO-CONTROLLED-MANUAL modlari, preview/save/return-to-auto, Plus/Pro ve 18+ gate, tek kullanimli preview token, idempotency, TR/EN uyari/onay UX'i ve Dashboard/Energy Balance/Next Meal/AI Nutrition Plan entegrasyonlari backend ve mobilde tamamlandi.", 0.93, "Kismi", "Kod ve otomatik test kapsami tamamlandi. Uzman/hukuk esik-metni onayi, gercek cihaz accessibility/klavye smoke, startup/recipe/meal onerisi E2E ve kontrollu Plus/Pro rollout/rollback kaniti bekliyor."],
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

for (let rowIndex = 2; rowIndex < rows.length; rowIndex += 1) {
  const completion = Number(rows[rowIndex][4]);
  if (!Number.isFinite(completion) || completion < 0 || completion > 1) {
    throw new Error(`Invalid completion percentage at row ${rowIndex + 1}`);
  }
  rows[rowIndex][4] = completion;
}

for (const table of [...sheet.tables.items]) {
  table.delete();
}
sheet.getRange("A1:G100").clear({ applyTo: "contents" });
sheet.getRangeByIndexes(0, 0, rows.length, 7).values = rows;

const todoTable = sheet.tables.add(`B2:G${rows.length}`, true, "TodoStatusTable");
todoTable.style = "TableStyleMedium2";
todoTable.showFilterButton = true;

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
const completionRange = sheet.getRange(`E3:E${rows.length}`);
completionRange.format.numberFormat = "0%";
completionRange.conditionalFormats.deleteAll();
completionRange.conditionalFormats.add("dataBar", {
  color: "#2563EB",
  gradient: true,
});

sheet.getRange(`F3:F${rows.length}`).dataValidation = {
  rule: {
    type: "list",
    values: ["Tamamlandi", "Kismi", "Planlandi", "Ertelendi", "Uzun Vade"],
  },
};

sheet.getRange("A:A").format.columnWidthPx = 24;
sheet.getRange("B:B").format.columnWidthPx = 230;
sheet.getRange("C:C").format.columnWidthPx = 290;
sheet.getRange("D:D").format.columnWidthPx = 360;
sheet.getRange("E:E").format.columnWidthPx = 120;
sheet.getRange("F:F").format.columnWidthPx = 130;
sheet.getRange("G:G").format.columnWidthPx = 520;
sheet.getRange("1:1").format.rowHeightPx = 34;
sheet.getRange("2:2").format.rowHeightPx = 44;
sheet.getRange(`3:${rows.length}`).format.rowHeightPx = 96;

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
