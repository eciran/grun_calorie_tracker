# GRun Calorie Tracker - Progress Log

Bu dosya proje ilerlemesini, teknik kararları ve yapılan değişiklikleri takip etmek için tutulur.
Kod ve teknik uygulama İngilizce standartlara göre yazılır; proje notları Türkçe tutulabilir.

## Çalışma Prensipleri

- Backend uzun vadeli mobil uygulama ürünü olarak tasarlanacak.
- Ana stack: Java 17, Spring Boot 3.5.x, PostgreSQL, JWT, Maven.
- Controller katmanı ince tutulacak; iş mantığı service katmanında kalacak.
- API response'larında JPA entity doğrudan dönülmeyecek, DTO kullanılacak.
- Endpoint path'leri açık talep olmadan değiştirilmeyecek.
- Kullanıcıya ait veriler authentication context üzerinden izole edilecek.
- Null gelebilecek request alanları güvenli işlenecek.
- Beklenen hatalar GlobalExceptionHandler üzerinden tutarlı response formatıyla dönülecek.
- Yeni veya düzeltilen kritik davranışlar testlerle desteklenecek.
- Kod içi yorumlar İngilizce olacak.

## Kaynak Dosyalar

- `C:\Users\emrah\Downloads\CODEX_PROJECT_CONTEXT_GRUN.txt`
- `C:\Users\emrah\Downloads\GRun_Calorie_Tracker.txt`
- `C:\Users\emrah\Downloads\TODO_List.xlsx`

## 2026-05-11 - Başlangıç Bağlamı

### Yapılanlar

- Proje bağlamını anlatan iki metin dosyası okundu.
- Excel yapılacaklar listesi incelendi.
- Projenin uzun vadeli publish edilecek mobil uygulama backend'i olarak ele alınacağı netleştirildi.
- Backend geliştirme standartları ve öncelik sırası belirlendi.
- Repo içinde kalıcı ilerleme takibi için bu dosya oluşturuldu.

### Mevcut Teknik Durum

- Auth, user profile, goal, food logs, exercise logs ve progress logs modülleri mevcut.
- Subscription, promo, notification ve device integration tarafları daha çok iskelet seviyesinde.
- Food search ve Open Food Facts entegrasyonu eksik veya geçici uygulanmış durumda.
- FoodLogs günlük istatistik metodu tamamlanmamış.
- ExerciseLogs external source metotları tamamlanmamış.
- BMI/body fat hesaplama persistence davranışı doğrulanmalı.

### Öncelikli İş Listesi

1. FoodItem search modülünü null-safe ve DTO tabanlı şekilde stabilize et.
2. Barcode lookup davranışını netleştir: lokal DB, external fallback veya temiz not-found response.
3. FoodLogs daily stats implementasyonunu tamamla ve test et.
4. ExerciseLogs external/source metotlarını tamamla.
5. BMI/body fat hesaplamasının kaydedilip kaydedilmediğini doğrula ve gerekiyorsa düzelt.
6. Exception handling, DTO kullanımı ve test kapsamını kademeli iyileştir.

### Notlar

- Öncelik backend çekirdeğini sağlamlaştırmak; frontend ve büyük ürün özellikleri daha sonra gelmeli.
- Her önemli değişiklikten sonra bu dosyaya kısa kayıt eklenmeli.

## 2026-05-11 - FoodItem Search Stabilizasyonu

### Yapılanlar

- `FoodItemServiceImpl.searchFoodItems` boş liste dönen geçici yapıdan çıkarıldı.
- `FoodItemRepository`, `JpaSpecificationExecutor` ile uyumlu hale getirildi.
- Lokal ürün araması için null-safe Specification tabanlı filtreleme eklendi.
- Arama `name` ve `barcode` alanlarında case-insensitive çalışacak şekilde düzenlendi.
- `nutriScore`, `minCalories` ve `maxCalories` filtreleri null geldiğinde query'ye eklenmeyecek şekilde güvenli hale getirildi.
- `sortBy` ve `sortOrder` için kontrollü sıralama eklendi; desteklenmeyen sort alanları `name` sıralamasına düşüyor.
- Barkod aramada ürün bulunamazsa `null` dönmek yerine `ProductNotFoundException` fırlatılıyor.
- `FoodItemController` barkod not-found davranışında `GlobalExceptionHandler` akışını kullanacak şekilde sadeleştirildi.
- `FoodItemMapper` içindeki Türkçe yorumlar İngilizceye çevrildi.

### Test ve Altyapı

- `FoodItemServiceImplTest` eklendi.
- FoodItem search, barcode found/not-found, blank barcode ve sort fallback senaryoları test edildi.
- Maven derlemesini bozan `org.mapstruct.version` değeri `1.6.3.Final` yerine `1.6.3` yapıldı.
- Testlerde gerçek PostgreSQL bağımlılığını kaldırmak için test scope H2 dependency eklendi.
- `src/test/resources/application.yml` ile testler in-memory H2 üzerinde çalışacak hale getirildi.
- Mevcut testlerdeki eksik/yanlış mock ve payload sorunları düzeltildi.
- `UserGoalServiceImpl`, somut `UserServiceImpl` yerine `UserService` interface'ine bağımlı hale getirildi.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 31 test geçti, 0 failure, 0 error.

### Sonraki Adım

- Barcode lookup için Open Food Facts fallback davranışı netleştirilecek veya temiz not-found davranışı korunarak sonraki öncelik olan FoodLogs analytics'e geçilecek.

## 2026-05-11 - Ürün Kataloğu Kalite Stratejisi

### Karar

- GRun uzun vadede kendi ürün veritabanını oluşturacak.
- Open Food Facts birincil katalog değil, dış veri kaynağı ve fallback olarak kullanılacak.
- Dış API'den gelen ürünler doğrudan "onaylı ürün" kabul edilmeyecek.
- Ürün görseli ve veri kalitesi ayrı kalite durumlarıyla takip edilecek.

### Planlanan Model Genişletmeleri

- `dataSource`: Ürünün kaynağı. Örnek: `MANUAL`, `OPEN_FOOD_FACTS`, `ADMIN_IMPORT`.
- `verificationStatus`: Ürünün katalog kalite durumu. Örnek: `RAW_IMPORTED`, `NEEDS_REVIEW`, `VERIFIED`, `REJECTED`.
- `externalImageUrl`: Dış API'den gelen ham görsel.
- `displayImageUrl`: Mobil uygulamada gösterilecek onaylı veya seçilmiş görsel.
- `imageSource`: Görselin kaynağı. Örnek: `OPEN_FOOD_FACTS`, `ADMIN_UPLOAD`, `USER_UPLOAD`, `BRAND_OFFICIAL`.
- `imageStatus`: Görsel kalite durumu. Örnek: `RAW`, `NEEDS_REVIEW`, `APPROVED`, `REJECTED`.

### Uygulama Prensibi

- Barcode lookup lokal DB'de ürün bulamazsa Open Food Facts'e fallback yapabilir.
- Open Food Facts'ten gelen ürün DB'ye cachelenirse `verificationStatus = NEEDS_REVIEW` veya `RAW_IMPORTED` olarak kaydedilmeli.
- Kullanıcıya gösterilecek görsel için mobil uygulama öncelikle `displayImageUrl` kullanmalı.
- `displayImageUrl` yoksa kontrollü placeholder veya geçici `externalImageUrl` fallback politikası uygulanmalı.
- Search sonuçlarında çok fazla ürünü otomatik DB'ye yazmak yerine, seçilen ürünlerin cachelenmesi tercih edilmeli.

### Gelecek İşler

- FoodItemEntity katalog kalite alanlarıyla genişletilecek.
- Gerekirse enum'lar eklenecek: `FoodDataSource`, `VerificationStatus`, `ImageSource`, `ImageStatus`.
- OpenFoodFacts mapping yapılırken kalite alanları set edilecek.
- İleride admin review API/paneli ile ürün ve görsel doğrulama akışı kurulacak.

## 2026-05-11 - FoodLogs Daily Stats Implementasyonu

### Yapılanlar

- `FoodLogsServiceImpl.getDailyStats` artık `null` dönmüyor.
- Repository'den gelen native query `Object[]` sonuçları `FoodLogDailyStatsDto` içine güvenli şekilde map ediliyor.
- Tarih alanı `java.sql.Date`, `java.sql.Timestamp`, `LocalDate`, `LocalDateTime` ve string fallback ile güvenli formatlanıyor.
- Numeric alanlar `Number` üzerinden `Double` değerine çevriliyor.
- Null aggregate değerleri `0.0` olarak dönüyor.
- `FoodLogsRepository` daily stats query'sinde `COALESCE` kullanıldı; null kalori/makro veya portion değerleri toplamı bozmayacak.

### Testler

- `FoodLogsServiceImplTest` içine daily stats başarı senaryosu eklendi.
- Null aggregate değerlerinin `0.0` döndüğü senaryo eklendi.
- Kullanıcı bulunamadığında `InvalidCredentialsException` fırlatma senaryosu eklendi.
- `FoodLogsControllerTest` içine `/api/food-logs/stats` endpoint testi eklendi.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 35 test geçti, 0 failure, 0 error.

## 2026-05-11 - Tek Run ile Lokal Ortam Başlatma

### Yapılanlar

- Lokal geliştirme için `scripts/run-local.ps1` eklendi.
- Script şu akışı otomatikleştirir:
  - Docker CLI ve Docker Desktop çalışma kontrolü.
  - PostgreSQL environment değerlerinin ayarlanması:
    - `POSTGRES_USER=postgres`
    - `POSTGRES_PASSWORD=Magellan1!`
    - `POSTGRES_DB=grun_calorie_db`
  - `src/main/resources/docker-compose.yml` ile PostgreSQL container başlatma.
  - `pg_isready` ile container içinden PostgreSQL hazır olana kadar bekleme.
  - PostgreSQL hazır olduktan sonra `.\mvnw.cmd spring-boot:run` ile API başlatma.
- IntelliJ IDEA içinde tek tık çalıştırmak için `.run/GRun Local - Docker PostgreSQL + API.run.xml` shared run configuration eklendi.

### Lokal Kullanım

- Docker Desktop açık olmalı.
- IntelliJ Run Configurations içinde `GRun Local - Docker PostgreSQL + API` seçilip çalıştırılabilir.
- Alternatif terminal komutu:
  - `.\scripts\run-local.ps1`

### Doğrulama

- PowerShell script parse kontrolü yapıldı.
- Tam run testi Docker Desktop çalışma durumuna bağlı olduğu için ayrıca lokal ortamda çalıştırılmalıdır.

## 2026-05-11 - Tek Run ile Lokal Ortam Durdurma

### Yapılanlar

- Lokal geliştirme ortamını durdurmak için `scripts/stop-local.ps1` eklendi.
- Script şu akışı otomatikleştirir:
  - Docker CLI ve Docker Desktop çalışma kontrolü.
  - Maven/Spring Boot ile çalışan GRun Java process'lerini durdurma.
  - `src/main/resources/docker-compose.yml` ile PostgreSQL container'ını durdurma.
  - PostgreSQL data volume'unu koruma.
- IntelliJ IDEA içinde tek tık durdurmak için `.run/GRun Local - Stop API + Docker PostgreSQL.run.xml` shared run configuration eklendi.

### Lokal Kullanım

- IntelliJ Run Configurations içinde `GRun Local - Stop API + Docker PostgreSQL` seçilip çalıştırılabilir.
- Alternatif terminal komutu:
  - `.\scripts\stop-local.ps1`

### Doğrulama

- `scripts/run-local.ps1` ve `scripts/stop-local.ps1` için PowerShell parse kontrolü yapıldı.

## 2026-05-11 - ExerciseItem Katalog Modelinin Genişletilmesi

### Yapılanlar

- `ExerciseItem` modeli AI workout planner ve hareket önizleme ihtiyaçlarına uygun şekilde genişletildi.
- Yeni `ExerciseDifficulty` enum'u eklendi:
  - `BEGINNER`
  - `INTERMEDIATE`
  - `ADVANCED`
- `ExerciseItemEntity` ve `ExerciseItemDto` içine şu katalog alanları eklendi:
  - `primaryMuscleGroup`
  - `secondaryMuscleGroups`
  - `equipment`
  - `difficulty`
  - `instructions`
  - `safetyNotes`
  - `thumbnailUrl`
  - `videoUrl`
  - `animationUrl`
  - `aiEligible`
  - `active`
- `ExerciseItemMapper` yeni alanları entity/dto arasında map edecek şekilde güncellendi.
- `ExerciseItemServiceImpl` create/update akışında `aiEligible` ve `active` için default `true` davranışı verecek şekilde güncellendi.
- `ExerciseItemServiceImplTest` eklendi.

### Karar

- Bu adım AI entegrasyonu değildir; sadece egzersiz kataloğunu ileride AI, video/gif önizleme ve zengin hareket kütüphanesi için hazırlar.
- Mevcut endpoint path'leri kırılmadı; var olan mobil/backend tüketicileri yeni alanları opsiyonel olarak kullanabilir.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 52 test geçti, 0 failure, 0 error.

## 2026-05-12 - Product Search OpenFoodFacts Fallback

### Yapılanlar

- Ürün arama local DB'de sonuç bulamazsa OpenFoodFacts search fallback devreye girecek şekilde güncellendi.
- External search sadece ilk sayfada (`page=0`) çalışır; sonraki sayfalar local katalog üzerinden devam eder.
- OpenFoodFacts'ten gelen search sonuçları:
  - Barcode yoksa cache edilmez.
  - Barcode varsa önce local DB'de duplicate kontrolü yapılır.
  - Local DB'de yoksa `RAW_IMPORTED`, `NEEDS_REVIEW`, `OPEN_FOOD_FACTS` statüleriyle cache edilir.
  - `qualityScore`, `reviewPriority`, `lastExternalSyncAt` değerleri set edilir.
- Search response external fallback sonucunda da aynı `FoodProductSearchPageDto` formatını korur.
- `FoodItemServiceImplTest` içine local search boşken external sonuçların cache edilip döndüğünü doğrulayan test eklendi.

### Karar

- İsimle ürün aramada kullanıcı boş sonuçla hemen karşılaşmayacak; local katalog boşsa açık kaynak fallback denenir.
- External search sonuçları ham veri olarak kabul edilir ve review sürecine dahil edilir.
- External fallback tüm sayfalarda çalıştırılmayacak; maliyet/trafik kontrolü için sadece ilk sayfada devreye girer.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 53 test geçti, 0 failure, 0 error.

## 2026-05-12 - Local Secret ve Config Güvenliği

### Yapılanlar

- `src/main/resources/application.yml` Git tarafından takip edilmiyor olduğu doğrulandı.
- Local `application.yml` env variable tabanlı hale getirildi:
  - `SPRING_DATASOURCE_URL`
  - `POSTGRES_USER`
  - `POSTGRES_PASSWORD`
  - `JWT_SECRET`
  - `JWT_EXPIRATION_MS`
  - `OPENFOODFACTS_BASE_URL`
- Gerçek secret içermeyen örnek dosyalar eklendi:
  - `.env.example`
  - `src/main/resources/application-example.yml`
- `scripts/run-local.ps1` ve `scripts/stop-local.ps1` hardcoded DB password kullanmayacak şekilde güncellendi.
- Scriptler varsa `.env` dosyasını okuyarak environment variable set eder.
- `.gitignore` içinde `.codex/`, `.env`, `*.env` ignore kalırken `.env.example` commit edilebilir hale getirildi.

### Karar

- Gerçek DB password, JWT secret ve provider key değerleri repository'ye eklenmeyecek.
- Lokal geliştirme için `.env.example` kopyalanıp `.env` oluşturulacak.
- Paylaşılan config dosyalarında gerçek secret yerine env variable placeholder kullanılacak.

### Doğrulama

- PowerShell script parse kontrolü yapıldı.
- Komut: `.\mvnw.cmd clean test`
- Sonuç: 53 test geçti, 0 failure, 0 error.

## 2026-05-12 - Product Search Pagination ve Kalite Öncelikli Sıralama

### Yapılanlar

- Kullanıcı ürün arama endpoint'i paginated hale getirildi:
  - `GET /api/products/search?q=milk&page=0&size=25`
  - Maksimum page size `100` olarak sınırlandı.
- Yeni response DTO eklendi:
  - `FoodProductSearchPageDto`
  - `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last` alanlarını döner.
- `FoodItemService` ve `FoodItemServiceImpl` paginated search destekleyecek şekilde güncellendi.
- Eski `searchFoodItems(criteria)` metodu geriye uyumlu bırakıldı ve ilk 100 sonucu dönecek şekilde yeni paginated metodu kullanır.
- Varsayılan ürün arama sıralaması kalite odaklı yapıldı:
  - `qualityScore DESC`
  - `usageCount DESC`
  - `name ASC`
- Explicit sort alanları hâlâ desteklenir:
  - `calories`, `protein`, `fat`, `carbs`, `fiber`, `sugar`, `sodium`, `nutriScore`, `qualityScore`, `usageCount`
- Büyük katalog performansı için `V5__add_product_search_indexes.sql` migration dosyası eklendi:
  - `idx_food_items_name`
  - `idx_food_items_search_quality`
  - `idx_food_items_nutri_score`

### Karar

- Mobil uygulamanın en yoğun kullanacağı ürün arama akışı artık limitsiz liste dönmeyecek.
- Varsayılan arama sonuçlarında daha kaliteli ve daha sık kullanılan ürünler öne çıkacak.
- Katalog büyüdükçe arama endpoint'i page/page çalışacak.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 52 test geçti, 0 failure, 0 error.

## 2026-05-12 - Admin Product Review Pagination ve Indexler

### Yapılanlar

- Admin ürün review endpoint'i paginated hale getirildi:
  - `GET /api/admin/products/review?page=0&size=25`
  - Maksimum page size `100` olarak sınırlandı.
- Yeni response DTO eklendi:
  - `FoodProductReviewPageDto`
  - `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last` alanlarını döner.
- `FoodProductReviewService` ve implementation pagination destekleyecek şekilde güncellendi.
- Review listesi hâlâ şu sıralamayla döner:
  - `reviewPriority DESC`
  - `usageCount DESC`
  - `id ASC`
- Büyük katalog performansı için `V4__add_product_review_indexes.sql` migration dosyası eklendi:
  - `idx_food_items_barcode`
  - `idx_food_items_review_queue`
  - `idx_food_logs_user_log_date`
  - `idx_exercise_logs_user_log_date`
- Controller ve service testleri yeni paginated response yapısına göre güncellendi.

### Karar

- Admin review ekranı artık tüm ürünü tek seferde çekmeyecek.
- Büyük ürün kataloglarında review işlemleri page/page ilerleyecek.
- Ürün arama, review queue ve log tarih filtreleri için temel index altyapısı oluşturuldu.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 52 test geçti, 0 failure, 0 error.

## 2026-05-12 - Flyway Validate Vitamin Kolon Düzeltmesi

### Problem

- Flyway geçişinden sonra uygulama run edilirken Hibernate validate aşamasında şu hata görüldü:
  - `Schema-validation: missing column [vitamina] in table [food_items]`
- Sebep, `vitaminA`, `vitaminC`, `vitaminD`, `vitaminE`, `vitaminB12` alanlarının Hibernate tarafından beklenen kolon adı ile migration dosyalarındaki okunabilir kolon adlarının farklılaşmasıydı.

### Yapılanlar

- `FoodItemEntity` içinde vitamin alanlarına explicit `@Column` isimleri verildi:
  - `vitamin_a`
  - `vitamin_c`
  - `vitamin_d`
  - `vitamin_e`
  - `vitamin_b12`
- Mevcut local DB'lerde eksik olabilecek besin kolonları için `V3__add_missing_food_nutrition_columns.sql` migration dosyası eklendi.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 52 test geçti, 0 failure, 0 error.

## 2026-05-11 - Flyway Database Migration Geçişi

### Yapılanlar

- Flyway dependency'leri projeye eklendi:
  - `flyway-core`
  - `flyway-database-postgresql`
- Lokal/prod `application.yml` içinde Hibernate schema yönetimi `ddl-auto:update` yerine `ddl-auto:validate` olarak değiştirildi.
- Flyway aktif edildi:
  - `baseline-on-migrate: true`
  - `baseline-version: 1`
  - `locations: classpath:db/migration`
- Test ortamında Flyway kapalı bırakıldı; testler H2 üzerinde `create-drop` ile izole çalışmaya devam eder.
- İlk migration dosyaları eklendi:
  - `V1__baseline_schema.sql`: Temiz PostgreSQL database için mevcut ana schema.
  - `V2__add_catalog_quality_and_preview_fields.sql`: Daha önce Hibernate ile oluşmuş local DB'ler için additive kolon/index/constraint güncellemeleri.

### Karar

- Bundan sonra entity değişikliği yapılırken karşılığı olan Flyway migration dosyası da eklenecek.
- Hibernate artık production/lokal PostgreSQL şemasını otomatik değiştirmeyecek; sadece entity ile DB uyumunu doğrulayacak.
- Mevcut local DB varsa Flyway önce baseline kaydı oluşturacak, sonra `V2` ve sonraki migration'ları çalıştıracak.
- Yeni temiz DB kurulursa `V1` ile tüm schema oluşturulacak, sonra sonraki migration'lar sırayla uygulanacak.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 52 test geçti, 0 failure, 0 error.

## 2026-05-11 - Food Product Kullanım ve Kalite Takibi

### Yapılanlar

- Ürün katalog büyümesini yönetmek için `FoodItemEntity` ve `FoodProductDto` içine kalite/kullanım alanları eklendi:
  - `usageCount`
  - `qualityScore`
  - `reviewPriority`
  - `lastExternalSyncAt`
  - `lastReviewedAt`
  - `reviewedBy`
- `FoodProductQualityRules` ortak kural sınıfı eklendi.
- OpenFoodFacts'ten import edilen ürünlerde:
  - `usageCount=0` başlatılır.
  - `lastExternalSyncAt` set edilir.
  - `qualityScore` ve `reviewPriority` hesaplanır.
- Food log ekleme akışında:
  - Ürün `usageCount` değeri artırılır.
  - Ürünün kalite ve review öncelik skorları yeniden hesaplanır.
- Admin review akışında:
  - Review listesi `reviewPriority DESC`, `usageCount DESC`, `id ASC` sıralamasıyla döner.
  - Review sonrası `lastReviewedAt` set edilir.
  - Kalite ve review öncelik skorları yeniden hesaplanır.
- Mapper ve repository metotları yeni alanları ve sıralamayı destekleyecek şekilde güncellendi.
- İlgili service testleri güncellendi.

### Karar

- Dış kaynaklardan gelen tüm ürünler eşit önemde kabul edilmeyecek.
- Kullanıcıların gerçekten log'a eklediği ürünler sistemde daha yüksek öncelik kazanacak.
- Admin review süreci, kullanım ve kalite skorlarına göre önemli ürünleri önce gösterecek.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 52 test geçti, 0 failure, 0 error.

## 2026-05-11 - ExerciseLogs External Source Akışı

### Yapılanlar

- External source üzerinden egzersiz log ekleme tamamlandı:
  - `POST /api/exercise-logs/external`
- Source'a göre egzersiz log listeleme endpointi eklendi:
  - `GET /api/exercise-logs/source/{source}`
- `source` değeri servis seviyesinde normalize ediliyor.
- `externalId` trim ediliyor fakat case bilgisi korunuyor.
- Kullanıcı + source + externalId kombinasyonu için duplicate kontrol eklendi.
- DB seviyesinde `user_id`, `source`, `external_id` için unique constraint eklendi.
- Source filtreleme için DB index eklendi.
- Duplicate external kayıtlar için `DuplicateExternalExerciseLogException` eklendi.
- Duplicate external kayıtlar global handler üzerinden HTTP 409 Conflict dönüyor.
- `ExerciseLogsDto` içine `id` alanı eklendi.
- `ExerciseLogsMapper` artık log id bilgisini doğru şekilde DTO'ya map ediyor.
- Yeni endpointler Swagger açıklamalarıyla belgelendi.

### Testler

- `ExerciseLogsServiceImplTest` eklendi:
  - External log başarı senaryosu
  - Duplicate external log senaryosu
  - Source'a göre listeleme senaryosu
- `ExerciseLogsControllerTest` eklendi:
  - External log endpoint başarı senaryosu
  - Duplicate external log için 409 senaryosu
  - Source'a göre listeleme endpoint senaryosu

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 41 test geçti, 0 failure, 0 error.

### Sonraki Adım

- Food product katalog kalite alanları için entity/enum tasarımına geçilecek: `dataSource`, `verificationStatus`, `externalImageUrl`, `displayImageUrl`, `imageSource`, `imageStatus`.

## 2026-05-11 - Food Product Katalog Kalite Modeli

### Yapılanlar

- Food product katalog kalite enum'ları eklendi:
  - `FoodDataSource`: `MANUAL`, `OPEN_FOOD_FACTS`, `ADMIN_IMPORT`
  - `VerificationStatus`: `RAW_IMPORTED`, `NEEDS_REVIEW`, `VERIFIED`, `REJECTED`
  - `ImageSource`: `OPEN_FOOD_FACTS`, `ADMIN_UPLOAD`, `USER_UPLOAD`, `BRAND_OFFICIAL`
  - `ImageStatus`: `RAW`, `NEEDS_REVIEW`, `APPROVED`, `REJECTED`
- `FoodItemEntity` kalite alanlarıyla genişletildi:
  - `dataSource`
  - `verificationStatus`
  - `externalImageUrl`
  - `displayImageUrl`
  - `imageSource`
  - `imageStatus`
- `FoodProductDto` aynı kalite alanlarıyla genişletildi ve Swagger schema açıklamaları eklendi.
- `FoodItemMapper` kalite alanlarını entity/DTO arasında map edecek şekilde güncellendi.
- `displayImageUrl` sadece curated/onaylı görsel için kullanılacak şekilde bırakıldı; external görsel otomatik bu alana yazılmıyor.
- Barkod lookup lokal DB'de ürün bulamazsa `OpenFoodFactsService` fallback akışına bağlandı.
- External source'dan gelen ürün cachelenirken şu kalite durumları set ediliyor:
  - `dataSource = OPEN_FOOD_FACTS`
  - `verificationStatus = RAW_IMPORTED`
  - `externalImageUrl = dış kaynaktan gelen görsel`
  - `displayImageUrl = null`
  - `imageSource = OPEN_FOOD_FACTS`
  - `imageStatus = NEEDS_REVIEW`
- `FoodItemServiceImplTest` içine external fallback cache senaryosu eklendi.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 42 test geçti, 0 failure, 0 error.

### Sonraki Adım

- `OpenFoodFactsServiceImpl` gerçek HTTP client ile doldurulacak.
- OpenFoodFacts response mapping'i ürün adı, barkod, makro değerler, nutri-score ve görsel alanlarını güvenli şekilde parse edecek.
- Admin/product review endpointleri tasarlanacak: ürün verisini ve `displayImageUrl` alanını onaylama/düzeltme akışı.

## 2026-05-11 - OpenFoodFacts HTTP Client ve Mapping

### Yapılanlar

- `OpenFoodFactsServiceImpl` boş stub yapıdan çıkarıldı.
- Spring `RestClient` ile OpenFoodFacts HTTP çağrıları eklendi.
- Barcode lookup endpointi bağlandı:
  - `/api/v2/product/{barcode}.json`
- Search endpointi bağlandı:
  - `/cgi/search.pl`
- OpenFoodFacts JSON response'u `JsonNode` ile güvenli parse ediliyor.
- Barcode lookup response mapping'i eklendi:
  - barcode
  - product name
  - brand
  - external image URL
  - ingredients
  - allergens
  - nutri-score
  - serving size
  - calories/protein/fat/carbs/fiber/sugar/sodium
- Search response mapping'i eklendi.
- Search criteria için brand ve nutri-score filtreleri servis seviyesinde desteklendi.
- Dış API hatalarında veya ürün bulunamadığında backend exception fırlatmak yerine boş sonuç dönüyor; `FoodItemServiceImpl` gerekli not-found kararını kendi seviyesinde veriyor.
- OpenFoodFacts'ten gelen DTO'lar katalog kalite alanlarıyla işaretleniyor:
  - `dataSource = OPEN_FOOD_FACTS`
  - `verificationStatus = RAW_IMPORTED`
  - `imageSource = OPEN_FOOD_FACTS`
  - `imageStatus = NEEDS_REVIEW`

### Testler

- `OpenFoodFactsServiceImplTest` eklendi.
- Testlerde network kullanılmadan `MockRestServiceServer` ile HTTP response simüle edildi.
- Barcode ürün bulundu senaryosu test edildi.
- Barcode ürün bulunamadı senaryosu test edildi.
- Search criteria brand/nutri-score filtreleme senaryosu test edildi.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 45 test geçti, 0 failure, 0 error.

### Sonraki Adım

- Admin/product review endpointleri tasarlanacak: imported ürünleri listeleme, ürün verisini doğrulama, `displayImageUrl` atama ve görsel/ürün status güncelleme.

## 2026-05-11 - Admin Product Review API

### Yapılanlar

- Admin-only ürün review API eklendi:
  - `GET /api/admin/products/review`
  - `PATCH /api/admin/products/{id}/review`
- Endpointler `@PreAuthorize("hasRole('ADMIN')")` ile sadece admin kullanıcıya açıldı.
- Imported/review bekleyen ürünler varsayılan olarak `RAW_IMPORTED + NEEDS_REVIEW` filtreleriyle listeleniyor.
- Listeleme endpointi opsiyonel `verificationStatus` ve `imageStatus` filtrelerini destekliyor.
- Review update endpointi şu alanları güncelleyebiliyor:
  - curated product name
  - `displayImageUrl`
  - `verificationStatus`
  - `imageSource`
  - `imageStatus`
- `FoodProductDto` içine internal `id` alanı eklendi.
- Non-admin erişimlerde 403 dönmesi için `AccessDeniedException` global handler'a eklendi.
- Admin product review endpointleri Swagger açıklamalarıyla belgelendi.

### Testler

- `FoodProductReviewServiceImplTest` eklendi:
  - default review list filtreleri
  - curated alan ve status update senaryosu
- `AdminFoodProductReviewControllerTest` eklendi:
  - admin listeleme başarı senaryosu
  - non-admin 403 senaryosu
  - admin review update başarı senaryosu

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 50 test geçti, 0 failure, 0 error.

### Sonraki Adım

- `ExerciseItem` katalog modelini AI, hareket önizleme ve zengin egzersiz kütüphanesi ihtiyaçlarına göre planlamak.

## 2026-05-11 - Uzun Vadeli AI Workout Planner Vizyonu

### Karar

- AI entegrasyonu şu an aktif geliştirme kapsamında değildir; uzun vadeli ürün planı olarak tutulacaktır.
- Mevcut backend modülleri bu fikre uygun şekilde genişletilebilir yapıda korunacaktır.
- `ExerciseItem` katalog modeli ileride AI ve hareket önizleme ihtiyaçlarına göre genişletilecektir.
- AI önerileri doğrudan kullanıcının egzersiz log'una yazılmayacaktır.
- AI tarafından üretilen programlar önce kullanıcı onayına sunulacak, onaydan sonra aktif plana dönüştürülecektir.
- Sağlık, sakatlık ve seviye güvenlik kontrolleri AI önerilerinin zorunlu parçası olacaktır.

### Gelecek Modüller

- `AI Workout Planner`: Kullanıcının hedefi, seviyesi, kilosu, ekipmanı, haftalık uygunluğu ve geçmiş loglarına göre antrenman planı üretir.
- `Workout Plan`: Haftalık planı, gün bazlı egzersizleri, set/tekrar/süre/dinlenme bilgilerini saklar.
- `Exercise Library`: Hareket açıklaması, hedef kas grubu, ekipman, zorluk seviyesi, thumbnail, video veya gif URL alanlarını tutar.
- `AI Recommendation History`: AI'ın ne önerdiğini, hangi input ile önerdiğini ve kullanıcının kabul/red durumunu saklar.
- `Safety / Review Layer`: Sakatlık, yaş, seviye ve sağlık beyanına göre riskli önerileri engeller.

### Varsayımlar

- Bu özellik hemen implemente edilmeyecek; sadece uzun vadeli plan olarak dokümana eklendi.
- İlk gerçek implementasyon zamanı geldiğinde önce `ExerciseItem` ve yeni `WorkoutPlan` veri modeli tasarlanacak.
- AI provider seçimi şimdilik yapılmayacak; OpenAI veya başka provider kararı ileride teknik gereksinim ve maliyet kriterlerine göre verilecek.

### Sonraki İlgili Adım

- Admin/product review işlerinden sonra `ExerciseItem` katalog modelinin AI uyumlu olacak şekilde nasıl genişletileceği ayrıca planlanacak.

## 2026-05-11 - Swagger UI Endpoint Açıklamaları ve DTO Şemaları

### Yapılanlar

- OpenAPI metadata konfigürasyonu eklendi:
  - API başlığı, versiyon, açıklama ve JWT bearer security scheme tanımlandı.
- Tüm controller grupları Swagger tag'leriyle ayrıldı:
  - Authentication
  - Users
  - Products
  - Food Logs
  - Exercise Logs
  - Exercise Items
  - Goals
  - Progress
- Controller metotlarına `summary`, `description` ve temel response açıklamaları eklendi.
- JWT ile gelen `AuthenticationPrincipal` parametreleri Swagger UI request formundan gizlendi.
- Request parametrelerine açıklama ve örnek değerler eklendi.
- DTO modellerine `@Schema` açıklamaları ve örnek değerler eklendi.

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 35 test geçti, 0 failure, 0 error.

### Sonraki Adım

- ExerciseLogs external/source metotları tamamlanacak: external source log ekleme, source'a göre listeleme ve duplicate `externalId` koruması.

## 2026-05-11 - Swagger/OpenAPI Lokal Dokümantasyon

### Yapılanlar

- Springdoc OpenAPI Swagger UI dependency'si eklendi.
- Spring Boot 3.5.0 ile uyumlu olması için `springdoc-openapi-starter-webmvc-ui` sürümü `2.8.9` olarak sabitlendi.
- Swagger endpointleri Spring Security içinde public erişime açıldı:
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/swagger-ui.html`
- Test profilini bozmadan Swagger'ın Spring context içinde açılabildiği doğrulandı.

### Lokal Kullanım

- PostgreSQL Docker container çalışır durumda olmalı.
- Uygulama `.\mvnw.cmd spring-boot:run` ile başlatıldığında Swagger UI şu adresten görülebilir:
  - `http://localhost:8080/swagger-ui/index.html`

### Doğrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuç: 35 test geçti, 0 failure, 0 error.

## 2026-05-12 - Food Product Barcode Normalizasyonu

### Yapilanlar

- Food product duplicate riskini azaltmak icin `normalizedBarcode` alani eklendi.
- `FoodProductNormalizationRules` destek sinifi eklendi:
  - Barcode trim edilir.
  - Bosluk ve tire karakterleri temizlenir.
  - Alfanumerik barkodlar buyuk harfe normalize edilir.
- Barcode lookup artik once `normalizedBarcode` uzerinden calisir.
- Eski kayitlarla uyumluluk icin `barcode` alanina fallback lookup korunur.
- Eski kayit fallback ile bulunursa `normalizedBarcode` eksikse otomatik backfill edilir.
- OpenFoodFacts barcode fallback ve search cache akislari normalize barkodla duplicate kontrolu yapacak sekilde guncellendi.
- Product search query artik `name`, `barcode` ve `normalizedBarcode` alanlarini birlikte tarar.
- `FoodProductDto` ve mapper yapisi `normalizedBarcode` alanini destekleyecek sekilde guncellendi.
- PostgreSQL icin `V6__add_normalized_barcode.sql` migration dosyasi eklendi:
  - `normalized_barcode` kolonu
  - Mevcut barcode degerleri icin backfill
  - `idx_food_items_normalized_barcode` index'i

### Karar

- `barcode` alani ham/urun kaynagi degeri olarak korunabilir.
- Lookup, duplicate kontrolu ve indexleme icin `normalizedBarcode` esas alinacak.
- Su asamada unique constraint eklenmedi; once mevcut data icinde duplicate analizi yapilmasi daha guvenli.

### Dogrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuc: 54 test gecti, 0 failure, 0 error.

## 2026-05-12 - Food Product Duplicate Analiz Endpointi

### Yapilanlar

- Admin urun review modulu icine duplicate analiz endpointi eklendi:
  - `GET /api/admin/products/duplicates?page=0&size=25`
- Ayni `normalizedBarcode` degerini paylasan urunler grup halinde doner.
- Yeni response DTO'lari eklendi:
  - `FoodProductDuplicateGroupDto`
  - `FoodProductDuplicateGroupPageDto`
- `FoodItemRepository` icine duplicate normalized barcode gruplarini bulan paginated native query eklendi.
- Duplicate gruptaki urunler kalite, kullanim ve id siralamasiyla doner.
- Controller ve service testleri eklendi.

### Karar

- Bu endpoint sadece analiz ve admin gorunurlugu icindir.
- Merge/silme gibi veri degistiren aksiyonlar bu adimda eklenmedi.
- Unique constraint eklemeden once mevcut katalogdaki duplicate durumlari bu endpoint ile incelenmeli.

### Dogrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuc: 56 test gecti, 0 failure, 0 error.

## 2026-05-14 - Food Product Duplicate Merge Endpointi

### Yapilanlar

- Admin urun review modulu icine duplicate merge endpointi eklendi:
  - `POST /api/admin/products/duplicates/merge`
- Yeni request/response DTO'lari eklendi:
  - `FoodProductMergeRequestDto`
  - `FoodProductMergeResponseDto`
- Merge akisi transaction icinde calisir.
- Merge oncesi validasyonlar eklendi:
  - Target product bulunmali.
  - Duplicate product id listesi bos olmamali.
  - Target id duplicate listesinde olmamali.
  - Tum duplicate urunler bulunmali.
  - Target ve duplicate urunler ayni `normalizedBarcode` degerini paylasmali.
- `food_logs.food_id` referanslari duplicate urunlerden target urune tasinir.
- `user_favorites.food_item_id` referanslari duplicate urunlerden target urune tasinir.
- Favorilerde unique constraint cakismasini onlemek icin target urunu zaten favorileyen kullanicilarin duplicate favorite kayitlari merge oncesi silinir.
- Target urunun `usageCount` degeri duplicate urunlerin `usageCount` toplamiyla guncellenir.
- Target urunun kalite ve review skorleri merge sonrasinda yeniden hesaplanir.
- Duplicate urun kayitlari referanslar tasindiktan sonra silinir.
- `UserFavoriteRepository` eklendi.
- `FoodLogsRepository` icine food item referans reassign metodu eklendi.
- Controller ve service testleri eklendi.

### Karar

- Merge islemi sadece admin yetkisiyle calisir.
- Merge sadece ayni `normalizedBarcode` grubundaki urunler icin izinlidir.
- Kullanici log gecmisi korunur; sadece loglarin bagli oldugu urun kaydi target urune tasinir.
- Favori kayitlarinda duplicate unique constraint sorunu veri kaybi yaratmadan, ayni kullanicinin zaten target favorite kaydi varsa duplicate favorite kaydini silerek cozulur.

### Dogrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuc: 58 test gecti, 0 failure, 0 error.

## 2026-05-14 - Normalized Barcode Unique Index Hazirligi

### Yapilan Kontroller

- Calisan lokal API uzerinden OpenAPI dokumani kontrol edildi:
  - `GET /v3/api-docs` HTTP 200 dondu.
  - `/api/admin/products/duplicates`
  - `/api/admin/products/duplicates/merge`
- PostgreSQL Docker container dogrulandi:
  - `grun-postgres`
  - `0.0.0.0:5432->5432/tcp`
- Flyway migration history kontrol edildi:
  - `V1` - `V6` basarili.
- Gercek lokal DB icinde duplicate `normalized_barcode` kontrol edildi:
  - Duplicate kayit bulunmadi.

### Yapilanlar

- `V7__add_unique_normalized_barcode_index.sql` migration dosyasi eklendi.
- `normalized_barcode` icin partial unique index tanimlandi:
  - `normalized_barcode IS NOT NULL` olan urunlerde tekillik zorunlu.
  - Barcode'u olmayan urunler bu constraint disinda kalir.
- Lokal calisan PostgreSQL uzerinde `V7` migration'in basariyla uygulandigi dogrulandi.
- `uq_food_items_normalized_barcode` index'inin PostgreSQL'de olustugu dogrulandi.
- Kod degisikligi sonrasi test seti yeniden calistirildi.

### Karar

- Duplicate analiz ve merge endpointleri eklendigi icin katalog artik unique index'e hazir hale getirildi.
- Bu constraint, ayni normalized barcode'a sahip ikinci bir urunun DB'ye yazilmasini engeller.
- Null barcode degerleri engellenmedi; manuel veya barkodsuz urunler ileride ayri kalite/review akisi ile yonetilebilir.

### Dogrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuc: 58 test gecti, 0 failure, 0 error.
- Lokal API: `GET /v3/api-docs` HTTP 200.
- Flyway history: `V1` - `V7` success.

## 2026-05-14 - User List Endpoint Admin Namespace'e Tasindi

### Yapilanlar

- Genel kullanici controller'i altindaki tum kullanicilari listeleme endpointi kaldirildi:
  - Eski endpoint: `GET /api/users`
- Kullanici listeleme islemi admin controller altina tasindi:
  - Yeni endpoint: `GET /api/admin/users/userList`
- Yeni `AdminUserController` eklendi.
- Admin user endpointi `@PreAuthorize("hasRole('ADMIN')")` ile admin rolune kisitlandi.
- Swagger dokumantasyonunda admin kullanici operasyonlari ayri `Admin Users` tag'i altina alindi.
- 401 ve 403 Swagger response'lari icin gereksiz user list example payload'lari kaldirildi.
- Kaldirilan `/api/users` route'u icin 500 yerine 404 donmesi amaciyla `NoResourceFoundException` handler'i eklendi.
- Controller testleri admin ve normal kullanici yetki ayrimini kapsayacak sekilde guncellendi.

### Karar

- `/api/users` sadece login olan kullanicinin kendi profil ve vucut kompozisyon islemlerini tasir.
- Tum kullanicilari listeleme gibi operasyonlar admin namespace altinda tutulur.
- Normal kullanici, sistemdeki diger kullanicilari listeleyemez.

### Dogrulama

- Komut: `.\mvnw.cmd clean test "-Dtest=*Test,*Tests"`
- Sonuc: 61 test gecti, 0 failure, 0 error.

## 2026-05-14 - Swagger Error Response Standardizasyonu

### Yapilanlar

- Swagger UI'da hata response'larinin basarili response modeliyle ayni gorunmesine sebep olan genel OpenAPI response mirasi duzeltildi.
- Standart hata response modeli eklendi:
  - `ApiErrorResponseDto`
  - Alanlar: `timestamp`, `status`, `error`, `message`, `path`
- `OpenApiConfig` icine global customizer eklendi:
  - `400+` HTTP response kodlari Swagger dokumaninda `ApiErrorResponseDto` semasina baglanir.
  - Hata response example'lari endpoint path'i, status code'u ve ilgili response description'i kullanilarak uretilir.
  - `204 No Content` response'lari Swagger'da body/example gostermeyecek sekilde temizlenir.
- `GlobalExceptionHandler` artik map yerine standart `ApiErrorResponseDto` doner.
- `AuthenticationException` icin 401 standart hata body handler'i eklendi.
- Genel `IllegalArgumentException` response'u 409 yerine 400 olarak duzeltildi.
- `POST /api/auth/register` duplicate email senaryosu artik `AuthResponse` yerine standart hata response body doner.
- Auth controller testlerine hem gercek duplicate email hata body kontrolu hem de OpenAPI 400 schema kontrolu eklendi.
- Product barcode endpointi icin 401/404 Swagger example'larinin endpoint'e ozel path ve mesajla uretildigi testle dogrulandi.

### Karar

- Basarili response DTO'lari ile hata response DTO'lari Swagger'da ayrik tutulacak.
- Hata response'lari genel olarak standart `ApiErrorResponseDto` modeliyle temsil edilecek.
- Gercek conflict durumlari icin ozel exception'lar 409 donmeye devam edecek; genel invalid request hatalari 400 doner.

### Dogrulama

- Komut: `.\mvnw.cmd clean test "-Dtest=*Test,*Tests"`
- Sonuc: 64 test gecti, 0 failure, 0 error.

## 2026-05-15 - Product Search Empty Result Kontrati

### Yapilanlar

- `GET /api/products/search` endpointinde bos sonuc davranisi duzeltildi.
- Endpoint artik urun bulunamadiginda `204 No Content` yerine `200 OK` ve bos `FoodProductSearchPageDto` doner.
- Swagger dokumantasyonundan product search icin `204` response kaldirildi.
- Product controller testleri eklendi:
  - Bos arama sonucunda `200` ve `content: []` doner.
  - Urun bulunan aramada paginated product response doner.
  - Barcode lookup product response doner.

### Karar

- Paginated liste endpointlerinde bos sonuc hata veya no-content degildir.
- Mobil client her durumda ayni response kontratini kullanabilmeli:
  - `content`
  - `page`
  - `size`
  - `totalElements`
  - `totalPages`
- `204` sadece gercekten response body tasimamasi gereken delete gibi aksiyonlarda kullanilacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodItemControllerTest,FoodItemServiceImplTest" test`
- Sonuc: 12 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test "-Dtest=*Test,*Tests"`
- Sonuc: 67 test gecti, 0 failure, 0 error.

## 2026-05-15 - Food Catalog Manuel API Dogrulamasi ve Sort Duzeltmesi

### Yapilan Kontroller

- Lokal backend dogrulandi:
  - `GET /v3/api-docs` HTTP 200 dondu.
- PostgreSQL Docker container dogrulandi:
  - `grun-postgres`
  - `0.0.0.0:5432->5432/tcp`
- Lokal test kullanicisi ile auth token alindi:
  - `codex.foodtest@grun.local`
- Barcode lookup manuel test edildi:
  - `GET /api/products/barcode/3017620422003`
  - Urun basariyla dondu.
  - Urun `OPEN_FOOD_FACTS`, `RAW_IMPORTED`, `NEEDS_REVIEW` olarak gorundu.
- DB cache kontrol edildi:
  - `food_items.normalized_barcode = 3017620422003`
  - Tek kayit bulundu.
  - Review alanlari beklenen degerlerdeydi.
- Product search bos sonuc kontrati canli API'de dogrulandi:
  - `GET /api/products/search?q=unlikely-product-name-codex-000&page=0&size=25`
  - HTTP 200 dondu.
  - `content: []` ve pagination metadata dondu.
- Admin product review endpointi manuel test edildi:
  - `GET /api/admin/products/review?verificationStatus=RAW_IMPORTED&imageStatus=NEEDS_REVIEW&page=0&size=25`
  - Nutella urunu admin review listesinde gorundu.

### Bulunan Problem

- Admin product review endpointi ilk manuel testte 500 dondu.
- Hata mesaji:
  - `Applying Null Precedence using Criteria Queries is not yet supported.`
- Sebep:
  - `Sort.Order.nullsLast()` JPA Criteria query ile bu Hibernate/PostgreSQL akisi icinde desteklenmiyordu.

### Yapilanlar

- `FoodProductReviewServiceImpl` icindeki review ve duplicate product sort'larindan `nullsLast()` kaldirildi.
- `FoodItemServiceImpl` icindeki default product search sort'undan `nullsLast()` kaldirildi.
- Sort davranisi native DB null handling'e birakildi.
- Regression testleri eklendi:
  - Review sort null handling `NATIVE`.
  - Duplicate group sort null handling `NATIVE`.
  - Product search default sort null handling `NATIVE`.

### Karar

- Criteria query ile calisan pageable/sort akislarda explicit `nullsLast/nullsFirst` kullanilmayacak.
- Kalite/review alanlari import ve review akislari tarafindan dolduruldugu icin native null handling pratikte yeterli kabul edildi.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductReviewServiceImplTest,FoodItemServiceImplTest" test`
- Sonuc: 13 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test "-Dtest=*Test,*Tests"`
- Sonuc: 67 test gecti, 0 failure, 0 error.
- Canli API:
  - Barcode lookup HTTP 200.
  - Product search empty page HTTP 200.
  - Admin review HTTP 200.

## 2026-05-15 - ExerciseItem Baslangic Seed Stratejisi

### Yapilanlar

- Exercise catalog icin runtime seed yerine Flyway SQL seed yaklasimi secildi.
- Yeni migration dosyasi eklendi:
  - `V8__seed_initial_exercise_items.sql`
- Baslangic katalog icin 12 temel egzersiz eklendi:
  - Brisk Walking
  - Running
  - Cycling
  - Swimming
  - Bodyweight Squat
  - Push-Up
  - Plank
  - Jump Rope
  - Dumbbell Deadlift
  - Bench Press
  - Yoga
  - Rowing Machine
- Seed kayitlari `met_code` uzerinden idempotent olacak sekilde yazildi; ayni `met_code` varsa tekrar insert edilmez.
- Mevcut eski kayitlarda `ai_eligible` veya `active` bos ise varsayilan olarak `true` yapilir.

### Karar

- Baslangic egzersiz katalog verisi uygulama acilisinda kodla degil, migration ile yonetilecek.
- Bu tercih lokal, test/staging ve ileride production ortamlari arasinda daha tutarli DB durumu saglar.
- Gorsel/video URL alanlari simdilik bos birakildi; daha sonra curator/admin akisi veya CDN karariyla doldurulacak.

### Sonraki Adim

- ExerciseItem katalog API'si yetki, duplicate kontrolu ve listeleme kullanimi acisindan iyilestirilecek.

### Dogrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuc: 67 test gecti, 0 failure, 0 error.
- Lokal API: `GET /v3/api-docs` HTTP 200.
- Flyway history: `V1` - `V8` success.
- Lokal PostgreSQL: `exercise_items` tablosunda 12 seed kayit dogrulandi.
- Canli API: JWT ile `GET /api/exercise-items` 12 hareket dondurdu.

## 2026-05-15 - ExerciseItem Admin Yetkisi ve Duplicate Koruması

### Yapilanlar

- Exercise catalog listeleme endpointi normal authenticated kullanicilara acik birakildi:
  - `GET /api/exercise-items`
- Exercise catalog yazma islemleri admin yetkisine alindi:
  - `POST /api/exercise-items`
  - `PUT /api/exercise-items/{id}`
  - `DELETE /api/exercise-items/{id}`
- `metCode` duplicate kontrolu eklendi.
- Yeni exception eklendi:
  - `DuplicateExerciseItemException`
- Duplicate `metCode` durumunda standart error response ile `409 Conflict` doner.
- Service seviyesinde `metCode` trim edilip uppercase normalize edilir.
- Controller ve service testleri eklendi.

### Karar

- Egzersiz katalog verisi tum kullanicilar tarafindan okunabilir.
- Katalog yonetimi admin operasyonudur; standart kullanici egzersiz katalog kaydi olusturamaz, guncelleyemez veya silemez.
- `metCode`, egzersiz katalog kayitlari icin stabil teknik anahtar gibi ele alinacak ve duplicate olmasi engellenecek.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=ExerciseItemControllerTest,ExerciseItemServiceImplTest" test`
- Sonuc: 11 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 76 test gecti, 0 failure, 0 error.

## 2026-05-15 - ExerciseItem Paginated Search Endpointi

### Yapilanlar

- `GET /api/exercise-items` response kontrati paginated hale getirildi.
- Yeni response DTO eklendi:
  - `ExerciseItemPageDto`
- Endpoint artik su filtreleri destekler:
  - `q`: name, metCode ve description uzerinde arama.
  - `primaryMuscleGroup`
  - `equipment`
  - `difficulty`
  - `active`
  - `page`
  - `size`
- Varsayilan davranis aktif hareketleri dondurur:
  - `active=true`
- Maksimum page size `100` olarak sinirlandirildi.
- Sonuclar `name ASC` siralamasiyla doner.
- `ExerciseItemRepository`, Specification tabanli filtreleme icin `JpaSpecificationExecutor` destekleyecek sekilde guncellendi.
- Controller ve service testleri yeni paginated response kontratina gore guncellendi.

### Karar

- Mobil uygulama egzersiz katalog listesini limitsiz liste olarak cekmeyecek.
- Katalog buyudukce filtreleme ve sayfalama ayni endpoint uzerinden surdurulecek.
- Inactive hareketler varsayilan kullanici listesinde gorunmeyecek; admin ihtiyaci icin `active=false` filtresiyle sorgulanabilir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=ExerciseItemControllerTest,ExerciseItemServiceImplTest" test`
- Sonuc: 12 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 77 test gecti, 0 failure, 0 error.
- Canli API: JWT ile `GET /api/exercise-items?page=0&size=5&q=run` paginated response dondurdu.

## 2026-05-15 - Local Setup Dokumantasyonu

### Yapilanlar

- `README.md` local backend calistirma akisini anlatacak sekilde genisletildi.
- Dokumanda su basliklar netlestirildi:
  - Gereksinimler: Java 17, Docker Desktop, Maven Wrapper, PowerShell.
  - `.env` dosyasinin `.env.example` uzerinden olusturulmasi.
  - `.env` dosyasinin commitlenmemesi gerektigi.
  - `.\scripts\run-local.ps1` ile PostgreSQL ve API baslatma.
  - `.\scripts\stop-local.ps1` ile lokal servisleri durdurma.
  - Swagger UI adresi.
  - Test komutu.
  - Docker ve Flyway history kontrol komutlari.

### Karar

- Lokal kurulum bilgisi repo icinde `README.md` uzerinden takip edilecek.
- Hassas local degerler icin `.env`, paylasilabilir ornekler icin `.env.example` kullanilmaya devam edilecek.

### Dogrulama

- Dokuman degisikligidir; ek test gerektirmez.

## 2026-05-16 - Admin Review Audit Canli Demo Dogrulama

### Yapilanlar

- Local admin kullanici ile login olundu.
- Raw demo product admin review queue icinden alindi:
  - `normalizedBarcode=8690000000042`
- `PATCH /api/admin/products/{id}/review` ile product verified hale getirildi.
- Guncellenen durum:
  - `verificationStatus=VERIFIED`
  - `imageStatus=APPROVED`
- `GET /api/admin/products/{id}/audit?page=0&size=25` ile audit history kontrol edildi.

### Sonuc

- Review update HTTP basarili.
- Audit history toplam kayit sayisi: 5.
- Admin review/audit akisi canli lokal API uzerinde dogrulandi.

### Not

- Demo seed tekrar acik calisirsa raw demo product tekrar review queue durumuna getirilebilir.
- Bu kabul edilebilir; lokal demo seed amaci Swagger akisini tekrar denenebilir tutmaktir.

## 2026-05-16 - Local Demo Cleanup Script

### Yapilanlar

- Yeni script eklendi:
  - `scripts/cleanup-local-demo.ps1`
- Script yalnizca lokal demo verilerini temizler:
  - `demo.user@grun.local`
  - demo food logs
  - `LOCAL_DEMO` exercise logs
  - demo product audit kayitlari
  - demo food products
- Local admin bootstrap kullanicisi korunur:
  - `admin@grun.local`
- README ve local Swagger demo flow dokumani cleanup komutuyla guncellendi.

### Karar

- Local DB volume reset son care olarak kalacak.
- Demo veriyi temizlemek icin kontrollu cleanup script yeterli ve daha az riskli.

## 2026-05-16 - Food/Exercise Log Swagger Demo Aciklamalari

### Yapilanlar

- Food log Swagger aciklamalari local demo seed akisiyle uyumlu hale getirildi.
- Exercise log Swagger aciklamalari local demo seed akisiyle uyumlu hale getirildi.
- Demo test icin ornek tarih degerleri bugunun tarihiyle guncellendi.
- Exercise source ornegi `LOCAL_DEMO` olarak eklendi.

### Karar

- Swagger sadece endpoint listesi degil, lokal demo akisinin denenebilir kilavuzu olarak da kullanilacak.

## 2026-05-16 - Sonraki Backend Sprint Secenekleri

### Yapilanlar

- Yeni dokuman eklendi:
  - `docs/NEXT_BACKEND_SPRINT_OPTIONS.md`
- Sonraki backend sprint alternatifleri karsilastirildi:
  - Mail/password reset
  - i18n TR/ENG
  - Google/Apple login
  - Admin panel API genisletmesi

### Karar

- Onerilen siradaki backend sprint: Mail/password reset.
- Gerekce:
  - Mobil app icin temel hesap guvenligi ihtiyaci.
  - Backend-only ilerlenebilir.
  - Swagger ve testlerle dogrulanabilir.
  - Production hazirlik seviyesini artirir.

## 2026-05-15 - Duplicate Merge Audit Kaydi

### Yapilanlar

- Duplicate product merge akisi target product uzerinde audit kaydi olusturacak sekilde genisletildi.
- Controller merge endpointi authenticated admin email bilgisini service'e gecmeye basladi.
- Service interface geriye uyumlu kalacak sekilde yeni admin email parametreli merge metodunu destekler hale getirildi.
- Merge audit kaydi:
  - `actionType = MERGE`
  - `fieldName = duplicateProductIds`
  - `oldValue = merged duplicate id listesi`
  - `newValue = target product id`
  - `note = reassigned food log/favorite sayilari`
- Audit kaydi sadece target product'a baglanir; silinen duplicate product kayitlarina FK baglanmaz.

### Karar

- Merge audit'i duplicate product kayitlari silinmeden once target urun uzerinden yazilir.
- Bu yaklasim audit FK davranisini bozmadan merge isleminin izlenmesini saglar.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductReviewServiceImplTest,AdminFoodProductReviewControllerTest" test`
- Sonuc: 17 test gecti, 0 failure, 0 error.

## 2026-05-15 - Barcode Lookup REJECTED Urun Davranisi

### Yapilanlar

- `getOrSaveFoodItemByBarcode` local DB'de `REJECTED` urun bulursa artik urunu kullaniciya dondurmez.
- `REJECTED` local urun icin Open Food Facts fallback calistirilmaz.
- External search sonucunda gelen barkod local DB'de `REJECTED` urune denk gelirse bu urun search response'a eklenmez ve yeniden save edilmez.
- Testler eklendi:
  - Local rejected barkod lookup `ProductNotFoundException` firlatir.
  - Local rejected barkod lookup external fallback cagirmaz.
  - External search sonucu rejected local urune denk gelirse response bos kalir.
  - Rejected local urun yeniden cachelenmez.

### Karar

- `REJECTED` katalog kaydi fiziksel olarak silinmeden admin tarafinda korunur.
- Normal kullanici hem search hem barcode lookup akisi uzerinden rejected urunu goremez.
- Rejected urunun yeniden kullanima alinmasi admin review/yeniden onay sureciyle yapilmalidir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodItemServiceImplTest,FoodItemServiceSearchIntegrationTest" test`
- Sonuc: 12 test gecti, 0 failure, 0 error.

## 2026-05-15 - Food Product Review Audit History Implementasyonu

### Yapilanlar

- Review audit action enum'u eklendi:
  - `FoodProductReviewAuditAction`
- Review audit entity eklendi:
  - `FoodProductReviewAuditEntity`
- Review audit repository eklendi:
  - `FoodProductReviewAuditRepository`
- Review audit response DTO'lari eklendi:
  - `FoodProductReviewAuditDto`
  - `FoodProductReviewAuditPageDto`
- Flyway migration eklendi:
  - `V10__add_food_product_review_audits.sql`
- Migration ile `food_product_review_audits` tablosu ve temel indexler tanimlandi.
- `PATCH /api/admin/products/{id}/review` akisi audit yazacak sekilde guncellendi.
- Controller authenticated admin email bilgisini service'e gecmeye basladi.
- Sadece gercekten degisen alanlar icin audit kaydi olusturulur.
- Yeni admin endpoint eklendi:
  - `GET /api/admin/products/{id}/audit?page=0&size=25`

### Karar

- Mevcut `updateProductReview(id, request)` imzasi geriye uyumluluk icin korunur ve yeni admin email parametreli metoda delege eder.
- Audit kayitlari kullanici search davranisina dahil edilmez.
- Ilk implementasyon review update alanlarini izler; duplicate merge audit'i ayri is olarak kalir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductReviewServiceImplTest,AdminFoodProductReviewControllerTest" test`
- Sonuc: 14 test gecti, 0 failure, 0 error.

## 2026-05-15 - Food Product Review Request Validasyonlari

### Yapilanlar

- `FoodProductReviewRequestDto` icine `reviewNote` alani eklendi.
- `displayImageUrl` verildiginde absolute `http` veya `https` URL olmasi zorunlu hale getirildi.
- Product verification status `REJECTED` yapilirsa `reviewNote` zorunlu hale getirildi.
- Image status `REJECTED` yapilirsa `reviewNote` zorunlu hale getirildi.
- Review note audit kayitlarina yazilir hale getirildi.
- Testler eklendi:
  - Invalid display image URL reddedilir.
  - Product reject note olmadan reddedilir.
  - Image reject note olmadan reddedilir.
  - Audit note kayda yazilir.

### Karar

- Reject islemleri gerekcesiz yapilmayacak.
- Curated image URL mobil client tarafinda kullanilacagi icin sadece absolute HTTP/HTTPS URL kabul edilecek.
- Review note simdilik `FoodItemEntity` uzerinde tutulmaz; degisiklik audit history icinde saklanir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductReviewServiceImplTest,AdminFoodProductReviewControllerTest" test`
- Sonuc: 17 test gecti, 0 failure, 0 error.

## 2026-05-15 - Local Demo Seed Ihtiyaci Degerlendirmesi

### Yapilanlar

- Yeni dokuman eklendi:
  - `docs/LOCAL_DEMO_SEED_PLAN.md`
- Lokal demo seed ihtiyaci mevcut API ve DB akisi uzerinden degerlendirildi.
- Standard demo user'in API ile olusturulabilecegi belirlendi.
- Admin demo user ve verified food product seed icin mevcut dogrudan guvenli API olmadigi netlestirildi.

### Karar

- Demo veriler production Flyway migration icine konulmayacak.
- Local demo seed script'i yazilmadan once admin bootstrap yaklasimi netlestirilecek.
- Gecici DB role update gibi islemler ancak acikca local-only script icinde ve production korumasi ile dusunulebilir.

### Dogrulama

- Dokuman degisikligidir; ek test gerektirmez.

## 2026-05-15 - DB Seed ve Katalog Veri Stratejisi

### Yapilanlar

- Yeni dokuman eklendi:
  - `docs/DATA_SEED_STRATEGY.md`
- README icine proje dokumanlari listesi eklendi.
- Seed stratejisi veri tipine gore ayrildi:
  - Kucuk ve stabil reference veriler Flyway ile gelir.
  - Buyuk food product catalog Flyway ile topluca doldurulmaz.
  - Food catalog barcode lookup, Open Food Facts fallback, admin review ve ileride batch import ile buyur.
- Lokal-only demo seed script fikri ayri ve opsiyonel is olarak ayrildi.

### Karar

- Exercise catalog seed migration yaklasimi dogru yerde kullanilmaya devam edecek.
- Food product katalog verisi kalite/review surecinden gecmeden verified kabul edilmeyecek.
- 100 binlerce urun ilk gunden DB'ye doldurulmayacak; kullanim ve admin kalite sureciyle kademeli buyutulecek.
- Buyuk import ancak review, audit ve pagination/index altyapisi olgunlastiktan sonra tasarlanacak.

### Dogrulama

- Dokuman degisikligidir; ek test gerektirmez.

## 2026-05-15 - Food Product Catalog Manuel Akis Dogrulamasi

### Yapilan Kontroller

- Lokal API ayakta dogrulandi:
  - `GET /v3/api-docs` HTTP 200.
- PostgreSQL Docker container ayakta dogrulandi:
  - `grun-postgres`
- Geçici test kullanıcısı ile JWT alindi.
- Barcode lookup canli API uzerinden test edildi:
  - `GET /api/products/barcode/3017620422003`
  - Urun adi: `Nutella`
  - `dataSource = OPEN_FOOD_FACTS`
  - `verificationStatus = RAW_IMPORTED`
  - `imageStatus = NEEDS_REVIEW`
- DB cache kontrol edildi:
  - `normalized_barcode = 3017620422003`
  - Tek urun kaydi bulundu.
- Admin review endpointi canli API uzerinden test edildi:
  - `GET /api/admin/products/review?verificationStatus=RAW_IMPORTED&imageStatus=NEEDS_REVIEW&page=0&size=25`
  - `totalElements = 1`
  - Ilk urun: `Nutella`
- Geçici test kullanıcısı DB'den temizlendi.

### Karar

- Food product lookup, cache ve admin review akisi beklenen sekilde calisiyor.
- Admin review tarafinda siradaki iyilestirme, review durum gecislerini ve curator is akisini daha net hale getirmek olacak.

## 2026-05-15 - Product Catalog Admin Flow Dokumani

### Yapilanlar

- Yeni dokuman eklendi:
  - `docs/PRODUCT_CATALOG_ADMIN_FLOW.md`
- Admin product catalog review sureci sade is akisi olarak yazildi:
  - Imported urunleri listeleme.
  - Urun bilgisini kontrol etme.
  - Curated product name ve display image guncelleme.
  - Image approval/rejection.
  - Product verification/rejection.
  - Duplicate analiz.
  - Duplicate merge.

### Karar

- Open Food Facts gibi dis kaynaklardan gelen urunler ham veri olarak kabul edilmeye devam edecek.
- Admin review akisi, katalog kalitesini artirmak icin temel curator sureci olacak.
- `REJECTED` urunlerin kullanici arama sonucundaki davranisi ayri teknik is olarak ele alinacak.

### Dogrulama

- Dokuman degisikligidir; ek test gerektirmez.

## 2026-05-15 - ExerciseItem Catalog Index Migration

### Yapilanlar

- ExerciseItem katalog filtreleri icin yeni Flyway migration eklendi:
  - `V9__add_exercise_item_catalog_indexes.sql`
- Eklenen indexler:
  - `idx_exercise_items_active_name`
  - `idx_exercise_items_difficulty`
  - `idx_exercise_items_primary_muscle_group`
  - `idx_exercise_items_equipment`

### Karar

- `GET /api/exercise-items` artik `active`, `difficulty`, `primaryMuscleGroup`, `equipment` filtrelerini destekledigi icin bu alanlar temel index kapsamına alindi.
- `active + name` composite index'i varsayilan aktif katalog listeleme ve `name ASC` siralamasi icin eklendi.

### Sonraki Adim

- Lokal PostgreSQL uzerinde Flyway `V9` migration'i dogrulanacak.

### Dogrulama

- Komut: `.\mvnw.cmd clean test`
- Sonuc: 77 test gecti, 0 failure, 0 error.
- Lokal PostgreSQL Flyway history: `V9 - add exercise item catalog indexes` success.

## 2026-05-15 - Food Product Review Durum Validasyonlari

### Yapilanlar

- Admin product review update akisi icin temel durum validasyonlari eklendi.
- `verificationStatus = VERIFIED` yapilacak urunlerde product name zorunlu hale getirildi.
- `imageStatus = APPROVED` yapilacak urunlerde `displayImageUrl` zorunlu hale getirildi.
- Validasyon hatalari `IllegalArgumentException` ile standart `400 Bad Request` response akisini kullanir.
- Service testleri eklendi:
  - Verified urunde bos product name reddedilir.
  - Approved image status icin bos display image URL reddedilir.

### Karar

- Admin review akisi serbest alan guncelleme olmaktan cikarak minimum kalite kurallari tasimaya basladi.
- Daha detayli durum gecisleri ve audit history ayri bir teknik is olarak ele alinacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductReviewServiceImplTest" test`
- Sonuc: 6 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 79 test gecti, 0 failure, 0 error.

## 2026-05-15 - Dashboard Swagger ve Test Kapsami

### Yapilanlar

- `GET /api/dashboard/daily-summary` endpointi icin Swagger aciklamasi guclendirildi.
- Endpoint bearer token gerektirdigi icin OpenAPI uzerinde `bearerAuth` security requirement eklendi.
- `date` request parametresi Swagger'da opsiyonel tarih olarak aciklandi.
- `DailySummaryDto` response semasi alan bazli `@Schema` aciklamalari ve ornekleriyle netlestirildi.
- Controller testi eklendi:
  - Authenticated kullanicinin email bilgisinin service katmanina aktarildigi dogrulandi.
  - Request `date` parametresinin dogru parse edildigi dogrulandi.
  - Daily summary JSON response alanlari kontrol edildi.
- Service testi eklendi:
  - Log ve aktif hedef olmayan bos gun senaryosunda toplamlarin `0` dondugu dogrulandi.
  - Progress log yokken mevcut kullanici profil kilosunun `currentWeight` olarak kullanildigi dogrulandi.

### Karar

- Dashboard endpointi kullaniciya ait ozet veriyi authentication context uzerinden uretmeye devam edecek.
- Liste endpointlerinden farkli olarak dashboard response'u tek gunluk ozet kontrati tasir.
- Empty day davranisi hata degildir; mobil uygulama icin sifirlanmis summary response'u doner.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=DashboardControllerTest,DashboardServiceImplTest" test`
- Sonuc: 2 test gecti, 0 failure, 0 error.

## 2026-05-15 - Product Search REJECTED Urun Davranisi

### Yapilanlar

- Kullanici urun arama akisi `REJECTED` verification status tasiyan urunleri dondurmeyecek sekilde guncellendi.
- Search specification icine su kural eklendi:
  - `verificationStatus IS NULL` olan eski kayitlar gorunebilir.
  - `verificationStatus != REJECTED` olan aktif/ham/onayli kayitlar gorunebilir.
  - `verificationStatus = REJECTED` olan urunler kullanici search sonucundan dislanir.
- Admin review akisi degistirilmedi; admin tarafinda `REJECTED` urunler filtrelenebilir ve incelenebilir kalir.
- Gercek JPA specification davranisini dogrulamak icin `FoodItemServiceSearchIntegrationTest` eklendi.

### Karar

- `REJECTED` urunler katalogdan fiziksel olarak silinmeyecek.
- Kullanici aramasinda gorunmeyecekler; boylece hatali veya uygunsuz katalog verisi mobil deneyime tasinmayacak.
- Eski log referanslari korunur; bu is sadece product search sonucunu etkiler.
- Barcode lookup icin `REJECTED` local urun davranisi ayri teknik is olarak ele alinacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodItemServiceImplTest,FoodItemServiceSearchIntegrationTest" test`
- Sonuc: 10 test gecti, 0 failure, 0 error.

## 2026-05-15 - ExerciseItem Canli API ve Flyway V9 Dogrulamasi

### Yapilan Kontroller

- Lokal API canli olarak dogrulandi:
  - `GET /v3/api-docs` HTTP 200.
- Docker PostgreSQL container durumu kontrol edildi:
  - `grun-postgres` ayakta.
- Flyway history kontrol edildi:
  - `V9 - add exercise item catalog indexes` success.
- PostgreSQL indexleri kontrol edildi:
  - `idx_exercise_items_active_name`
  - `idx_exercise_items_difficulty`
  - `idx_exercise_items_equipment`
  - `idx_exercise_items_primary_muscle_group`
- Geçici test kullanicisi ile JWT alinarak filtreli endpoint test edildi:
  - `GET /api/exercise-items?page=0&size=5&q=run&active=true`
  - HTTP 200 dondu.
  - Ilk sonuc `Running` olarak dogrulandi.
- Geçici test kullanicisi DB'den temizlendi.

### Karar

- ExerciseItem paginated/filter endpointi migration sonrasi canli ortamda beklenen sekilde calisiyor.
- V9 index migration'i lokal PostgreSQL uzerinde uygulanmis durumda.

## 2026-05-15 - Food Product Review Audit History Plani

### Yapilanlar

- Yeni plan dokumani eklendi:
  - `docs/FOOD_PRODUCT_REVIEW_AUDIT_PLAN.md`
- Admin product review islemleri icin audit history kapsami tanimlandi.
- Planlanan tablo ve entity netlestirildi:
  - `food_product_review_audits`
  - `FoodProductReviewAuditEntity`
- Audit alanlari belirlendi:
  - `food_item_id`
  - `reviewed_by`
  - `action_type`
  - `field_name`
  - `old_value`
  - `new_value`
  - `note`
  - `created_at`
- Admin audit listeleme endpointi planlandi:
  - `GET /api/admin/products/{id}/audit?page=0&size=25`
- Review update sirasinda sadece gercekten degisen alanlar icin audit kaydi yazilmasi kararlastirildi.

### Karar

- Ilk audit implementasyonu sadece `PATCH /api/admin/products/{id}/review` akisini kapsayacak.
- Duplicate merge audit'i sonraki is olarak ayrilacak.
- Admin kimligi controller tarafinda `AuthenticationPrincipal` uzerinden alinip service'e acik parametre olarak gecilecek.
- Audit history user-facing search davranisina dahil edilmeyecek; admin kalite sureci icin tutulacak.

### Dogrulama

- Dokuman degisikligidir; ek test gerektirmez.

## 2026-05-16 - V10 Audit, Swagger Ornekleri ve Local Admin Bootstrap

### Yapilanlar

- Flyway V10 migration lokal PostgreSQL uzerinde kontrol edildi:
  - `V10 - add food product review audits` success.
  - `food_product_review_audits` kolonlari mevcut.
  - Audit indexleri mevcut:
    - `idx_food_product_review_audits_action_type`
    - `idx_food_product_review_audits_food_item_created_at`
    - `idx_food_product_review_audits_reviewed_by`
- Admin product review Swagger dokumani guclendirildi:
  - Review update request body icin approve/reject ornekleri eklendi.
  - `reviewNote` rejection senaryolarinda gorunur hale getirildi.
  - Duplicate merge request body ornegi eklendi.
  - Audit endpoint response schema acik yazildi.
- Local admin bootstrap yapisi eklendi:
  - Sadece `local` profile altinda aktif olabilir.
  - Varsayilan olarak kapali.
  - `GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED=true`, email ve password env degerleriyle admin kullanici olusturur/gunceller.
  - Production icin otomatik admin kullanici yaratmaz.

### Karar

- Admin kullanici seed ihtiyaci demo/local gelistirme icin gereklidir, ancak production akisi olmamalidir.
- Local admin bootstrap kontrollu bir altyapi olarak tutulacak; sonraki demo seed isleri buna baglanabilir.
- V10 DB migration dogrulamasi tamamlandi. API endpointin canli HTTP dogrulamasi lokal PostgreSQL parola/volume uyumsuzlugu nedeniyle ayri setup isine ayrildi.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AdminFoodProductReviewControllerTest,LocalAdminBootstrapConfigTest" test`
- Sonuc: 9 test gecti, 0 failure, 0 error.
- Tam regresyon komutu: `.\mvnw.cmd clean test`
- Sonuc: 93 test gecti, 0 failure, 0 error.

## 2026-05-16 - Local Setup ve Admin Audit Canli Dogrulama

### Yapilanlar

- Docker PostgreSQL container'in ayakta oldugu kontrol edildi.
- Mevcut `.env` degerleriyle PostgreSQL TCP baglantisi dogrulandi.
- Spring Boot API lokal olarak baslatildi ve `/v3/api-docs` HTTP 200 dondu.
- Local admin bootstrap gecici process env ile acilarak admin kullanici olusturuldu/guncellendi.
- `admin@grun.local` kullanicisinin `ADMIN` rolunde oldugu DB uzerinden dogrulandi.
- Admin JWT ile `GET /api/admin/products/1/audit?page=0&size=25` endpointi cagrildi.
- Endpoint HTTP 200 dondu; mevcut lokal urunde audit kaydi olmadigi icin `totalElements=0` donmesi beklenen durum olarak kaydedildi.
- README local setup bolumu guncellendi:
  - local admin bootstrap env degerleri
  - PostgreSQL Docker volume parola davranisi
  - stop script fallback notu
- `scripts/stop-local.ps1` Windows process command-line inspection yetki problemi durumunda port `8080` fallback kullanacak sekilde guclendirildi.

### Karar

- Mevcut local DB volume korunacak; sifirlama su an gerekli degil.
- Parola uyumsuzlugu tekrar olusursa once `.env` ile mevcut volume'un baglanti testi yapilacak, volume reset son care olarak ele alinacak.
- Admin bootstrap production mekanizmasi degildir; sadece local/demo akisi icindir.

### Dogrulama

- PostgreSQL TCP testi: basarili.
- `/v3/api-docs`: HTTP 200.
- `GET /api/admin/products/1/audit?page=0&size=25`: HTTP 200.

## 2026-05-16 - Local Demo Seed Ilk Implementasyon

### Yapilanlar

- `LocalDemoSeedConfig` eklendi.
- Yapı sadece `local` profile altinda ve `GRUN_LOCAL_DEMO_SEED_ENABLED=true` ile calisir.
- Varsayilan olarak kapali tutuldu.
- Demo standard user seed edildi:
  - `demo.user@grun.local`
  - Rol: `STANDARD`
- 3 verified demo food product seed edildi:
  - `GRun Demo Greek Yogurt`
  - `GRun Demo Banana`
  - `GRun Demo Chicken Breast`
- Demo product seed idempotent tasarlandi:
  - `normalizedBarcode` uzerinden mevcut kayit bulunur.
  - Varsa guncellenir, yoksa olusturulur.
  - Tekrar calisma duplicate product uretmez.
- Local admin/demo env placeholder okumasi guclendirildi:
  - Direkt env degiskenleri ve property mapping birlikte desteklenir.
- README, `.env.example` ve `application-example.yml` demo seed ayarlariyla guncellendi.

### Canli Dogrulama

- API local profile ile demo seed acik sekilde baslatildi.
- DB uzerinde kullanicilar dogrulandi:
  - `admin@grun.local` -> `ADMIN`
  - `demo.user@grun.local` -> `STANDARD`
- DB uzerinde 3 demo product dogrulandi.
- Demo user ile login basarili oldu.
- Demo user JWT ile product search dogrulandi:
  - `GET /api/products/search?q=GRun%20Demo&page=0&size=10`
  - Sonuc: 3 verified demo product.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=LocalDemoSeedConfigTest,LocalAdminBootstrapConfigTest" test`
- Sonuc: 4 test gecti, 0 failure, 0 error.

## 2026-05-16 - Local Demo Seed Food/Exercise Log Genisletmesi

### Yapilanlar

- Local demo seed food log ve exercise log uretecek sekilde genisletildi.
- Demo food log stratejisi:
  - Bugun icin `BREAKFAST`, `SNACK`, `LUNCH` kayitlari olusturulur.
  - Ayni gun ve ayni meal type zaten varsa tekrar olusturulmaz.
- Demo exercise log stratejisi:
  - `source=LOCAL_DEMO`
  - `externalId=local-demo-run-{date}`
  - Ayni source/externalId varsa tekrar olusturulmaz.
- Food log idempotency icin `FoodLogsRepository.findByUserAndMealTypeAndLogDateBetween(...)` eklendi.

### Canli Dogrulama

- API local demo seed acik sekilde yeniden baslatildi.
- Bugun icin demo user food log sayisi: 3.
- Bugun icin demo user exercise log sayisi: 1.
- Demo user ile dashboard endpointi cagrildi:
  - `GET /api/dashboard/daily-summary`
  - `consumedCalories=521.8`
  - `burnedCalories=300.0`
  - `totalExerciseMinutes=30`

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=LocalDemoSeedConfigTest" test`
- Sonuc: 2 test gecti, 0 failure, 0 error.

## 2026-05-16 - Password Reset Altyapisi

### Yapilanlar

- Auth modulu icin sifre sifirlama endpointleri eklendi:
  - `POST /api/auth/password-reset/request`
  - `POST /api/auth/password-reset/confirm`
- Reset tokenlari icin `password_reset_tokens` tablosu tasarlandi.
- Flyway migration eklendi:
  - `V11__add_password_reset_tokens.sql`
- Tokenlar DB'de ham haliyle degil, SHA-256 hash olarak saklanacak sekilde uygulandi.
- Mevcut kullanici icin yeni reset talebi geldiginde onceki kullanilmamis tokenlar gecersiz hale getirilir.
- Token gecerlilik suresi configurable yapildi:
  - `GRUN_PASSWORD_RESET_EXPIRATION_MINUTES`
  - `GRUN_PASSWORD_RESET_BASE_URL`
- Gercek mail provider secilene kadar local gelistirme icin log tabanli mail sender eklendi.
- Swagger dokumani password reset endpointlerini gosterecek sekilde genisletildi.

### Karar

- Request endpointi email var/yok bilgisini aciga cikarmamak icin her zaman generic basari mesaji doner.
- Ham reset token sadece mail/log katmaninda gorunur; persistence katmaninda hash saklanir.
- Gercek email provider secimi simdilik ertelendi; ileride SMTP, SendGrid, AWS SES veya benzeri provider ile bu abstraction degistirilebilir.

### Dogrulama

- Service ve controller testleri eklendi.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 101 test gecti, 0 failure, 0 error.

## 2026-05-16 - Admin Dashboard Summary API

### Yapilanlar

- Yeni admin endpoint eklendi:
  - `GET /api/admin/dashboard/summary`
- Endpoint sadece `ADMIN` role ile kullanilabilir.
- Response icinde kullanici metrikleri doner:
  - total users
  - standard users
  - pro users
  - admin users
- Response icinde food catalog kalite metrikleri doner:
  - total products
  - verified products
  - raw imported products
  - needs review products
  - rejected products
  - review queue products
- `AdminDashboardService` ve implementation eklendi.
- `UserRepository.countByRole(...)` eklendi.
- `FoodItemRepository` katalog status count ve review queue count destekleyecek sekilde genisletildi.

### Karar

- Admin panel UI gelmeden once backend metrikleri hazir tutulacak.
- Review queue metric'i product verification status veya image review status nedeniyle admin ilgisi isteyen urunleri tek sayida toplar.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AdminDashboardControllerTest,AdminDashboardServiceImplTest" test`
- Sonuc: 3 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 104 test gecti, 0 failure, 0 error.

## 2026-05-16 - I18n Hata Kategori Cekirdegi

### Yapilanlar

- `Accept-Language` header'i ile locale secimi icin `LocaleConfig` eklendi.
- Varsayilan locale `en`, desteklenen locale'ler `en` ve `tr` olarak belirlendi.
- `messages.properties` ve `messages_tr.properties` eklendi.
- `GlobalExceptionHandler` standart error category alanini message key uzerinden resolve edecek sekilde guncellendi.
- Validation error icin Turkish kategori testi eklendi.

### Karar

- Bu adim tam i18n cevirisi degildir; once merkezi hata kategori altyapisi kuruldu.
- Sonraki i18n adimi validation annotation mesajlarini ve domain exception mesajlarini message key tabanli hale getirmek olacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AuthControllerTest" test`
- Sonuc: 7 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 105 test gecti, 0 failure, 0 error.

## 2026-05-16 - Email Verification Ihtiyaci Notu

### Karar

- Uye olurken mail onayi alinmasi gerekecek.
- Bu is password reset ile ayni mail abstraction mantigina baglanacak, ancak ayri bir hesap dogrulama akisi olarak ele alinacak.
- Ilk tasarimda kullanici register oldugunda `emailVerified=false` baslamali.
- Verification token DB'de ham haliyle degil hash olarak saklanmali.
- Token confirm edildiginde kullanici email dogrulanmis hale gelmeli.
- Production oncesi gercek mail provider secimi password reset ve email verification tarafini birlikte kapsayacak.

### Durum

- Su an aktif implementasyon kapsaminda degil.
- Roadmap'e sonraki hesap guvenligi islerinden biri olarak eklendi.

## 2026-05-16 - Auth Validation Mesajlari I18n

### Yapilanlar

- Auth register/login request validation mesajlari message key tabanli hale getirildi.
- Password reset request/confirm validation mesajlari message key tabanli hale getirildi.
- `messages.properties` ve `messages_tr.properties` icine email, password ve reset token validasyon mesajlari eklendi.
- `Accept-Language: tr` ile register email validation hatasinin Turkce dondugu test edildi.

### Karar

- I18n genisletmesi parca parca yapilacak.
- Ilk kapsam hesap guvenligi endpointleri oldu: auth ve password reset.
- User goal, progress log ve diger domain DTO validasyonlari sonraki i18n isleri olarak kalacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AuthControllerTest,PasswordResetServiceImplTest" test`
- Sonuc: 11 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 105 test gecti, 0 failure, 0 error.

## 2026-05-18 - Email Verification Ilk Implementasyon

### Yapilanlar

- Register sonrasi email verification akisi eklendi.
- `users.email_verified` kolonu eklendi.
- Yeni kayit olan kullanicilar `emailVerified=false` baslar.
- Mevcut kullanicilar migration ile verified kabul edilir.
- Email verification token modeli eklendi:
  - `email_verification_tokens`
  - token hash
  - expires at
  - used at
- Flyway migration eklendi:
  - `V12__add_email_verification.sql`
- Email verification endpointleri eklendi:
  - `POST /api/auth/email-verification/resend`
  - `POST /api/auth/email-verification/confirm`
- Local log tabanli mail sender eklendi.
- Local admin bootstrap ve local demo seed kullanicilari verified olarak set edilir.
- Login akisi email verified olmayan kullaniciyi reddeder.

### Karar

- Kendi mail server'i yazilmayacak.
- Backend provider bagimsiz kalacak; mail gonderimi ileride Brevo, Resend veya Amazon SES implementasyonu ile degistirilebilir.
- Verification token ham olarak DB'de tutulmaz; sadece hash tutulur.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AuthControllerTest,EmailVerificationServiceImplTest,LocalAdminBootstrapConfigTest,LocalDemoSeedConfigTest" test`
- Sonuc: 18 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 112 test gecti, 0 failure, 0 error.

## 2026-05-18 - Food/Exercise API Validation ve Swagger Sıkılaştırması

### Yapilanlar

- Food log request DTO'su icin zorunlu alan validationlari eklendi:
  - `foodItemId`
  - `portionSize`
  - `logDate`
- Exercise log request DTO'su icin zorunlu alan validationlari eklendi:
  - `exerciseItemId`
  - `durationMinutes`
  - `caloriesBurned`
  - `logDate`
- Exercise item katalog request DTO'su icin zorunlu alan validationlari eklendi:
  - `name`
  - `metCode`
  - `caloriesPerMinute`
- Validation mesajlari `messages.properties` ve `messages_tr.properties` icine tasindi.
- Food/Exercise controller Swagger hata response'lari `ApiErrorResponseDto` schema'si ile netlestirildi.
- `ConstraintViolationException` ve `DateTimeParseException` icin global 400 response handling eklendi.

### Karar

- Bos veya eksik request body artik servis katmanina gitmeden 400 validation error doner.
- Swagger'da error response'lar basarili response modelini kopyalamayacak sekilde ayrildi.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodLogsControllerTest,ExerciseLogsControllerTest,ExerciseItemControllerTest" test`
- Sonuc: 19 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 116 test gecti, 0 failure, 0 error.

## 2026-05-18 - i18n Validation Kapsam Genisletmesi

### Yapilanlar

- Kalan hard-coded DTO validation mesajlari i18n key'lerine tasindi:
  - `UserGoalDto`
  - `ProgressLogDto`
  - `FoodProductMergeRequestDto`
- Yeni validation mesajlari hem English hem Turkish message bundle icine eklendi.
- Goal ve progress validation response'lari `Accept-Language: tr` ile test edildi.

### Karar

- Kullaniciya donen validation mesajlari DTO icinde sabit metin olarak tutulmayacak.
- Yeni request DTO'lari icin validation mesajlari `messages.properties` ve `messages_tr.properties` icinden yonetilecek.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=UserGoalControllerTest,ProgressLogControllerTest,AdminFoodProductReviewControllerTest" test`
- Sonuc: 19 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 118 test gecti, 0 failure, 0 error.

## 2026-05-18 - Mobile MVP Backend Gap Analizi

### Yapilanlar

- Mobil uygulama publish hedefi icin backend gap analizi dokumani eklendi:
  - `docs/MOBILE_MVP_BACKEND_GAP_ANALYSIS.md`
- Mevcut backend kapsami, MVP blocking gap'leri, non-blocking uzun vadeli gap'ler ve onerilen siradaki backend sprinti netlestirildi.

### Karar

- Backend erken mobil entegrasyon icin yeterli seviyeye yaklasti.
- Production/publish oncesi refresh token, gercek email provider, rate limiting ve food portion/unit modeli bloklayici kabul edildi.

### Dogrulama

- Kod degisikligi olmadigi icin test calistirmak zorunlu degil.
- Dokuman eklendigi manuel olarak kontrol edildi.

## 2026-05-19 - Refresh Token ve Logout/Revoke Flow

### Yapilanlar

- Mobil session icin refresh token altyapisi eklendi.
- Yeni tablo eklendi:
  - `refresh_tokens`
- Refresh token DB'de raw olarak tutulmaz; SHA-256 hash olarak saklanir.
- Login response artik access token ile birlikte refresh token da doner.
- Yeni auth endpointleri eklendi:
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
- Refresh token her refresh isteginde rotate edilir:
  - eski token `usedAt` ile isaretlenir
  - yeni refresh token uretilir
- Logout sadece gonderilen refresh tokeni revoke eder.
- Password reset basarili olunca kullanicinin aktif refresh tokenlari revoke edilir.
- `AuthResponse` geriye uyumluluk icin `token` alanini korur; yanina `refreshToken`, `tokenType`, `expiresIn` alanlari eklendi.

### Karar

- Mobil uygulama kullanicidan surekli login istemeyecek.
- Access token kisa omurlu kalacak; refresh token mobil oturum devamlıligini saglayacak.
- Refresh token raw degerleri loglanmayacak ve DB'de saklanmayacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AuthControllerTest,RefreshTokenServiceImplTest,PasswordResetServiceImplTest" test`
- Sonuc: 19 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 124 test gecti, 0 failure, 0 error.

## 2026-05-19 - Configurable Email Provider Altyapisi

### Yapilanlar

- Password reset ve email verification mail gonderimleri merkezi `MailDeliveryService` arkasina alindi.
- Local development icin varsayilan `LOG` provider korundu; raw test linkleri uygulama logunda gorulebilir.
- Gercek mail gonderimi icin ilk provider olarak Brevo HTTP API entegrasyonu eklendi.
- Provider secimi ve credential degerleri environment config ile yonetilecek hale getirildi:
  - `GRUN_MAIL_PROVIDER`
  - `GRUN_MAIL_FROM_EMAIL`
  - `GRUN_MAIL_FROM_NAME`
  - `GRUN_BREVO_API_KEY`
  - `GRUN_BREVO_API_URL`
- README icine transactional email provider ayarlari eklendi.

### Karar

- Sifre resetleme ve email verification domain akislari mail provider detayini bilmeyecek.
- Local ortamda masrafsiz ve hizli gelistirme icin `LOG` provider kullanilacak.
- Production ortaminda gercek provider credentiallari kod yerine deployment secret olarak verilecek.

### Dogrulama

- Sender siniflari icin `MailDeliveryService` delegasyon testleri eklendi.

## 2026-05-19 - Food Portion ve Unit Modeli

### Yapilanlar

- Food log modeli `portionUnit` ve `normalizedPortionGrams` alanlariyla genisletildi.
- `FoodPortionUnit` enum eklendi:
  - `GRAM`
  - `MILLILITER`
  - `SERVING`
  - `PIECE`
- Eski client uyumlulugu icin `portionUnit` bos gelirse `GRAM` kabul edilecek sekilde servis mantigi korundu.
- `SERVING` ve `PIECE` icin gram donusumu product `servingSizeGrams` uzerinden hesaplanacak sekilde eklendi.
- Food product modeline `servingSizeGrams` ve `servingUnit` alanlari eklendi.
- Daily nutrition aggregation sorgulari artik `normalized_portion_grams` alanini kullanacak sekilde guncellendi.
- Flyway migration eklendi:
  - `V14__add_food_portion_units.sql`

### Karar

- Besin degerleri uygulama icinde 100 gram bazli hesaplanmaya devam edecek.
- Kullanici deneyimi tarafinda farkli porsiyon birimleri desteklenecek, fakat hesaplama icin tek normalize gram alani tutulacak.
- Serving bilgisi eksik urunlerde gecici varsayilan 100 gram kullanilacak; admin review surecinde serving bilgisi duzeltilebilir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodLogsServiceImplTest,FoodLogsControllerTest,FoodItemServiceImplTest,OpenFoodFactsServiceImplTest" test`
- Sonuc: 26 test gecti, 0 failure, 0 error.

## 2026-05-19 - Rate Limiting Ilk Koruma Katmani

### Yapilanlar

- In-memory fixed-window rate limiter eklendi.
- `RateLimitingFilter` security zincirine eklendi.
- Su auth endpointleri rate limit kapsaminda korumaya alindi:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/auth/password-reset/request`
  - `POST /api/auth/email-verification/resend`
- Limit config ile yonetilebilir hale getirildi:
  - `GRUN_RATE_LIMIT_ENABLED`
  - `GRUN_RATE_LIMIT_AUTH_MAX_REQUESTS_PER_MINUTE`
- Limit asildiginda API `429 Too Many Requests` ve standart `ApiErrorResponseDto` doner.
- Swagger auth endpointlerine `429` response dokumani eklendi.

### Karar

- Ilk asamada dependency eklemeden in-memory cozum kullanildi.
- Production ortaminda birden fazla backend instance calisacaksa limiter Redis gibi paylasimli bir store'a tasinmali.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=RateLimitingFilterTest,AuthControllerTest" test`
- Sonuc: 13 test gecti, 0 failure, 0 error.

## 2026-05-19 - API v1 Versioning Alias

### Yapilanlar

- Tum mevcut REST controller base pathleri `/api/v1/...` alias'i ile genisletildi.
- Legacy `/api/...` pathleri korunarak mevcut local Swagger akislarinin ve eski client denemelerinin kirilmamasi saglandi.
- Security config icinde `/api/v1/auth/**` public auth path olarak eklendi.
- Rate limiting korumasi `/api/v1/auth/...` endpointleri icin de aktif hale getirildi.
- README icine API versioning notu eklendi.

### Karar

- Mobil uygulama yeni entegrasyonlarda `/api/v1/...` kullanacak.
- Geriye uyumluluk icin `/api/...` pathleri simdilik korunacak.
- Gelecekte kirici kontrat degisikligi gerekirse `/api/v2/...` acilacak; her kucuk backend degisikligi yeni versiyon gerektirmeyecek.

### Dogrulama

- `AuthControllerTest` icine `/api/v1/auth/refresh` testi eklendi.
- `RateLimitingFilterTest` icine `/api/v1/auth/refresh` rate limit testi eklendi.
- Komut: `.\mvnw.cmd "-Dtest=AuthControllerTest,RateLimitingFilterTest,FoodLogsControllerTest,FoodItemControllerTest,AdminUserControllerTest" test`
- Sonuc: 27 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 132 test gecti, 0 failure, 0 error.

## 2026-05-16 - Password Reset ve Admin Dashboard Canli Dogrulama

### Yapilan Kontroller

- Lokal API uzerinde OpenAPI dokumani kontrol edildi:
  - `/api/auth/password-reset/request`
  - `/api/auth/password-reset/confirm`
  - `/api/admin/dashboard/summary`
- Lokal PostgreSQL Flyway history uzerinde `V11 - add password reset tokens` migration success olarak dogrulandi.
- Password reset request endpointi canli API uzerinde generic response dondu.
- Demo kullanici icin hash'li gecici reset token ile confirm endpointi canli test edildi.
- Yeni sifreyle login basarili oldu.
- Demo kullanicinin sifresi tekrar eski demo sifreye alindi ve login basarili oldu.
- Admin dashboard summary endpointi local admin token ile canli test edildi.

### Ek Duzeltme

- Expired password reset token kullanildiginda token `usedAt` isaretlemesinin exception rollback nedeniyle kaybolmamasi icin confirm transaction davranisi guncellendi.

### Dogrulama

- Canli API kontrolleri basarili.
- Canli testte olusan gecici demo password reset tokenlari lokal DB'den temizlendi.
- Komut: `.\mvnw.cmd "-Dtest=PasswordResetServiceImplTest" test`
- Sonuc: 4 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 105 test gecti, 0 failure, 0 error.

## 2026-05-16 - Local Swagger Demo Flow Dokumani

### Yapilanlar

- Yeni dokuman eklendi:
  - `docs/LOCAL_SWAGGER_DEMO_FLOW.md`
- Dokumanda lokal Swagger deneme akisi tanimlandi:
  - demo user login
  - product search
  - dashboard daily summary
  - admin login
  - admin review queue
  - product review update
  - audit history kontrolu

### Karar

- Demo seed artik sadece veri olusturan bir mekanizma degil; Swagger uzerinden izlenebilir bir demo akisiyle birlikte tutulacak.
- Bu dokuman proje kontrolunu geri kazanmak icin referans akis olarak kullanilacak.

### Dogrulama

- Dokuman degisikligidir; ek test gerektirmez.

## 2026-05-16 - Local Demo Admin Review Product

### Yapilanlar

- Local demo seed admin review kuyruğu icin raw demo product olusturacak sekilde genisletildi.
- Seed edilen demo review product:
  - `GRun Demo Raw Protein Bar`
  - `normalizedBarcode=8690000000042`
  - `verificationStatus=RAW_IMPORTED`
  - `imageStatus=NEEDS_REVIEW`
  - `reviewPriority=200`
- Product `normalizedBarcode` uzerinden idempotent olarak guncellenir; tekrar calisma duplicate uretmez.

### Canli Dogrulama

- API local demo seed acik sekilde yeniden baslatildi.
- Admin user ile login olundu.
- Admin review endpointi cagrildi:
  - `GET /api/admin/products/review?verificationStatus=RAW_IMPORTED&imageStatus=NEEDS_REVIEW&page=0&size=20`
- Response icinde `GRun Demo Raw Protein Bar` dogrulandi.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=LocalDemoSeedConfigTest" test`
- Sonuc: 2 test gecti, 0 failure, 0 error.

## 2026-05-19 - Goal Calorie Calculation ve Onboarding Request Ayrimi

### Yapilanlar

- Gunluk kalori ve makro hesaplama servisi deterministik testlerle dogrulandi.
- Kullanilan hesaplama yaklasimi netlestirildi:
  - Body fat varsa Katch-McArdle BMR formulu.
  - Body fat yoksa Mifflin-St Jeor BMR formulu.
  - Aktivite seviyesine gore TDEE carpani.
  - Hedef tipine gore kalori acigi/fazlasi.
- `LOSE_WEIGHT` icin haftalik kilo degisim hedefi pozitif gonderilse bile backend tarafinda kalori acigi olarak normalize edildi.
- Goal hesaplama request'i `UserGoalDto` icinden ayrildi ve `GoalCalculationRequestDto` eklendi.
- `/api/goals/calculate` ve `/api/goals/save` artik kullanicidan hesaplanmis kalori/makro degerleri istemiyor.
- Goal save akisi artik DB'ye client tarafindan gelen kalori/makro degerlerini degil, backend tarafinda hesaplanan degerleri kaydediyor.

### Karar

- Onboarding akisi icin kullanici sadece hedef bilgilerini ve aktivite seviyesini gondermeli.
- Kalori, protein, yag ve karbonhidrat hedefleri backend tarafinda hesaplanip kaydedilmeli.
- Bu ayrim mobil app tarafinda daha temiz onboarding ekrani kurulmasini saglayacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=UserGoalServiceImplTest,UserGoalControllerTest" test`
- Sonuc: 13 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 138 test gecti, 0 failure, 0 error.

## 2026-05-19 - Mobil Onboarding Flow, Goal Read Endpoint ve Profile Update Guclendirme

### Yapilanlar

- Mobil onboarding icin tek request ile calisan yeni endpoint eklendi:
  - `POST /api/v1/onboarding/complete`
  - Legacy alias: `POST /api/onboarding/complete`
- Onboarding complete akisi tek transaction icinde:
  - kullanici profilini gunceller,
  - hedef bilgisine gore kalori/makro hedefini hesaplar,
  - hesaplanan hedefi DB'ye kaydeder,
  - mobil UI icin profile, saved goal ve calculation response doner.
- Yeni DTO'lar eklendi:
  - `OnboardingCompleteRequestDto`
  - `OnboardingCompleteResponseDto`
- Yeni servis eklendi:
  - `OnboardingService`
  - `OnboardingServiceImpl`
- Goal okuma endpointi eklendi:
  - `GET /api/v1/goals/me`
  - Legacy alias: `GET /api/goals/me`
- Kayitli goal yoksa endpoint `204 No Content` doner.
- Profile update akisi `bodyFat` ve `bmi` alanlarini da guncelleyebilir hale getirildi.
- Onboarding request validation mesajlari TR/EN message bundle icine eklendi.

### Karar

- Register/login sonrasi mobil uygulama kullaniciyi onboarding complete endpointine yonlendirecek.
- Profil ve hedef setup'i daginik endpointlerle tamamlanmak zorunda kalmayacak.
- Kalori/makro hedefleri client tarafindan girilmeyecek; backend hesaplayip kaydedecek.
- Mobil ana ekran hedefleri okumak icin `GET /api/v1/goals/me` kullanabilecek.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=OnboardingControllerTest,OnboardingServiceImplTest,UserGoalControllerTest,UserGoalServiceImplTest,UserControllerTest,UserServiceImplTest" test`
- Sonuc: 29 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 146 test gecti, 0 failure, 0 error.

## 2026-05-20 - Dashboard Ana Ekran Akisi ve Reklam Karari

### Yapilanlar

- Dashboard daily summary response mobil ana ekran icin genisletildi:
  - `netCalories`
  - `calorieProgressPercent`
  - `remainingProtein`
  - `remainingFat`
  - `remainingCarbs`
  - `proteinProgressPercent`
  - `fatProgressPercent`
  - `carbsProgressPercent`
  - `hasActiveGoal`
  - `onboardingCompleted`
- Dashboard artik onboarding sonrasi kullanicinin ana ekranda ihtiyac duyacagi hedef, kalan kalori, net kalori ve makro ilerleme alanlarini tek response icinde doner.
- Local Swagger demo flow dokumani yeni onboarding ve dashboard akisini icerecek sekilde guncellendi.

### Urun Kararlari

- Reklam yonetimi ana gelir modeli olarak ele alinmayacak.
- Reklam modulu dusuk oncelikli kalacak.
- Standart kullanici icin reklam kullanilacaksa:
  - sinirli ve kontrollu olacak,
  - kritik log/arama/kalori goruntuleme akislarini bolmeyecek,
  - tam ekran interstitial varsayilan strateji olmayacak,
  - premium kullanici deneyimi reklamsiz olacak.
- Ana gelir modeli icin freemium/premium paket daha mantikli kabul edildi.
- Profilde kilo, boy, yas veya cinsiyet degisirse kayitli hedef otomatik degistirilmeyecek.
- Bu durumda mobil uygulama kullaniciya hedefi yeniden hesaplatma ve onaylama akisi sunmali.
- Backend tarafinda `calculate` ve `save` ayrimi korundugu icin bu onayli yeniden hesaplama akisina hazir durumdayiz.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=DashboardServiceImplTest,DashboardControllerTest,OnboardingControllerTest,OnboardingServiceImplTest,UserGoalServiceImplTest,UserGoalControllerTest" test`
- Sonuc: 24 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 147 test gecti, 0 failure, 0 error.
- PostgreSQL ve lokal API uzerinde canli test yapildi.
- Test edilen akistan donen ana degerler:
  - `onboardingCompleted=true`
  - `goalCalories=2242`
  - `dashboardHasActiveGoal=true`
  - `dashboardTargetCalories=2242`
  - `dashboardRemainingCalories=2242.0`
  - `dashboardNetCalories=0.0`

## 2026-05-20 - Profile Goal Recalculation Signal

### Yapilanlar

- `UserProfileDto` response'una mobil uygulama icin iki yeni alan eklendi:
  - `goalRecalculationRecommended`
  - `goalRecalculationReason`
- `PUT /api/v1/users/me` ile kalori hesabini etkileyen profil alanlari degistiginde backend artik sadece yeniden hesaplama onerisi doner:
  - `gender`
  - `age`
  - `height`
  - `weight`
  - `bodyFat`
- Kayitli goal otomatik degistirilmez.
- Onboarding complete akisi ayni transaction icinde goal hesaplayip kaydettigi icin response icinde `goalRecalculationRecommended=false` doner.

### Karar

- Profil degisikligi hedef kaloriyi sessizce degistirmeyecek.
- Mobil uygulama, bu sinyali gordugunde kullaniciya hedefleri yeniden hesaplatma/onaylama akisi sunacak.
- Kullanici onay verdikten sonra mevcut `calculate` ve `save` goal akisi kullanilacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=UserServiceImplTest,UserControllerTest,OnboardingServiceImplTest,OnboardingControllerTest,DashboardServiceImplTest,DashboardControllerTest" test`
- Sonuc: 15 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 147 test gecti, 0 failure, 0 error.
- PostgreSQL ve lokal API uzerinde canli test:
  - `PUT /api/v1/users/me` ile `weight=83.0` gonderildi.
  - Response: `goalRecalculationRecommended=true`.
  - Kayitli goal kalorisi degismedi: `beforeGoalCalories=2242`, `afterGoalCalories=2242`.
  - Demo user agirligi tekrar `82.0` degerine alindi.

## 2026-05-20 - Mobile App Startup State Endpoint

### Yapilanlar

- Mobil login sonrasi ilk routing kararini kolaylastirmak icin yeni endpoint eklendi:
  - `GET /api/v1/app/startup`
  - Legacy alias: `GET /api/app/startup`
- Yeni DTO eklendi:
  - `AppStartupDto`
- Yeni servis eklendi:
  - `AppStartupService`
  - `AppStartupServiceImpl`
- Response icinde su bilgiler tek seferde doner:
  - `profile`
  - `goal`
  - `profileComplete`
  - `hasActiveGoal`
  - `onboardingCompleted`
  - `emailVerified`
  - `dashboardReady`
  - `nextStep`

### Karar

- Mobil uygulama login/register sonrasi once `GET /api/v1/app/startup` cagiracak.
- `nextStep=VERIFY_EMAIL` ise email verification ekrani/uyarisi gosterilecek.
- `nextStep=COMPLETE_ONBOARDING` ise onboarding complete akisi baslatilacak.
- `nextStep=OPEN_DASHBOARD` ise ana dashboard acilabilecek.
- Dashboard endpoint'i gunluk takip verisi icin kalacak; startup endpoint'i routing/state kararindan sorumlu olacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AppStartupServiceImplTest,AppStartupControllerTest" test`
- Sonuc: 4 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 151 test gecti, 0 failure, 0 error.

## 2026-05-20 - Email Verification Login Error Semantics

### Yapilanlar

- Dogrulanmamis email ile login denemesi icin ozel exception eklendi:
  - `EmailNotVerifiedException`
- Global error handler icinde bu durum `403 Forbidden` olarak doner hale getirildi.
- Login Swagger dokumani `403 Email address is not verified` response'unu gosterir hale getirildi.
- EN/TR message bundle icine email dogrulanmadi hata basligi eklendi.

### Karar

- Email dogrulama zorunlu bir hesap durumu olarak ele alinacak.
- Dogrulanmamis email ile login teknik olarak hatali request degil; hesap dogrulanmadigi icin erisim engeli olarak `403` donecek.
- Mobil uygulama bu durumda resend/confirm email verification akisini gosterecek.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AuthControllerTest,AppStartupServiceImplTest,AppStartupControllerTest" test`
- Sonuc: 19 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 153 test gecti, 0 failure, 0 error.

## 2026-05-20 - Food Database ve Subscription Oncelik Karari

### Karar

- Gercek mail provider, production deployment, privacy/data delete ve production security sertlestirme ilerleyen fazlara birakildi.
- Frontend/mobile tasarim kullanici tarafinda paralel ilerleyebilir; backend bu surecte flow ve veri omurgasini tamamlamaya odaklanacak.
- Yakin odak iki is kolu olarak belirlendi:
  - Food database import ve buyutme stratejisi.
  - Subscription/premium paket modeli.
- Food database icin hedef:
  - kendi DB'mizde en az 10.000 temel urun bulunmasi,
  - lokal DB'de olmayan urunlerde Open Food Facts fallback akisinin devam etmesi,
  - admin review/image kalite akisinin korunmasi.
- Subscription icin hedef:
  - once paket/entitlement modeli netlestirilecek,
  - payment provider entegrasyonu daha sonra ele alinacak.

## 2026-05-20 - Admin Food Product CSV Import

### Yapilanlar

- Admin tarafinda CSV ile toplu food product import endpoint'i eklendi:
  - `POST /api/v1/admin/products/import`
  - Legacy alias: `POST /api/admin/products/import`
- Yeni DTO'lar eklendi:
  - `FoodProductImportResultDto`
  - `FoodProductImportErrorDto`
- Yeni servis eklendi:
  - `FoodProductImportService`
  - `FoodProductImportServiceImpl`
- Import davranisi:
  - CSV icin `barcode` ve `name` header'lari zorunlu.
  - `productName` ve `product_name` alternatif isim header'i olarak kabul edilir.
  - Barcode normalize edilir.
  - Mevcut `normalizedBarcode` bulunursa yeni duplicate olusturulmaz, mevcut urun guncellenir.
  - Yeni urunler `ADMIN_IMPORT` data source ile kaydedilir.
  - Import edilen urunler varsayilan olarak `VERIFIED` kabul edilir.
  - Display image varsa image status `APPROVED`, yoksa `NEEDS_REVIEW` olur.
  - Hatali satirlar import sonucunda `errors` listesine yazilir.

### Desteklenen CSV Alanlari

- `barcode`
- `name`, `productName`, `product_name`
- `calories`
- `protein`
- `fat`
- `carbs`
- `fiber`
- `sugar`
- `sodium`
- `serving_size_grams`
- `serving_unit`
- `image_url`
- `external_image_url`
- `display_image_url`
- `image_source`
- `image_status`
- `allergens`
- `nutri_score`

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductImportServiceImplTest,AdminFoodProductReviewControllerTest" test`
- Sonuc: 10 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 156 test gecti, 0 failure, 0 error.

## 2026-05-21 - Food Database Aktif Odak Karari

### Karar

- Subscription ve AI quota implementasyonu bu fazda aktif sprint disina alindi.
- Backend gelistirme odagi food database'i kontrollu sekilde buyutmek olarak guncellendi.
- Yakin hedef kendi DB'mize ilk etapta en az 10.000 kullanilabilir food product kaydi almaktir.
- Open Food Facts barkod fallback akisi korunacak; toplu urun cekme icin API'yi tek tek zorlamak yerine bulk export veya normalize edilmis CSV akisi tercih edilecek.
- Mevcut admin CSV import endpoint'i ilk toplu yukleme yolu olarak kullanilacak.

### Siradaki Teknik Adimlar

1. Toplu veri kaynagi ve alinacak kolonlari netlestir.
2. Kaynak veriyi GRun CSV import kolonlarina normalize et.
3. Pilot CSV ile lokal PostgreSQL importunu dogrula.
4. +10.000 urunluk ilk kontrollu importu calistir.
5. Duplicate, verification status, image status ve review queue sonucunu kontrol et.

## 2026-05-21 - Raw External Food Import Pilot

### Yapilanlar

- Admin food CSV import akisi iki moda ayrildi:
  - `CURATED_ADMIN`
  - `RAW_EXTERNAL`
- Varsayilan import modu `CURATED_ADMIN` olarak korundu.
- Open Food Facts veya benzeri ham dis veri icin `RAW_EXTERNAL` modu eklendi.
- `RAW_EXTERNAL` import edilen urunleri:
  - `dataSource=OPEN_FOOD_FACTS`
  - `verificationStatus=RAW_IMPORTED`
  - `imageSource=OPEN_FOOD_FACTS`
  - `imageStatus=NEEDS_REVIEW`
  durumunda saklar.
- Ham dis veri importu existing curated/admin/manual urun metadata'sini dusurmez.
- Pilot CSV eklendi:
  - `docs/samples/open-food-facts-pilot-import.csv`

### Canli Pilot Akis

- Lokal PostgreSQL ve local API uzerinde 3 satirlik pilot CSV import edildi.
- Import sonucu:
  - `totalRows=3`
  - `insertedRows=2`
  - `updatedRows=1`
  - `skippedRows=0`
- Import edilen `Coca-Cola Original Taste` urunu standart demo kullanici akisinda dogrulandi:
  - product search ile bulundu,
  - barcode lookup ile local kayit dondu,
  - raw review state korundu: `RAW_IMPORTED` ve `NEEDS_REVIEW`,
  - `SERVING` log ile `330` gram normalize edildi,
  - gunluk stats kalorisi `138.6` olarak hesaplandi.
- Admin review queue kontrolunde raw imported ve image review bekleyen kayitlar listelendi.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodProductImportServiceImplTest,AdminFoodProductReviewControllerTest,FoodItemControllerTest,FoodItemServiceImplTest,FoodItemServiceSearchIntegrationTest,FoodLogsControllerTest,FoodLogsServiceImplTest" test`
- Sonuc: 39 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 158 test gecti, 0 failure, 0 error.

## 2026-05-21 - API V1 Tek Kontrat Karari

### Yapilanlar

- REST controller base pathleri tek versioned path ailesine indirildi.
- Unversioned `/api/...` alias'lari controller mappinglerinden kaldirildi.
- Swagger artik endpointleri yalnizca `/api/v1/...` altinda gosterecek.
- Security public auth matcher'i sadece `/api/v1/auth/**` icin tutuldu.
- Test ve aktif kullanim dokumanlari `/api/v1/...` pathlerine guncellendi.

### Karar

- Mobil client ve Swagger kontrati `/api/v1/...` olacak.
- Proje henuz public client bagimliligi olusturmadan once legacy alias tasimayacak.
- Ileride kirici degisiklik gerekirse yeni path ailesi `/api/v2/...` olarak acilacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=AdminDashboardControllerTest,AdminFoodProductReviewControllerTest,AdminUserControllerTest,AppStartupControllerTest,AuthControllerTest,DashboardControllerTest,ExerciseItemControllerTest,ExerciseLogsControllerTest,FoodItemControllerTest,FoodLogsControllerTest,OnboardingControllerTest,ProgressLogControllerTest,UserControllerTest,UserGoalControllerTest" test`
- Sonuc: 73 controller testi gecti, 0 failure, 0 error.
- Canli OpenAPI kontrolu:
  - `totalPaths=39`
  - `v1Paths=39`
  - `legacyPaths=0`
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 158 test gecti, 0 failure, 0 error.

## 2026-05-21 - Open Food Facts Normalize Script

### Yapilanlar

- Open Food Facts bulk export verisini GRun raw import CSV formatina ceviren script eklendi:
  - `scripts/convert-open-food-facts-export.ps1`
- Script varsayilan olarak tab-separated OFF export dosyasi okur.
- Opsiyonel filtreler eklendi:
  - `-RequireCalories`
  - `-RequireMacroData`
  - `-Limit`
  - `-Delimiter`
- Script su davranislari uygular:
  - `code` alanini GRun `barcode` alanina map eder,
  - `product_name` alanini GRun `name` alanina map eder,
  - enerji ve makro alanlarini 100 gram bazli import kolonlarina cevirir,
  - batch icindeki duplicate barcode kayitlarini atlar,
  - barcode veya urun adi olmayan kayitlari atlar,
  - `RAW_EXTERNAL` import endpoint'i icin BOM'suz UTF-8 CSV uretir.
- OFF kaynak ornegi eklendi:
  - `docs/samples/open-food-facts-export-sample.tsv`
- `DATA_SEED_STRATEGY.md` icine script kullanim komutu eklendi.

### Dogrulama

- Ornek OFF TSV dosyasi script ile normalize edildi.
- Script sonucu:
  - `rowsRead=4`
  - `rowsWritten=3`
  - `rowsSkipped=1`
- Uretilen CSV header'i BOM'suz dogrulandi.
- Uretilen CSV lokal API'de `POST /api/v1/admin/products/import?importMode=RAW_EXTERNAL` ile import edildi.
- Import sonucu:
  - `totalRows=3`
  - `updatedRows=3`
  - `skippedRows=0`
  - `errors=0`

### Siradaki Adim

- Gercek Open Food Facts bulk export dosyasindan once 100-500 urunluk filtreli batch uret.
- Bu batch'i local DB'ye import edip review queue ve kullanici search kalitesini kontrol et.
- Batch kalitesi yeterliyse ayni hatla +10.000 urunluk ilk import dosyasini uret.

## 2026-05-21 - Gercek Open Food Facts Batch Pilot

### Yapilanlar

- Open Food Facts exportunun ilk kontrollu gercek parcasi uzerinden 500 urunluk GRun CSV batch'i uretildi.
- Batch lokal API'ye ham dis veri modunda import edildi:
  - `POST /api/v1/admin/products/import?importMode=RAW_EXTERNAL`
- Script kalite filtreleri 10.000 urunluk ilk batch oncesi genisletildi:
  - `-RequireImage`
  - `-RequireKnownNutriScore`
- Bu filtreler mevcut kalori ve makro filtreleriyle birlikte dusuk kaliteli ham urun oranini azaltmak icin kullanilacak.

### Canli Kontrol

- 500 satirlik gercek OFF batch import sonucu:
  - `totalRows=500`
  - `insertedRows=500`
  - `updatedRows=0`
  - `skippedRows=0`
  - `errors=0`
- Standart demo kullanici akisi dogrulandi:
  - `Pinto` aramasi `Pinto Bean` urununu dondurdu,
  - `00001001` barcode lookup `pasta` urununu dondurdu,
  - barcode sonucu `RAW_IMPORTED` ve `NEEDS_REVIEW` durumunu korudu.
- Admin review queue kontrolu:
  - `RAW_IMPORTED` ve `NEEDS_REVIEW` toplam sonucu `504` oldu.

### Kalite Gozlemi

- Ilk 500 urunluk ham batch icinde OFF gorseli olmayan urunler ve `nutri_score=unknown` urunler goruldu.
- Bu batch teknik import hattini dogruladi; ilk 10.000 urunluk batch icin daha secici filtre kullanilacak.

## 2026-05-21 - Gunluk Food Tracking Akisi Guclendirme

### Yapilanlar

- Food log duzeltme endpoint'i eklendi:
  - `PUT /api/v1/food-logs/{id}`
- Duzeltme akisi kullaniciya ait kayitta su alanlari yeniden hesaplayarak gunceller:
  - urun,
  - porsiyon miktari ve birimi,
  - normalize gram,
  - ogun tipi,
  - log tarihi.
- `mealType` kontrati netlestirildi:
  - zorunlu hale getirildi,
  - kabul edilen degerler `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`,
  - servis tarafinda buyuk harfe normalize edilir.
- Food log gecmis endpoint'i eklendi:
  - `GET /api/v1/food-logs/history?start=...&end=...`
  - end date istemci kontratinda inclusive davranir.
- Dashboard daily summary mobil ana ekran icin genisletildi:
  - secilen gunun `foodLogs` listesi response'a eklendi,
  - secilen gunun `exerciseLogs` listesi response'a eklendi.
- Food log get/delete not-found davranisi generic 500 yerine kontrollu not-found exception yoluna alindi.

### Sonuc

- Kullanici urun bulduktan sonra ogune kayit ekleyebilir, yanlis kaydi duzeltebilir veya silebilir.
- Dashboard secilen gunun toplamlarini ve ayni gunun diary kayitlarini tek response'ta tasir.
- Gecmis gun diary kayitlari tarih araligiyla cekilip edit/delete akisina baglanabilir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodLogsServiceImplTest,FoodLogsControllerTest,DashboardServiceImplTest,DashboardControllerTest" test`
- Sonuc: 20 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 163 test gecti, 0 failure, 0 error.
- Canli OpenAPI kontrolunde su kontratlar goruldu:
  - `PUT /api/v1/food-logs/{id}`
  - `GET /api/v1/food-logs/history`
  - `DailySummaryDto.foodLogs`
  - `DailySummaryDto.exerciseLogs`

## 2026-05-21 - Food Tracking Hiz Kisayollari

### Yapilanlar

- Kullaniciya ozel son kullanilan urun endpoint'i eklendi:
  - `GET /api/v1/products/recent?limit=10`
- Son kullanilan urunler food log tarihine gore distinct urun listesi olarak doner.
- `REJECTED` katalog urunleri recent sonucundan cikarilir.
- Mevcut `user_favorites` tablosu kullanici akisi icin acildi:
  - `GET /api/v1/products/favorites`
  - `POST /api/v1/products/{id}/favorite`
  - `DELETE /api/v1/products/{id}/favorite`
- Favorite add davranisi idempotent tutuldu; ayni urune tekrar favorite ekleme duplicate olusturmaz.
- Favorite listesi `REJECTED` urunleri gizler, null veya kullanilabilir verification status kayitlarini dondurur.
- Gunluk ogun ozet endpoint'i eklendi:
  - `GET /api/v1/food-logs/meals?date=...`
- Ogun response'u her gun icin su gruplari dondurur:
  - `BREAKFAST`
  - `LUNCH`
  - `DINNER`
  - `SNACK`
- Her ogun grubunda log listesi ve kalori/protein/yag/karbonhidrat toplami hesaplanir.

### Sonuc

- Mobil food add akisi urun aramadan son kullanilan veya favori urun secimine baglanabilir.
- Gunluk food diary ekrani ogun bazli listeleri ve ogun toplamlarini backend'den hazir alabilir.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodItemControllerTest,UserProductLibraryServiceImplTest,FoodLogsControllerTest,FoodLogsServiceImplTest" test`
- Sonuc: 26 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 169 test gecti, 0 failure, 0 error.
- Canli OpenAPI kontrolunde su endpointler goruldu:
  - `GET /api/v1/products/recent`
  - `GET /api/v1/products/favorites`
  - `GET /api/v1/food-logs/meals`
- Demo kullanici canli kontrolunde recent listesi urun dondurdu ve meal summary response'u dort ogun grubunu dondurdu.

## 2026-05-22 - Copy Meal ve Custom Food Akisi

### Yapilanlar

- Ogun kopyalama endpoint'i eklendi:
  - `POST /api/v1/food-logs/copy-meal`
- Copy request su alanlari alir:
  - `sourceDate`
  - `targetDate`
  - `mealType`
- Kaynak gun ve ogundeki food loglar hedef gune kopyalanir:
  - urun referansi,
  - porsiyon miktari ve birimi,
  - normalize gram,
  - gun icindeki saat bilgisi korunur.
- Kopyalanan kayitlar urun kullanim sayacini normal food log gibi artirir.
- Kullaniciya ait manual/custom food endpointleri eklendi:
  - `POST /api/v1/products/custom`
  - `GET /api/v1/products/custom`
- Custom food sahipligi icin migration eklendi:
  - `V15__add_custom_food_owner.sql`
  - `food_items.created_by_user_id`
- Custom food kurallari:
  - `dataSource=MANUAL`
  - `verificationStatus=VERIFIED`
  - `isCustom=true`
  - olusturan kullaniciya bagli sahiplik
  - global product search sonucundan gizli
  - food log ekleme ve copy flow icinde sadece sahibi tarafindan kullanilabilir.

### Food Diary Note Degerlendirmesi

- `daily note` ve `meal note` ihtiyaci degerlendirildi.
- `ProgressLog.note` food diary icin yeterli kabul edilmedi; baglami farkli.
- Mobil diary UI netlesmeden bu turda yeni note DB modeli eklenmedi.
- Ihtiyac dogrulanirsa once gun bazli food diary note modeli, sonra gerekiyorsa meal note modeli ele alinacak.

### Dogrulama

- Komut: `.\mvnw.cmd "-Dtest=FoodLogsServiceImplTest,FoodLogsControllerTest,FoodItemControllerTest,UserProductLibraryServiceImplTest,FoodItemServiceImplTest" test`
- Sonuc: 41 test gecti, 0 failure, 0 error.
- Komut: `.\mvnw.cmd clean test`
- Sonuc: 173 test gecti, 0 failure, 0 error.
- Canli OpenAPI kontrolunde su kontratlar goruldu:
  - `POST /api/v1/food-logs/copy-meal`
  - `POST /api/v1/products/custom`
  - `GET /api/v1/products/custom`
