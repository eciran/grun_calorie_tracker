# GRun Calorie Tracker - Roadmap

Bu dosya projenin anlik durumunu sade sekilde takip etmek icin tutulur.
Detayli teknik gecmis `docs/PROGRESS_LOG.md` icindedir.

## Ana Hedef

GRun backend'i uzun vadede mobil uygulama olarak yayinlanabilecek, guvenilir, test edilebilir ve genisletilebilir bir Spring Boot API haline getirmek.

## Tamamlananlar

### Temel Altyapi

- Spring Boot backend mevcut proje yapisi incelendi.
- Swagger/OpenAPI aktif hale getirildi.
- Controller endpoint aciklamalari ve DTO schema bilgileri guclendirildi.
- Global exception handling iyilestirildi.
- Test profili H2 ile izole hale getirildi.
- Lokal PostgreSQL icin Docker Compose ve run/stop scriptleri eklendi.
- Local secret/config yonetimi `.env` yapisina tasindi.

### Database ve Migration

- Flyway eklendi.
- Hibernate `ddl-auto` davranisi `validate` olarak duzenlendi.
- PostgreSQL schema Flyway migration dosyalariyla yonetilir hale getirildi.
- Vitamin kolon isimlendirme problemi giderildi.
- Urun arama, review ve log performansi icin indexler eklendi.

### Food Product Catalog

- Food product search null-safe ve paginated hale getirildi.
- Barcode lookup local DB + OpenFoodFacts fallback akisi kuruldu.
- OpenFoodFacts HTTP client ve mapping eklendi.
- External urunler local DB'ye ham veri olarak cacheleniyor.
- Urun kalite/review alanlari eklendi:
  - `dataSource`
  - `verificationStatus`
  - `imageSource`
  - `imageStatus`
  - `qualityScore`
  - `reviewPriority`
  - `usageCount`
- Admin product review endpointleri eklendi.
- Product image icin external image ve curated display image ayrimi yapildi.
- Barcode normalizasyonu eklendi.
- Duplicate product analiz endpointi eklendi.
- Duplicate product merge endpointi eklendi.
- `normalized_barcode` icin unique index eklendi.

### Food Logs

- Food log ekleme akisi food product kullanim sayisini guncelliyor.
- Daily stats hesaplama tamamlandi.
- Null degerlere karsi guvenli hale getirildi.

### Exercise Logs

- External source ile exercise log ekleme tamamlandi.
- Source bazli listeleme eklendi.
- External duplicate kontrolu eklendi.

### Exercise Catalog

- `ExerciseItem` modeli genisletildi:
  - kas grubu
  - ekipman
  - zorluk seviyesi
  - aciklama
  - guvenlik notlari
  - video/thumbnail/animation URL alanlari
  - AI uygunluk bilgisi

## Devam Edenler

- Food product catalog altyapisi teknik olarak guclendirildi, ancak admin panel/UI henuz yok.
- DB'de gercek urun ve egzersiz katalog verisi henuz sistematik olarak doldurulmadi.
- Local run akisi calisiyor, ancak Docker/PostgreSQL sifre ve container state konulari net kullanim dokumani gerektiriyor.
- Yapilan degisiklikler henuz yeni bir commit olarak toparlanmadi.

## Siradaki 5 Is

1. Local setup dokumanini netlestir.
   - Docker nasil acilir.
   - `.env` nasil olmali.
   - API nasil baslatilir.
   - Swagger nereden acilir.

2. Mevcut degisiklikleri commit/push icin toparla.
   - Git status kontrolu.
   - `.env` gibi local dosyalarin commitlenmedigini dogrula.
   - Anlamli commit mesaji hazirla.

3. Food product catalog akisini manuel test et.
   - Barcode ile urun ara.
   - Local DB'ye cacheleniyor mu kontrol et.
   - Admin review endpointinde gorunuyor mu kontrol et.

4. ExerciseItem icin baslangic seed stratejisini sec.
   - Flyway SQL seed mi?
   - Sadece dev profile seed mi?
   - Ilk etapta kac hareket eklenecek?

5. Product catalog admin surecini sade sekilde planla.
   - Review listesi.
   - Image onaylama.
   - Duplicate analiz.
   - Merge aksiyonu.

## Ertelenen Buyuk Fikirler

Bu maddeler dogru fikirlerdir, ancak su an aktif sprint kapsaminda degildir.

- AI Workout Planner
- AI recommendation history
- Workout plan data model
- Exercise video/gif library
- Subscription ve premium paket modeli
- Detayli piyasa/fiyat arastirmasi
- Buyuk olcekli food product import sistemi
- Mobil app UI

## Su Anki Oncelik

Yeni ozellik eklemeden once proje kontrolunu geri kazanmak.

Pratik siralama:

1. Roadmap'i sabit tut.
2. Local setup'i problemsiz hale getir.
3. Mevcut degisiklikleri commit/push et.
4. Sonra tek bir kucuk hedef sec ve sadece ona odaklan.
