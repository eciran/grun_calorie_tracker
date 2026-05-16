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
- Product catalog admin flow dokumani eklendi.
- Food product review audit history plan dokumani eklendi.
- Food product review audit history entity, migration, repository, service ve admin listeleme endpointi eklendi.
- Duplicate merge islemi target product uzerinde audit kaydi olusturacak sekilde genisletildi.
- Food product admin review request validasyonlari genisletildi.
- Local demo seed ihtiyaci degerlendirildi ve plan dokumani eklendi.
- DB seed ve katalog veri yonetimi stratejisi dokumani eklendi.
- Food product admin review durum gecisleri icin temel validasyonlar eklendi.
- Kullanici product search sonucunda `REJECTED` urunlerin gizlenmesi saglandi.
- Barcode lookup ve external search cache akisi `REJECTED` local urunleri kullaniciya dondurmez hale getirildi.
- DB'de gercek urun ve egzersiz katalog verisi henuz sistematik olarak doldurulmadi.
- Local run akisi calisiyor, ancak Docker/PostgreSQL sifre ve container state konulari net kullanim dokumani gerektiriyor.
- Local setup dokumani README icinde netlestirildi.
- Egzersiz katalog verisi icin ilk Flyway seed stratejisi baslatildi ve lokal PostgreSQL uzerinde dogrulandi.
- ExerciseItem katalog filtreleri icin temel DB index migration'i eklendi.
- ExerciseItem katalog filtre endpointi Flyway V9 sonrasi canli API uzerinde dogrulandi.
- Dashboard daily summary endpointi icin Swagger ve temel controller/service test kapsami eklendi.
- Flyway V10 audit migration'i lokal PostgreSQL uzerinde dogrulandi.
- Admin product review Swagger ornekleri review note, duplicate merge ve audit endpoint icin guclendirildi.
- Local-only admin bootstrap yapisi eklendi; varsayilan kapali ve sadece `local` profile altinda calisir.
- Lokal API HTTP dogrulamasi sirasinda `.env` / mevcut PostgreSQL volume parola uyumsuzlugu tespit edildi; DB migration dogrulamasi tamam, API endpoint canli dogrulamasi setup duzeltmesine bagli.
- Local admin bootstrap ve admin audit endpointi canli API uzerinde dogrulandi.
- README local setup dokumani admin bootstrap ve PostgreSQL volume parola davranisi icin guncellendi.
- Stop script Windows process yetki probleminde port 8080 fallback kullanacak sekilde guclendirildi.
- Local demo seed yapisi eklendi; varsayilan kapali ve sadece `local` profile altinda calisir.
- Local demo seed ile demo standard user ve 3 verified demo food product canli API uzerinde dogrulandi.
- Local demo seed food/exercise log kapsamına genisletildi.
- Demo user daily summary akisi canli API uzerinde dogrulandi.
- Admin review kuyruğu icin raw demo product seed edildi ve canli API uzerinde dogrulandi.
- Local Swagger demo akisi dokumani eklendi.
- Admin review update ve audit history akisi raw demo product uzerinde canli API ile dogrulandi.
- Local demo cleanup script eklendi; demo user/product/log/audit verilerini temizler, admin kullaniciyi korur.
- Sonraki backend sprint secenekleri dokumante edildi.
- Mail/password reset altyapisi eklendi; tokenlar hash olarak saklanir ve local mail sender log uzerinden test edilir.
- Admin dashboard summary endpointi eklendi; kullanici sayilari ve food catalog kalite metrikleri tek endpointten izlenebilir.
- `Accept-Language` tabanli TR/ENG hata kategori cevirisi icin i18n cekirdegi eklendi.
- Uye olurken email onayi ihtiyaci uzun vadeli hesap guvenligi isleri arasina eklendi.

## Siradaki 5 Is

1. Food log ve exercise log Swagger aciklamalarini demo akisina gore tekrar kontrol et.

2. Password reset akisini lokal API uzerinde Swagger/log ile canli dogrula.
   - V11 migration uygulanir.
   - Reset request log token uretir.
   - Reset confirm yeni sifreyle login akisini dogrular.

3. Admin dashboard summary endpointini lokal admin kullanici ile canli dogrula.

4. i18n kapsaminda validation mesajlarini ve domain hata mesajlarini message key tabanli hale getir.

5. Mobil app MVP icin backend gap analizini guncelle.

6. Email verification akisini tasarla.
   - Register sonrasi kullanici `emailVerified=false` baslar.
   - Verification token hash olarak DB'de saklanir.
   - Onay maili mail sender abstraction uzerinden gider.
   - Token confirm edilmeden kritik hesap aksiyonlari sinirlanir.

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
- Email verification / uye mail onayi

## Su Anki Oncelik

Backend temel hesap ve admin kalite kontrol akisini publish hedefi icin guclendirmek.

Pratik siralama:

1. Roadmap'i sabit tut.
2. Local setup'i problemsiz hale getir.
3. Yeni endpointleri test ve Swagger ile dogrula.
4. I18n ve mobil MVP gap analizine gec.
