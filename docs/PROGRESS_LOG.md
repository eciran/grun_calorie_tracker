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
