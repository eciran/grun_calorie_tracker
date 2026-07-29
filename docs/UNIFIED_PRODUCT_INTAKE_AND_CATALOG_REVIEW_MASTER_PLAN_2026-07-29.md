# Unified Product Intake and Catalog Review Master Plan

**Tarih:** 2026-07-29  
**Durum:** Tasarım tamamlandı; uygulama Sprint 0 ile başlayabilir  
**Kapsam:** Kullanıcı barkod katkısı, cihaz üstü OCR, admin manuel ürün girişi, mevcut ürün düzeltmeleri, görev atama, kanıt inceleme ve katalog yayını  
**İlk pazar:** TR pilotu; veri modeli çoklu pazar için hazırlanacak  
**Ana ilke:** Kullanıcı ve admin iki farklı veri giriş kanalıdır; doğrulama, görev, kanıt, audit ve katalog yayın motoru tektir.

## 1. Yönetici Özeti

GRUN içinde bugün güçlü fakat birbirinden kopuk üç ürün işleme hattı vardır:

1. Kullanıcının tek fotoğrafla gönderdiği `Food Product Contribution` kuyruğu.
2. Kullanıcının mevcut ürün için gönderdiği ve admin tarafında tamamlanmamış kalan düzeltme önerisi.
3. Adminin var olan `FoodItem` kayıtlarını incelediği kapsamlı Product Review ve Quality Workbench.

Yeni sistem bu üç hattı ayrı ayrı büyütmeyecektir. Ortak bir `Product Review Case`
oluşturulacak ve her kaynak aynı ürün çalışma kaydına bağlanacaktır:

```text
Kullanıcı portalı                          Admin portalı
----------------                          -------------
Barkod bulunamadı                         Yeni ürün / araştırma / import
Fotoğraf + cihaz OCR                      Manuel veri + isteğe bağlı kanıt
Kullanıcı düzeltmesi                      Mevcut ürün düzeltmesi
          \                                  /
           \                                /
            ------ Product Review Case -----
                         |
                         v
               Barcode / market resolver
                  /                  \
          Mevcut ürün             İç aday ürün
          için öneri          (kullanıcıya görünmez)
                  \                  /
                   \                /
                Mevcut Product Review Workbench
                         |
                doğrula / düzelt / reddet
                         |
              tek ve kontrollü yayın işlemi
                         |
                    Public catalog
```

Seçilen çözümün temel kararları:

- Sunucuda OCR çalıştırılmayacak.
- Android'de unbundled Google ML Kit Latin OCR, iOS'ta Apple Vision kullanılacak.
- OCR yalnız fotoğraf çekildikten sonra, cihazda ve asenkron çalışacak.
- İki geçici özel kanıt alınacak: ön paket ve besin etiketi.
- Fotoğraflar uygulama backend'inden geçirilmeden özel Cloudflare R2 bucket'ına
  kısa ömürlü imzalı URL ile yüklenecek.
- Backend yalnız bounded doğrulama, metadata, checksum, review ve yayın işlemlerini
  yapacak.
- Kafka, yeni microservice, Lambda, sürekli çalışan OCR worker'ı veya server ML
  modeli kurulmayacak.
- Mevcut Product Review Workbench, evidence, audit, quality, duplicate, alias ve
  suggestion altyapısı yeniden kullanılacak.
- Kullanıcı katkısı onayı hiçbir zaman doğrudan public katalog yazımı anlamına
  gelmeyecek.
- `FoodItem` için açık bir katalog yayın/görünürlük durumu eklenmeden iç aday ürün
  oluşturulmayacak.
- Özel kullanıcı fotoğrafları yalnız `OWNER` ve yetkili `ADMIN_CATALOG` inceleyiciler
  tarafından görülebilecek.

Bu mimari uygulama boyutunu ve backend yükünü düşük tutarken adminin etiketi görüp
doğrulamasını, kullanıcı verisinin kataloğu büyütmesini ve aynı ürün için birden
fazla kanıtın birlikte değerlendirilmesini sağlar.

## 2. Hedefler

### 2.1 Ürün hedefleri

- Barkod bulunamadığında kullanıcıya hızlı ve anlaşılır bir katkı yolu sunmak.
- OCR ile manuel yazma yükünü azaltmak; yanlış veriyi kullanıcıya onaylatmak.
- Kullanıcı katkısını admin kuyruğuna otomatik göndermek.
- Kullanıcıya isterse aynı veriden hemen özel Custom Food oluşturma imkânı vermek.
- Adminin kullanıcı katkısını, kendi manuel girişini ve mevcut ürün düzeltmesini
  aynı çalışma ekranında incelemesini sağlamak.
- Görevli adminin kendi kuyruğunu, atanmış işleri ve SLA durumunu görebilmesini
  sağlamak.
- Onaylanmış katkıları kontrollü şekilde yeni ürüne veya mevcut ürün düzeltmesine
  çevirmek.

### 2.2 Teknik hedefler

- Normal uygulama açılışını OCR ile yavaşlatmamak.
- Android uygulama boyutuna büyük, bundled bir OCR modeli eklememek.
- Backend'e görüntü upload/download proxy yükü bindirmemek.
- Ham fotoğrafı PostgreSQL'de veya container diskinde saklamamak.
- Bir katkı için iki fotoğraf, OCR sonucu, parser sonucu ve kullanıcı düzeltmesini
  ayrı provenance katmanları olarak korumak.
- Public katalog sızıntısını teknik olarak imkânsız kılmak.
- Eski contribution ve correction API'lerini kontrollü ve geriye uyumlu taşımak.
- Fotoğrafları sınırlı süre sonunda otomatik silmek.

### 2.3 Başarı tanımı

Sistem yalnız kod yazıldığında tamamlanmış sayılmaz. Aşağıdakilerin tümü gerekir:

- Kullanıcı barkod bulunamadı akışı mobilde çalışır.
- Android/iOS OCR spike kalite ve performans kapılarını geçer.
- Admin Product Intake kuyruğu ve Product Workbench bağlantısı çalışır.
- İç aday ürün hiçbir user search/barcode endpoint'inde görünmez.
- Katkı tekrar gönderimi idempotenttir.
- Fotoğraf lifecycle, withdrawal ve account deletion testleri geçer.
- ADMIN_READ_ONLY ve ADMIN_SUPPORT özel kanıta erişemez.
- Yayın işlemi quality, evidence ve audit kapılarını atomik uygular.
- Pilot metrikleri ve kill switch hazırdır.

## 3. Kapsam Dışı Kararlar

İlk sürümde aşağıdakiler yapılmayacaktır:

- OCR sonucundan otomatik public ürün yayını.
- Server-side Tesseract, OpenCV veya sürekli cloud OCR.
- Kamera akışının her karesinde gerçek zamanlı OCR.
- Ön paket fotoğrafından AI ile besin değeri tahmini.
- AI tarafından üretilen nutrition değerini kanıtsız kabul etme.
- Fotoğrafların süresiz saklanması.
- Kullanıcı katkısı için zorunlu Custom Food oluşturma.
- Public katalog görseli için örtülü veya birleşik consent.
- Katkı puanı/ödülü; abuse ölçülmeden gamification.
- Yeni message broker, ayrı worker cluster veya yeni microservice.
- Admin web bundle içine büyük OCR/WASM modeli koymak.

## 4. Mevcut Durumun Doğrulanmış Haritası

### 4.1 Kullanıcı tarafında hazır olanlar

- Barkod arama ve ürün bulunamazsa failed scan kaydı:
  - `FoodItemController`
  - `FailedBarcodeScanServiceImpl`
- Manuel katalog arama:
  - `GET /api/v1/products/search`
- Kullanıcıya özel Custom Food CRUD:
  - `/api/v1/products/custom`
- Tek fotoğraflı contribution gönderme:
  - `POST /api/v1/products/contributions`
- Kullanıcının kendi contribution listesini ve kendi kanıtını görmesi.

### 4.2 Mevcut contribution sınırlamaları

- Yalnız bir evidence image destekler.
- OCR, geometry, parser version, field confidence veya correction diff yoktur.
- Nutrition değerleri kullanıcı tarafından tamamen manuel doldurulur.
- Yalnız `PENDING_REVIEW`, `APPROVED`, `REJECTED` durumları vardır.
- Request-better-evidence, withdrawal, expiry ve applied/published durumları yoktur.
- Approval yalnız contribution durumunu değiştirir; `food_items` yazmaz.
- Aynı barkod için yalnız bir approved contribution'a izin veren index vardır.
- Upload ve evidence read bütün dosyayı backend heap'ine alır.
- Otomatik retention/deletion yoktur.
- GDPR export/delete katkı ve evidence objelerini kapsamamaktadır.
- Contribution endpoint'i için özel kullanıcı rate limit'i yoktur.

### 4.3 Admin tarafında hazır olanlar

Mevcut Product Review sistemi korunacaktır. Hazır kabiliyetler:

- Review queue ve filtreler.
- Tam nutrition ve serving düzenleme.
- Product/image approve ve reject.
- Quality issue ve deterministic scan.
- AI/rule suggestion listesi ve güvenli apply.
- Product Quality Workbench:
  - overview;
  - nutrition;
  - names;
  - aliases;
  - serving;
  - evidence;
  - AI;
  - audit.
- Source evidence comparison.
- Duplicate/canonical resolution ve merge.
- Alan bazlı review audit.
- Search cache invalidation.
- Food item üzerinde `reviewAssignee`, `reviewDueAt`, `reviewClaimedAt`.
- Generic FOOD assignment backend endpoint'i.

### 4.4 Admin tarafındaki gerçek boşluklar

- Label Contributions ayrı bir inbox'tır ve Workbench'e bağlı değildir.
- Food assignment alanları DTO, query filtreleri ve product UI içinde kullanılmaz.
- Assignment endpoint'i assignee'nin aktif `ADMIN_CATALOG` olup olmadığını
  doğrulamaz.
- `reviewClaimedAt`, gerçek claim yerine assignment anında set edilmektedir.
- Admin portalında tek ürün için manuel create API/formu yoktur.
- Contribution ekranı yalnız tek fotoğraf ve approve/reject sunar.
- OCR/user/catalog karşılaştırması, duplicate context ve request-better-evidence
  yoktur.
- Read-only UI yazma butonlarını gösterebilir; backend daha sonra 403 döndürür.
- `ADMIN_READ_ONLY` ve `ADMIN_SUPPORT`, `CATALOG_READ` üzerinden private evidence
  GET endpoint'ine erişebilir.
- Mevcut kullanıcı correction suggestion akışının admin list/review/close ekranı
  yoktur.

### 4.5 Kritik yayın açığı

Bugünkü user search ve barcode lookup yalnız `REJECTED` ürünleri kesin olarak
gizler. `RAW_IMPORTED` veya `NEEDS_REVIEW` kayıtlar kullanıcıya görünebilir.

Bu nedenle contribution'dan doğrudan `FoodItem(NEEDS_REVIEW)` yaratmak güvenli
değildir. Hedef tasarımda `verificationStatus` ile yayın durumu ayrılır ve bütün
user-facing read path'ler açık yayın durumunu zorunlu olarak filtreler.

## 5. Değerlendirilen Mimari Seçenekler

| Seçenek | Sonuç | Karar gerekçesi |
|---|---|---|
| Server OCR + fotoğraf saklamama | Reddedildi | Server CPU/RAM, container boyutu ve operasyon artar; admin kanıtı göremez. |
| Cihaz OCR + fotoğrafı hemen silme | Reddedildi | Admin OCR hatası ile kullanıcı hatasını ayıramaz; tekrar araştırma yükü doğar. |
| Cihaz OCR + backend proxy upload | Geçiş desteği | Düşük hacimde çalışır fakat iki fotoğrafla heap, bandwidth ve timeout yükü büyür. |
| Cihaz OCR + private direct object upload | Seçildi | Uygulama/server yükü en düşük, admin doğrulaması korunur. |
| Ayrı ve ikinci bir user review motoru | Reddedildi | Workbench, audit, duplicate ve quality mantığı ikiye bölünür. |
| Ayrı tam product candidate tablosu | Reddedildi | FoodItem nutrition modelini ve Workbench servislerini kopyalar. |
| `FoodItem` + zorunlu publication gate + ortak review case | Seçildi | Tek product modeli ve mevcut Workbench kullanılır; sızıntı yayın durumu ile engellenir. |
| Her katkıyı otomatik publish | Reddedildi | OCR ve kullanıcı verisi authoritative değildir. |
| Yeni queue/microservice | Reddedildi | Pilot hacmi için gereksiz operasyon ve maliyet. |

## 6. Hedef Domain Mimarisi

### 6.1 Product Review Case

`Product Review Case`, portal ve kaynak farkından bağımsız ortak iş kaydıdır.

Kaynak türleri:

- `USER_OCR_NEW_PRODUCT`
- `USER_MANUAL_NEW_PRODUCT`
- `USER_CORRECTION`
- `ADMIN_MANUAL_NEW_PRODUCT`
- `ADMIN_CORRECTION`
- `ADMIN_IMPORT`
- `PROVIDER_IMPORT`

İlk uygulama kullanıcı OCR, kullanıcı correction ve admin manual türlerini tam
destekler. Mevcut import pipeline çalışmaya devam eder ve daha sonra aynı case
modeline adaptörle bağlanabilir.

Bir case şu iki çözümden birine bağlanır:

- `NEW_CANDIDATE`: yeni, yalnız admin tarafından görülebilen FoodItem.
- `UPDATE_EXISTING`: var olan public FoodItem için kanıt ve alan önerileri.

Bir normalized barcode + market için birden fazla case olabilir. Fakat aynı barcode
ve market için en fazla bir açık internal candidate bulunur. Böylece birden fazla
kullanıcı kanıtı aynı ürün çalışma kaydında birleştirilir.

### 6.2 FoodItem yayın durumu

Yeni enum:

```text
CatalogPublicationStatus
  INTERNAL_REVIEW
  PUBLISHED
  PRIVATE_USER
  HIDDEN
```

Anlamları:

- `INTERNAL_REVIEW`: admin workbench aday kaydı; hiçbir kullanıcı endpoint'inde
  görünmez.
- `PUBLISHED`: public search ve barcode lookup için uygundur.
- `PRIVATE_USER`: yalnız sahibi tarafından görülebilen Custom Food.
- `HIDDEN`: reddedilmiş, kaldırılmış veya operasyonel olarak gizlenmiş kayıt.

`VerificationStatus` ayrı kalır:

- `RAW_IMPORTED`
- `NEEDS_REVIEW`
- `VERIFIED`
- `REJECTED`

Yayın ve doğrulama aynı şey değildir. Örnek:

- Mevcut eski imported ürün: `PUBLISHED + RAW_IMPORTED`.
- Yeni kullanıcı adayı: `INTERNAL_REVIEW + NEEDS_REVIEW`.
- Onaylı yeni ürün: `PUBLISHED + VERIFIED`.
- Kişisel ürün: `PRIVATE_USER`.
- Reddedilen ürün: `HIDDEN + REJECTED`.

Tüm mevcut user search, alias search, barcode lookup, cache, fallback ve recommendation
query'leri `CatalogPublicationStatus.PUBLISHED` şartını uygular. Custom Food akışları
yalnız `PRIVATE_USER + owner` şartını uygular.

### 6.3 Review Case tablosu

Önerilen tablo: `food_product_review_cases`

Temel alanlar:

- `id`
- `case_type`
- `source_type`
- `submitted_by_principal_id` nullable
- `submitted_by_actor_type`: `USER`, `ADMIN`, `SYSTEM`
- `normalized_barcode`
- `original_barcode`
- `market_region`
- `food_item_id`
- `resolution_mode`: `NEW_CANDIDATE`, `UPDATE_EXISTING`
- `status`
- `risk_level`: `LOW`, `MEDIUM`, `HIGH`
- `schema_version`
- `submitted_values_json`
- `field_confidence_json`
- `correction_summary_json`
- `nutrition_basis`
- `consent_version`
- `temporary_evidence_allowed`
- `public_media_allowed`
- `review_note`
- `reviewed_by`
- `reviewed_at`
- `applied_at`
- `created_at`
- `updated_at`
- `version`

JSON payload'ları şema versiyonlu ve bounded olacaktır. Barkod, market, durum ve
çalışma ürünü gibi operasyonel filtreler JSON içinde tutulmayacaktır.

### 6.4 Case asset tablosu

Önerilen tablo: `food_product_review_case_assets`

Alanlar:

- `id`
- `review_case_id`
- `upload_session_id`
- `asset_type`
- `storage_key`
- `content_type`
- `size_bytes`
- `width`
- `height`
- `sha256`
- `upload_state`
- `expires_at`
- `deletion_state`
- `deleted_at`
- `deletion_attempt_count`
- `last_deletion_error`
- `created_at`
- `version`

İlk asset türleri:

- `FRONT_PACKAGE`
- `NUTRITION_LABEL`

Gelecekte gerekirse:

- `BARCODE_PANEL`
- `INGREDIENTS_LABEL`

### 6.5 OCR extraction tablosu

Önerilen tablo: `food_product_review_case_extractions`

Alanlar:

- `id`
- `review_case_id` unique
- `engine`
- `engine_version`
- `parser_version`
- `locale`
- `recognized_lines_json`
- `parsed_values_json`
- `parser_warnings_json`
- `raw_payload_expires_at`
- `raw_payload_deleted_at`
- `created_at`

Sınırlar:

- raw OCR payload için maksimum byte boyutu;
- maksimum recognized line sayısı;
- bounding box ve confidence için ortak platform-neutral şema;
- payload hiçbir application log veya genel analytics event'ine yazılmaz.

User-confirmed değerler case içinde authoritative submission snapshot olarak tutulur.
OCR sonucu yalnız karşılaştırma ve parser geliştirme verisidir.

### 6.6 Source evidence bağlantısı

Contribution kabul edildiğinde mevcut `food_product_source_evidence` tablosuna
immutable alan kanıtları yazılır.

Yeni `FoodDataSource` değerleri:

- `USER_SUBMITTED_LABEL`
- `ADMIN_REVIEWED_LABEL`

Kurallar:

- Finalize anında source evidence yazılmaz.
- Admin etiketi kabul ettikten sonra confirmed değerler evidence satırına çevrilir.
- OCR-only değer evidence sayılmaz.
- Reviewer identity, case ID, basis, observed time ve parser/source version saklanır.
- Kullanıcı etiketi mevcut source precedence kurallarını bypass etmez.
- AI, yeterli matching evidence yoksa kesin nutrition değeri öneremez.

### 6.7 Eski modellerin konumu

- `food_product_contributions` yeni case modeline backfill edilir.
- Eski contribution API bir geçiş süresi boyunca v2 case service'ine adaptör olur.
- `ProductCorrectionSuggestionEntity` açık kayıtları case tip
  `USER_CORRECTION` olarak taşınır.
- Yeni yazımlar yalnız ortak case hattına gider.
- Eski tablolar ilk sürümde drop edilmez; en az bir release read compatibility
  korunur.

## 7. Durum Makineleri

### 7.1 Upload session

```text
CREATED
  -> UPLOADING
  -> UPLOADED
  -> FINALIZED

CREATED / UPLOADING / UPLOADED
  -> EXPIRED
  -> CLEANED
```

Finalization idempotency key ile tek sonuç üretir. Signed URL'nin teknik olarak
birden fazla kullanılabilmesi DB slotunun tek finalize edilmesine izin verdiği
anlamına gelmez.

### 7.2 Review case

```text
SUBMITTED
  -> IN_REVIEW
  -> NEEDS_SUBMITTER_ACTION
  -> SUBMITTED

IN_REVIEW
  -> APPROVED
  -> REJECTED

APPROVED
  -> APPLIED

SUBMITTED / NEEDS_SUBMITTER_ACTION
  -> WITHDRAWN

SUBMITTED / NEEDS_SUBMITTER_ACTION / IN_REVIEW
  -> EXPIRED
```

Kullanıcıya gösterilecek metinler:

| Internal state | Kullanıcı etiketi |
|---|---|
| `SUBMITTED` | Gönderildi |
| `IN_REVIEW` | İnceleniyor |
| `NEEDS_SUBMITTER_ACTION` | Daha net fotoğraf gerekli |
| `APPROVED` | Kanıt kabul edildi |
| `APPLIED` | Kataloğa eklendi / ürün iyileştirildi |
| `REJECTED` | Kabul edilmedi |
| `WITHDRAWN` | Geri çekildi |
| `EXPIRED` | Süresi doldu |

`APPROVED`, public yayını garanti etmez. Public etki yalnız `APPLIED` durumunda
kullanıcıya bildirilir.

### 7.3 Candidate ve publication

Yeni ürün:

```text
INTERNAL_REVIEW + NEEDS_REVIEW
  -> INTERNAL_REVIEW + VERIFIED
  -> PUBLISHED + VERIFIED
```

Reddetme:

```text
INTERNAL_REVIEW + NEEDS_REVIEW
  -> HIDDEN + REJECTED
```

Mevcut ürün düzeltmesi:

```text
PUBLISHED ürün
  + review case önerileri
  -> admin seçili alanları uygular
  -> audit + evidence + cache invalidation
  -> ürün PUBLISHED kalır
```

## 8. Barkod Çözümleme ve Duplicate Kuralları

Case finalize edildiğinde tek transaction içinde:

1. GTIN doğrulanır ve normalized barcode hesaplanır.
2. Market uyumluluğu kontrol edilir.
3. Aynı barcode için `PUBLISHED` ürün aranır.
4. Varsa case `UPDATE_EXISTING` olarak ürüne bağlanır.
5. Yoksa aynı barcode + market için açık `INTERNAL_REVIEW` candidate aranır.
6. Varsa case aynı candidate'a bağlanır.
7. Yoksa yeni internal FoodItem candidate yaratılır.
8. Partial unique index yarış durumunda ikinci candidate oluşmasını engeller.
9. Unique conflict alan transaction tekrar resolver çalıştırılarak çözülür.

Önerilen partial unique index:

```text
(normalized_barcode, market_region)
WHERE publication_status = 'INTERNAL_REVIEW'
  AND normalized_barcode IS NOT NULL
```

Bir barcode için birden fazla case ve asset kabul edilir. Mevcut
`one approved contribution per barcode` kısıtı kaldırılır. Aynı kullanıcı, barcode
ve aynı checksum tekrarı yine idempotent/duplicate olarak engellenir.

Barcode aynı fakat marka/kimlik açıkça uyuşmuyorsa otomatik merge yapılmaz. Case
`HIGH` risk ile duplicate/canonical review'e gider.

## 9. Kullanıcı Portalı Tasarımı

Bu repository kullanıcı mobil istemcisini içermemektedir. Mobil uygulama ayrı
repository'de uygulanacak; bu dosyadaki API ve state contract backend handoff'tur.

### 9.1 Product not found ekranı

Sıralama:

1. `Etiketi Tara ve Ürünü Ekle` — önerilen.
2. `Manuel Ara`.
3. `Özel Yiyecek Oluştur`.

`Help us improve` destekleyici açıklama olabilir; ana buton değildir.

Örnek açıklama:

> Ön yüzü ve besin etiketini tara. Bilgileri kontrol ettikten sonra ürün
> GRUN ekibi tarafından incelensin.

### 9.2 Katkı ekranları

Akış mümkün olduğunca dört adımdır:

1. **Ürün önü**
   - Kamera yönlendirmesi.
   - İnsan, adres, fiş ve ilgisiz arka plan uyarısı.
   - Crop, orientation normalize, EXIF/GPS removal.
2. **Besin etiketi**
   - Etiketi kadraja doldurma.
   - Focus ve glare kalite kontrolü.
   - Still image alındıktan sonra OCR.
3. **Bilgileri kontrol et**
   - Product name ve brand.
   - Per 100g / per 100ml / serving basis.
   - Nutrition alanları.
   - Düşük confidence alanlarının belirgin gösterimi.
   - Eksik değerler boş kalır; sıfıra çevrilmez.
4. **Onayla ve GRUN'a gönder**
   - Geçici evidence consent.
   - Public front-image consent ayrı ve opsiyonel.
   - `Bunu özel yiyeceğim olarak da kaydet` seçeneği.

### 9.3 Custom Food davranışı

- Manuel Custom Food yolu fotoğraf veya admin onayı istemez.
- Contribution içindeki opsiyon, confirmed değerleri mevcut Custom Food API'sine
  taşır.
- Contribution finalize ve Custom Food create ayrı idempotent işlemlerdir.
- Biri başarısız olduğunda diğeri duplicate oluşturmadan tekrar denenebilir.
- Custom Food hiçbir zaman public candidate yerine geçmez.

### 9.4 Kullanıcı status ve yeniden gönderim

Kullanıcı `Katkılarım` ekranında:

- ürün adı/barcode;
- gönderim zamanı;
- mevcut durum;
- adminin kullanıcıya açık notu;
- daha iyi fotoğraf istenmişse ilgili asset türü;
- withdrawal uygunluğu;
- katalog etkisi

görür.

`NEEDS_SUBMITTER_ACTION` bildirimi deep link ile yalnız istenen fotoğrafı yeniden
çekme ekranını açar. Yeni gönderim aynı case'e yeni asset version olarak eklenir;
eski asset review geçmişinde kalır ve retention politikasına göre silinir.

## 10. OCR ve Nutrition Parser Tasarımı

### 10.1 Platform kararı

| Platform | Motor | Karar |
|---|---|---|
| Android | Google ML Kit Text Recognition v2 Latin | Unbundled/Google Play Services modeli |
| iOS | Apple Vision text recognition | İşletim sistemindeki cihaz-üstü yetenek |

Ortak çıktı:

```ts
type RecognizedLabelLine = {
  text: string;
  confidence: number;
  boundingBox: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
};
```

### 10.2 Performans kuralları

- OCR normal uygulama startup'ında initialize edilmez.
- Yalnız label contribution akışında lazy-load edilir.
- Kamera frame stream üzerinde çalışmaz.
- Fotoğraflar sırayla işlenir.
- UI thread bloke edilmez.
- Kullanıcı iptal edebilir.
- Model hazır değilse preparation/download durumu gösterilir.
- Her durumda manuel giriş fallback'i bulunur.
- OCR başarısızsa contribution akışı kaybolmaz.

### 10.3 Image hedefleri

| Asset | Maksimum çalışma boyutu | Hedef sıkıştırılmış boyut |
|---|---:|---:|
| Front package | 1280 px | 250–500 KB |
| Nutrition label | 2048 px | 350–700 KB |

Backend hard limitleri bu hedeflerden biraz yüksek fakat bounded olur. Pixel count
ayrıca doğrulanır; yalnız byte limitine güvenilmez.

### 10.4 Parser kuralları

İlk sözlük TR ve EN destekler:

- energy / enerji;
- kcal / kJ;
- fat / yağ;
- saturated fat / doymuş yağ;
- carbohydrate / karbonhidrat;
- sugars / şekerler;
- fibre / lif;
- protein;
- salt / tuz;
- sodium / sodyum;
- per 100 g;
- per 100 ml;
- per serving / porsiyon.

Zorunlu güvenlik kuralları:

- unread/missing değer `0` yapılmaz;
- comma ve dot decimal ayrılır;
- kcal ve kJ ayrı tutulur;
- salt ve sodium aynı alan gibi kopyalanmaz;
- negatif veya non-finite değer reddedilir;
- per-serving değer gram/ml dönüşümü yoksa per-100'e çevrilmez;
- table column geometry kullanılır;
- macro toplamı ve calorie/macro tutarlılığı kontrol edilir;
- basis belirsizse kullanıcı seçim yapmadan devam edemez;
- düşük confidence alan otomatik onaylanmış görünmez;
- kullanıcı final submit öncesi bütün populated alanları görür.

### 10.5 OCR kalite kapısı

Tam rollout öncesi en az 50 TR ve 50 EN gerçek etiketli, versiyonlanmış bir test
seti kullanılır.

Ölçümler:

- critical field precision;
- critical field extraction coverage;
- basis detection accuracy;
- salt/sodium confusion;
- decimal parsing;
- OCR p50/p95 latency;
- peak memory;
- Android binary size delta;
- kullanıcı correction rate;
- manuel girişe göre completion time.

Rollout kararı yalnız toplam OCR karakter doğruluğuna göre verilmez. En önemli
ölçü yanlış alanın güvenli şekilde kullanıcıya gösterilmesi ve contribution
tamamlama süresinin azalmasıdır.

## 11. Admin Portalı Tasarımı

### 11.1 Navigation

`Label Contributions` ekranı `Product Intakes` olarak yeniden konumlanır.

Alt görünümler:

- My queue
- Unassigned
- Needs user action
- High risk
- Overdue
- Approved / applied
- Rejected

Bu ekran ikinci bir product editor değildir. Inbox ve triage ekranıdır; satır
detayı ilgili Product Workbench'i case context ile açar.

### 11.2 Queue sütunları

- Product / barcode / brand
- Source
- Market
- Case count / evidence count
- OCR confidence summary
- Risk
- Assignee
- SLA / evidence expiry
- Status
- Existing product / new candidate
- Last activity

Liste ekranı fotoğraf indirmez. Thumbnail dahi lazy-load edilir.

### 11.3 Workbench contribution sekmesi

Mevcut Workbench'e `Submissions` sekmesi eklenir.

Gösterilecekler:

- front package ve nutrition label;
- zoom/rotate;
- barcode ve market;
- OCR değeri;
- user-confirmed değeri;
- mevcut katalog değeri;
- kullanıcı düzeltmesinin highlight edilmesi;
- field confidence;
- parser/plausibility warning;
- aynı barcode için diğer caseler;
- duplicate/canonical context;
- evidence age ve expiry;
- contributor yerine varsayılan olarak opaque case identity.

### 11.4 Admin aksiyonları

- Claim / release.
- Owner tarafından assign / reassign.
- Save candidate changes.
- Request clearer front image.
- Request clearer nutrition label.
- Attach case to a different existing product.
- Mark evidence accepted.
- Reject case with reason.
- Apply selected fields to existing product.
- Verify candidate.
- Publish candidate.
- Open duplicate/canonical workspace.

`Approve contribution` ve `Publish product` aynı buton olmayacaktır.

### 11.5 Admin manuel ürün akışı

Admin portalına `New product` aksiyonu eklenir:

1. Barcode ve market girilir.
2. Resolver mevcut product/internal candidate durumunu döndürür.
3. Mevcut ürün varsa yeni duplicate oluşturmak yerine Workbench açılır.
4. Yeni ise `ADMIN_MANUAL_NEW_PRODUCT` case ve internal candidate oluşturulur.
5. Admin manuel veri, source URL veya geçici label evidence ekler.
6. Aynı quality/review/publish kapıları uygulanır.

Admin web portalına OCR modeli eklenmez. Admin manuel form, import ve mevcut
araştırma/evidence araçlarını kullanır. Kullanıcı OCR sonucu zaten Workbench'te
görünür.

## 12. Görev Atama ve SLA

Mevcut FoodItem assignment alanları korunup tamamlanır.

Kurallar:

- Bir product çalışma kaydına bağlı birden fazla case aynı assignee altında birlikte
  incelenir.
- `OWNER`, aktif `ADMIN_CATALOG` kullanıcıya assign/reassign/clear yapabilir.
- `ADMIN_CATALOG`, unassigned bir işi kendine claim edebilir.
- Başka adminin aktif işini yalnız OWNER reassign edebilir.
- Assignee backend'de aktif hesap, doğru rol ve gerekli permission ile doğrulanır.
- `reviewClaimedAt` yalnız gerçek claim olduğunda set edilir.
- Assignment zamanı ile claim zamanı ayrıdır.
- Optimistic version/stale guard kullanılır.

Gerekli product DTO ve query filtreleri:

- `reviewAssignee`
- `reviewDueAt`
- `reviewClaimedAt`
- `assignmentState`
- `overdue`

MVP'de round-robin auto assignment veya ayrı scheduler kurulmaz. Manuel assign,
self-claim ve `claim next` yeterlidir. Queue büyüdüğünde ölçüme göre otomasyon
eklenebilir.

## 13. Review Risk ve Yayın Kuralları

### 13.1 Risk skoru

`LOW` örnekleri:

- net iki görsel;
- barcode/identity uyumlu;
- basis belirli;
- critical değerler yüksek confidence;
- az veya hiç kullanıcı düzeltmesi;
- plausibility kontrolleri geçiyor;
- mevcut conflict yok.

`MEDIUM` örnekleri:

- bazı düşük confidence alanlar;
- birden fazla kullanıcı düzeltmesi;
- eksik opsiyonel nutrition;
- serving conversion gereksinimi;
- evidence yaşının SLA'ya yaklaşması.

`HIGH` örnekleri:

- barcode ve ürün kimliği uyuşmazlığı;
- büyük critical nutrition düzeltmesi;
- salt/sodium veya basis belirsizliği;
- mevcut verified ürünle material conflict;
- birbiriyle çelişen caseler;
- duplicate/canonical belirsizliği;
- şüpheli kullanıcı/cihaz davranışı.

Risk skoru review sırasını belirler; hiçbir seviye auto-publish yetkisi vermez.

### 13.2 Yeni ürün yayın kapısı

`publishCandidate` tek application service ve transaction üzerinden çalışır.

Zorunlu koşullar:

- publication status `INTERNAL_REVIEW`;
- verification status `VERIFIED`;
- geçerli normalized GTIN;
- product name ve brand;
- market;
- nutrition basis;
- zorunlu macro değerleri;
- deterministic range ve calorie/macro kontrolü;
- kabul edilmiş ve süresi dolmamış nutrition evidence;
- çözülmemiş blocking quality issue olmaması;
- unresolved barcode/duplicate conflict olmaması;
- yetkili reviewer;
- review note;
- optimistic version eşleşmesi.

Transaction:

1. Candidate row lock alınır.
2. Case ve asset durumları tekrar doğrulanır.
3. Immutable source evidence yazılır.
4. Publication status `PUBLISHED` yapılır.
5. Case `APPLIED` yapılır.
6. Product review audit yazılır.
7. Search/canonical/quality hesapları güncellenir.
8. Search cache invalidation yapılır.
9. Notification after-commit tetiklenir.

Public display image zorunlu değildir. Kullanıcı front image'ının public media
olarak kullanılabilmesi ayrı `public_media_allowed` consent ve ayrı media review
gerektirir.

### 13.3 Mevcut ürüne düzeltme uygulama

- Contribution değerleri mevcut ürünü finalize anında değiştirmez.
- Admin alan bazlı apply seçer.
- Material nutrition farkı için mevcut high-impact confirmation yeniden kullanılır.
- Uygulanan her alan audit ve source evidence üretir.
- Apply sonrası quality issue ve comparison yeniden hesaplanır.
- Case, en az bir kabul edilmiş değişiklik veya doğrulanmış corroborating evidence
  kullandığında `APPLIED` olabilir.

## 14. Object Storage ve Upload Tasarımı

### 14.1 Provider kararı

Tercih:

- Private Cloudflare R2 bucket.
- EU jurisdiction.
- S3-compatible API.
- Public `r2.dev` kapalı.
- Uygulamaya özel access key/secret.
- AWS task credentials ile karışmayan ayrı configuration.

Mevcut AWS SDK S3 dependency protokol client'ı olarak yeniden kullanılabilir.
Bu, Amazon S3 hizmeti kullanıldığı anlamına gelmez.

### 14.2 Object key

Object key ve metadata içinde aşağıdakiler bulunmaz:

- user ID;
- email;
- barcode;
- product name;
- cihaz ID.

Örnek:

```text
pending/2026/07/{random-uuid}
review/2026/07/{random-uuid}
```

### 14.3 Direct upload

1. Client backend'den upload session ister.
2. Backend FRONT_PACKAGE ve NUTRITION_LABEL slotları üretir.
3. Her slot için 5–10 dakika TTL'li signed PUT döner.
4. Content type signed parametrenin parçasıdır.
5. Client sıkıştırılmış dosyayı doğrudan R2'ye yükler.
6. Finalize çağrısı asset checksum/metadata ve structured values taşır.
7. Backend HEAD ve bounded stream doğrulaması yapar.
8. Magic signature, gerçek decode, pixel count, byte limit ve SHA-256 doğrulanır.
9. Pending object review prefix'ine alınır veya managed final key'e bağlanır.
10. Upload slot ve session `FINALIZED` olur.

Signed URL bearer token olarak ele alınır. Kısa TTL, tek slot state'i, exact object
key ve finalization idempotency birlikte uygulanır.

### 14.4 Admin read

- Queue listesinde image fetch yoktur.
- Detail açıldığında permission-checked backend endpoint 1–3 dakikalık signed GET
  üretir.
- Signed URL application log'una yazılmaz.
- Admin browser `Cache-Control: private, no-store` davranışını kullanır.
- İlk pilotta proxy read geçici fallback olabilir; hedef direct signed read'dir.

## 15. Retention ve Silme

Başlangıç politikası:

| Durum | Retention |
|---|---:|
| Incomplete upload | 24 saat hedef; storage backstop en fazla 48 saat |
| Pending/in review | 30 gün |
| Needs submitter action | 30 gün; yalnız bir kayıtlı uzatma ile mutlak sınır içinde |
| Rejected | Karardan sonra 7 gün |
| Withdrawn | Derhal dene; 24 saat içinde sil |
| Applied/published | Apply/publish sonrası 30 gün |
| Raw OCR observations | En fazla evidence retention süresi |
| Mutlak private evidence sınırı | 90 gün |

Uygulama:

- R2 lifecycle 90 günlük hard backstop sağlar.
- `pending/` prefix için daha kısa lifecycle kullanılır.
- Uygulamadaki küçük scheduled cleanup job state'e göre daha erken siler.
- Job küçük batch, idempotent retry ve DB claim/lock ile çalışır.
- Birden fazla ECS replica aynı asset'i eşzamanlı işlememelidir.
- Delete başarısızlığı state ve metric olarak kaydedilir.
- Lifecycle tarafından silinen objeler reconciliation sırasında DB ile eşleştirilir.

Silme sonrası korunabilecek minimum provenance:

- normalized barcode;
- confirmed structured values;
- nutrition basis;
- checksum;
- OCR confidence summary;
- correction summary;
- reviewer decision;
- review/audit timestamps;
- applied product reference.

Public katalog görseli seçilmişse private evidence objesi ömür boyu tutulmaz. Consent
uygunsa optimize edilmiş türev ayrı catalog-media key'ine kopyalanır; iki asset
farklı retention amacına sahiptir.

## 16. Consent, Privacy ve GDPR

Consent'ler ayrılır:

1. **Geçici evidence processing** — contribution için gerekli.
2. **Ürün verisinin katalog geliştirmede kullanımı** — contribution için gerekli.
3. **Ön paket görselinin public katalog medyası olarak kullanımı** — opsiyonel.

Consent version, locale ve kabul zamanı saklanır. Eski
`persistentStorageAllowed=true` davranışı yeni sınırlı retention metniyle
değiştirilir; kalıcı public media consent ile karıştırılmaz.

Gerekli haklar:

- Kullanıcı pending case'i geri çekebilir.
- Kullanıcı kendi case/status verisini görebilir.
- Account export contribution/case metadata'sını içerir.
- Account deletion private assetleri purge queue'ya alır.
- Applied product facts için kişisel bağ kaldırılır; katalog verisinin tutulma
  hukuki metni ayrıca onaylanır.
- Admin varsayılan görünümde contributor email/name görmez.
- Abuse/support investigation için PII erişimi ayrı, audited ve ihtiyaç bazlıdır.

## 17. Yetkilendirme

Yeni permission önerisi:

```text
CATALOG_EVIDENCE_REVIEW
```

Rol matrisi:

| Rol | Queue metadata | Private evidence | Claim/review | Assign/reassign | Publish |
|---|---:|---:|---:|---:|---:|
| OWNER | Evet | Evet | Evet | Evet | Evet |
| ADMIN_CATALOG | Evet | Evet | Evet | Kendine claim | Evet, gate ile |
| ADMIN_READ_ONLY | Sınırlı | Hayır | Hayır | Hayır | Hayır |
| ADMIN_SUPPORT | Hayır veya anonim özet | Hayır | Hayır | Hayır | Hayır |
| Diğer admin roller | Hayır | Hayır | Hayır | Hayır | Hayır |

Route permission yalnız HTTP method'e göre `CATALOG_READ` seçmemelidir. Private
evidence GET isteği de hassas bir review işlemidir.

Admin UI:

- Kullanıcının sahip olmadığı aksiyonu render etmez.
- Read-only modda form alanları ve save/approve/reject butonları kapalıdır.
- Backend her durumda son yetki kaynağıdır.

## 18. Abuse ve Güvenlik Kontrolleri

- Authenticated user zorunlu.
- User/day finalize limiti; başlangıç önerisi 5.
- Upload session/day limiti finalize limitinden biraz yüksek.
- Yeni veya şüpheli hesap için daha düşük dinamik limit.
- Device identifier yalnız abuse hash olarak, süreli ve privacy-reviewed tutulabilir.
- Maximum byte, dimension ve pixel count.
- JPEG, PNG, WebP allowlist.
- File signature ve image decode.
- EXIF/GPS client'ta kaldırılır; backend bunu güven varsaymaz.
- SHA-256 ve duplicate detection.
- Idempotency key.
- Session expiry ve ownership check.
- Signed URL kısa TTL.
- Private bucket.
- No active content formats.
- Optimistic locking.
- Review ve publish audit.
- Inappropriate/unrelated image için reject/escalation reason.
- Ham OCR ve signed URL log redaction.

İlk sürümde ücretli otomatik content moderation servisi zorunlu değildir. Rate
limit, private access ve admin reject ile pilot ölçülür; gerçek abuse hacmi
gerekirse ek hizmet değerlendirilir.

## 19. API Sözleşmesi

Endpoint isimleri uygulama sırasında mevcut REST stiline uyarlanabilir; davranış
contract'ı sabittir.

### 19.1 Kullanıcı API

```text
POST /api/v1/products/review-cases/upload-sessions
POST /api/v1/products/review-cases/{caseId}/finalize
GET  /api/v1/products/review-cases
GET  /api/v1/products/review-cases/{caseId}
POST /api/v1/products/review-cases/{caseId}/withdraw
POST /api/v1/products/review-cases/{caseId}/replacement-assets
```

Upload session response:

```json
{
  "sessionId": "uuid",
  "expiresAt": "2026-07-29T20:00:00Z",
  "slots": [
    {
      "assetType": "FRONT_PACKAGE",
      "method": "PUT",
      "uploadUrl": "<short-lived>",
      "requiredContentType": "image/jpeg",
      "maxBytes": 700000
    },
    {
      "assetType": "NUTRITION_LABEL",
      "method": "PUT",
      "uploadUrl": "<short-lived>",
      "requiredContentType": "image/jpeg",
      "maxBytes": 900000
    }
  ]
}
```

Finalize ana alanları:

```json
{
  "idempotencyKey": "uuid",
  "schemaVersion": 1,
  "barcode": "869...",
  "marketRegion": "TR",
  "productName": "...",
  "brand": "...",
  "nutritionBasis": "PER_100_G",
  "confirmedValues": {},
  "ocr": {
    "engine": "ML_KIT_ANDROID",
    "engineVersion": "...",
    "parserVersion": "...",
    "recognizedLines": [],
    "parsedValues": {},
    "fieldConfidence": {},
    "correctionSummary": {}
  },
  "consents": {
    "version": "...",
    "temporaryEvidenceAllowed": true,
    "catalogDataUseAllowed": true,
    "publicMediaAllowed": false
  }
}
```

### 19.2 Admin API

```text
GET  /api/v1/admin/product-review-cases
GET  /api/v1/admin/product-review-cases/{caseId}
POST /api/v1/admin/product-review-cases/{caseId}/claim
POST /api/v1/admin/product-review-cases/{caseId}/release
PATCH /api/v1/admin/product-review-cases/{caseId}/assignment
POST /api/v1/admin/product-review-cases/{caseId}/request-evidence
POST /api/v1/admin/product-review-cases/{caseId}/approve-evidence
POST /api/v1/admin/product-review-cases/{caseId}/reject
POST /api/v1/admin/products/candidates
POST /api/v1/admin/products/{productId}/publish
POST /api/v1/admin/products/{productId}/review-cases/{caseId}/apply
GET  /api/v1/admin/product-review-cases/{caseId}/assets/{assetId}/read-authorization
```

Mevcut:

- `/api/v1/admin/products/{id}/quality-workbench`
- `/api/v1/admin/products/{id}/review`
- quality suggestion;
- duplicate/canonical;
- aliases;
- audit

endpoint'leri korunur ve case context ile genişletilir.

### 19.3 Backward compatibility

- Eski multipart contribution endpoint bir release deprecated adaptör olarak kalır.
- Tek fotoğraf legacy asset `NUTRITION_LABEL` olarak yorumlanır.
- Eski user contribution list response yeni case status'una map edilir.
- Eski correction suggestion POST yeni `USER_CORRECTION` case oluşturur.
- Eski admin approve endpoint'i doğrudan publish yapmaz; yeni evidence-approve
  davranışına delege edilir.
- Deprecation telemetry olmadan endpoint kaldırılmaz.

## 20. Sistem Yükü ve Maliyet Bütçesi

### 20.1 Mobil

- Android unbundled OCR client'ı seçilir.
- iOS işletim sistemi Vision framework'ü kullanılır.
- Model normal startup'a dahil edilmez.
- İki görsel eşzamanlı işlenmez.
- Full-resolution görsel yalnız OCR için upload edilmez.
- Cache dosyaları submit/cancel/expiry sonrasında silinir.

### 20.2 Backend

Normal contribution başına:

- bir upload-session metadata işlemi;
- iki direct object PUT, backend dışından;
- finalize sırasında iki HEAD ve bir defalık bounded validation stream;
- bir case ve asset transaction;
- review sırasında lazy signed read;
- publish/apply sırasında mevcut quality/evidence/audit işlemleri.

Olmayacak yükler:

- server OCR CPU;
- ML model RAM;
- büyük OCR container image;
- API üzerinden bütün upload/download proxy;
- yeni broker;
- sürekli worker.

### 20.3 Storage hesabı

Yaklaşık aktif private evidence:

```text
günlük finalized contribution
  x ortalama toplam asset MB
  x ortalama retention gün
  / 1024
= aktif GB
```

Örnek, iki asset toplamı ortalama 1.2 MB:

- 100 contribution/gün ve 30 gün ≈ 3.5 GB.
- 1.000 contribution/gün ve 30 gün ≈ 35 GB.

DB yalnız bounded JSON ve metadata sakladığı için ana maliyet object storage'dır.
Gerçek pilot metrikleriyle ortalama byte, GET/PUT sayısı ve accepted contribution
başına maliyet izlenir.

## 21. Observability

Ölçümler:

- barcode not found;
- CTA impression ve start;
- front/nutrition capture completion;
- OCR success/failure;
- OCR p50/p95 by platform/device class;
- model-not-ready fallback;
- parser required-field coverage;
- field correction rate;
- contribution finalize success/retry;
- upload failure ve orphan count;
- queue age;
- assignee workload;
- review p50/p95;
- request-better-evidence rate;
- evidence approval/reject rate;
- apply/publication yield;
- duplicate/conflict rate;
- evidence active bytes;
- expired/deleted/delete-failed;
- per accepted/applied contribution cost.

Loglanmayacaklar:

- raw photo;
- signed URL;
- full OCR text;
- contributor email;
- exact object key;
- gereksiz nutrition payload.

Alert'ler:

- deletion failure backlog;
- orphan upload growth;
- evidence older than absolute max;
- publish without evidence attempt;
- internal candidate user-query leakage test failure;
- queue SLA breach;
- direct upload error spike.

## 22. Migration Stratejisi

Uygulama sırası güvenlik nedeniyle değiştirilemez.

### 22.1 Expand

1. Son kullanılabilir Flyway version belirlenir; uygulanmış migration değiştirilmez.
2. `CatalogPublicationStatus` kolonu nullable eklenir.
3. Existing data backfill:
   - custom -> `PRIVATE_USER`;
   - rejected -> `HIDDEN`;
   - diğer existing catalog rows -> `PUBLISHED`.
4. NOT NULL ve check constraint daha sonra eklenir.
5. Bütün user-facing queries yayın filtresine geçirilir.
6. Search, barcode, aliases, caching, recommendations ve external fallback regression
   testleri geçer.
7. Ancak bundan sonra `INTERNAL_REVIEW` candidate yaratımı açılır.

### 22.2 Yeni case modeli

1. Review case, upload session, asset ve extraction tabloları eklenir.
2. Legacy contribution rows backfill edilir.
3. Legacy single image asset row'a map edilir.
4. Existing OPEN correction suggestions case'e taşınır.
5. Yeni endpoints v2 service'e yazmaya başlar.
6. Old endpoints compatibility adaptörü kullanır.

### 22.3 Contract

- En az bir release dual-read/compatibility.
- Production telemetry old endpoint kullanımını ölçer.
- Sıfır kullanım ve rollback window sonrası legacy write kapanır.
- Eski columns/tables ayrı bir sonraki cleanup sprint'inde kaldırılabilir.

### 22.4 Object migration

Mevcut object key'lerde user ID/barcode bulunabilir. Yeni yazımlar opaque key kullanır.
Legacy objeler:

- erişim devam ettiği sürece managed-prefix allowlist ile okunur;
- retention job ile silinir;
- gereksiz bulk copy yapılmaz;
- uzun süreli korunacak public media varsa consent sonrası ayrı key'e türetilir.

## 23. Sprint Planı

### Sprint 0 — Baseline, contract ve OCR feasibility

Amaç: Kod mimarisini dondurmadan önce gerçek performans ve migration güvenliğini
kanıtlamak.

Backend/admin:

- Mevcut contribution, correction, workbench, search ve permission baseline testleri.
- Review Case API ve state contract'ı.
- Publication status migration taslağı ve tüm user read-path envanteri.
- R2 proof of concept: dedicated client, EU endpoint, presigned PUT/GET.
- Retention ve consent metni için legal handoff.

Mobil:

- Android ML Kit unbundled spike.
- iOS Apple Vision spike.
- Ortak recognized-line adapter.
- İlk TR/EN parser.
- 50 TR + 50 EN etiket corpus planı; en az 25+25 ile ilk ölçüm.
- App size, latency, memory ve manual fallback ölçümü.

Kabul:

- Normal startup regression yok.
- OCR UI thread'i bloklamıyor.
- Android bundled model kullanılmıyor.
- iOS harici OCR modeli bundle edilmiyor.
- Manual fallback çalışıyor.
- R2 direct round-trip private ve kısa ömürlü çalışıyor.
- Bütün user-facing FoodItem query'leri envanterlenmiş.

### Sprint 1 — Publication gate ve katalog izolasyonu

- `CatalogPublicationStatus` migration/backfill.
- Search/barcode/alias/cache/recommendation predicate'leri.
- Custom Food `PRIVATE_USER` davranışı.
- Internal candidate repository ve partial unique index.
- Dedicated `CatalogPublicationService`.
- Publish guard ve audit.
- Clean DB + mevcut snapshot migration testleri.
- Internal candidate leakage integration testleri.

Kabul:

- `INTERNAL_REVIEW` product ID doğrudan bilinse bile user endpoint'leri dönmez.
- Existing public search result sayısı beklenmedik şekilde değişmez.
- Existing custom foods yalnız sahibine görünür.
- Publish dışındaki hiçbir servis publication status'u `PUBLISHED` yapamaz.

### Sprint 2 — Common Review Case ve legacy bridge

- Review case entity/repository/service.
- Upload session, asset ve extraction schema.
- Contribution backfill.
- Correction suggestion migration/adaptör.
- Barcode/market resolver.
- New candidate veya existing update bağlantısı.
- Multiple case per barcode.
- Status transition guard ve optimistic lock.
- Old endpoint compatibility.

Kabul:

- User OCR, user correction ve admin manual case aynı service üzerinden oluşur.
- Duplicate finalize tek case döndürür.
- İki eşzamanlı yeni barcode finalize tek internal candidate üretir.
- Legacy contribution listesi kaybolmaz.
- Approval public ürünü otomatik değiştirmez.

### Sprint 3 — Direct object storage ve retention

- Generic S3-compatible R2 properties:
  - endpoint;
  - jurisdiction;
  - access key;
  - secret;
  - bucket;
  - prefixes.
- Dedicated client ve presigner.
- Direct upload slots.
- Bounded streaming validation.
- Opaque object key.
- Signed admin read.
- R2 lifecycle IaC/runbook.
- Scheduled cleanup/reconciliation.
- Withdrawal purge.
- Account export/delete entegrasyonu.
- Rate limits ve metrics.

Kabul:

- İki fotoğraf backend multipart body'sinden geçmez.
- Invalid mime/signature/dimension/checksum finalize olmaz.
- Expired session finalize olmaz.
- ADMIN_READ_ONLY signed read alamaz.
- Withdrawn asset 24 saat gate'i içinde silinir.
- 90 günden eski private evidence kalmadığı doğrulanabilir.
- Multi-replica cleanup aynı asset'i iki kez işlese bile güvenlidir.

### Sprint 4 — Admin Product Intake ve görev sistemi

- Label Contributions -> Product Intakes UI.
- My queue, unassigned, needs action, high risk, overdue.
- Food assignment DTO/query/UI.
- Active ADMIN_CATALOG assignee doğrulaması.
- Claim/release/reassign.
- `reviewClaimedAt` semantik düzeltmesi.
- Workbench `Submissions` sekmesi.
- İki görsel lazy preview.
- OCR/user/catalog field comparison.
- Risk/warnings/expiry.
- Request-better-evidence.
- Existing product attach.
- Evidence approve/reject.
- Admin manual new product form.
- Action-level permission gating.

Kabul:

- Admin ikinci edit ekranına ihtiyaç duymadan Workbench'te karar verir.
- Owner görev atayabilir; catalog admin kendine claim edebilir.
- Read-only kullanıcı private foto veya write action görmez.
- Request-evidence kullanıcı status'unu ve notification'ı günceller.
- New admin product internal candidate olarak başlar.

### Sprint 5 — Mobil kullanıcı akışı

- Barcode-not-found üç CTA.
- Front ve nutrition capture.
- Crop/resize/orientation/EXIF removal.
- Android/iOS OCR adapter.
- Parser ve warnings.
- Review/correction form.
- Basis seçimi.
- Upload progress/retry.
- App restart draft recovery.
- Explicit submit consent.
- Optional Custom Food.
- My contributions/status.
- Request-better-evidence deep link.
- Withdrawal.
- TR/EN localization ve accessibility.

Kabul:

- OCR/model unavailable durumunda manuel yol tamamlanır.
- App restart duplicate case yaratmaz.
- Low-confidence alan kullanıcıdan gizlenmez.
- Upload retry orphan/duplicate yaratmaz.
- Final submit admin queue'da görünür.
- Optional Custom Food contribution yayınından bağımsız çalışır.

### Sprint 6 — Apply/publish, notification ve quality hardening

- Accepted case -> source evidence.
- Existing product selected-field apply.
- Candidate verify/publish transaction.
- Quality/canonical/search recalculation.
- High-impact confirmation.
- Audit completeness.
- User notification.
- Multiple evidence/corroboration UI.
- Failed scan -> case -> applied product attribution.

Kabul:

- New product yalnız publish service ile görünür.
- Existing product yalnız seçilen alanlarda değişir.
- Her applied field evidence ve audit taşır.
- Search cache stale veri göstermez.
- Kullanıcı yalnız gerçek apply/publication sonrası başarı bildirimi alır.

### Sprint 7 — TR pilotu ve operasyon kapanışı

- Feature flag ve cohort rollout.
- Internal dogfood.
- %1, %10, %50, uygun ise %100 TR rollout.
- OCR parser threshold tuning.
- Admin SLA ve queue capacity ölçümü.
- Retention deletion rehearsal.
- Cost report.
- Abuse review.
- Rollback ve kill-switch rehearsal.
- Production runbook ve handoff.

Pilot stop koşulları:

- internal product leakage;
- evidence authorization açığı;
- deletion backlog;
- unexplained duplicate candidate;
- material nutrition yanlış apply;
- OCR crash/ANR artışı;
- upload error veya admin backlog threshold aşımı.

## 24. Test Stratejisi

### 24.1 Backend

- State transition unit tests.
- Barcode/market resolver tests.
- Idempotent finalize.
- Concurrent candidate creation.
- Publication gate repository/service integration.
- Search/barcode leakage regression.
- Evidence checksum/mime/decode/dimension tests.
- Signed URL ownership/expiry tests.
- Permission matrix tests.
- Account export/delete tests.
- Retention/reconciliation tests.
- Apply/publish transaction rollback tests.
- Audit and cache invalidation tests.
- Legacy API compatibility tests.

### 24.2 Database

- Clean PostgreSQL Flyway chain.
- Existing production-like snapshot upgrade.
- Check constraint and partial unique index.
- Legacy contribution/correction backfill counts.
- Rollback-compatible expand/contract verification.
- Applied migration checksum korunumu.

### 24.3 Admin UI

- Permission-based action visibility.
- Queue filters.
- Assignment/claim.
- Lazy evidence read.
- OCR/user/catalog comparison.
- Request-evidence.
- Existing/new resolution.
- Apply and publish confirmations.
- Read-only 403 UX fallback.
- Production build.

### 24.4 Mobil

- Android/iOS native OCR adapter.
- Camera permission denial.
- Model not ready.
- Offline/manual fallback.
- App restart/resume.
- Upload interruption.
- Duplicate submit.
- Decimal/basis/salt-sodium parsing.
- Low-memory device.
- Accessibility and localization.

### 24.5 Security

- Cross-user case/evidence access.
- READ_ONLY/SUPPORT evidence denial.
- Reused signed URL/finalize slot.
- Oversized/decompression-bomb image.
- Forged content type.
- Object key traversal.
- Log redaction.
- Rate-limit bypass.
- Stale optimistic version.

## 25. Feature Flags ve Rollback

Önerilen flags:

- `product-review-case-v2`
- `product-contribution-direct-upload`
- `product-contribution-on-device-ocr`
- `product-candidate-publication-gate`
- `product-intake-admin-workbench`
- `product-contribution-public-media-consent`

Kill switch:

- Yeni contribution CTA'yı kapatır.
- Existing search/custom food çalışmaya devam eder.
- Mevcut pending caseler admin tarafından incelenebilir.
- Signed upload issuance kapanır; read/delete devam eder.
- Publication gate kapatılamaz; güvenlik invariant'ıdır.

Rollback:

- Additive schema korunur.
- Old endpoint adapter'ı bir release açık kalır.
- Internal candidate hiçbir zaman rollback sırasında public'e çevrilmez.
- Direct upload kapatılırsa bounded legacy proxy yalnız kontrollü pilot fallback
  olabilir.

## 26. Definition of Done

Bu program aşağıdakilerin tamamı kanıtlanmadan DONE değildir:

- [ ] Publication visibility gate bütün user read-path'lerde zorunlu.
- [ ] Common Review Case üç eski hattı birleştiriyor.
- [ ] İki private asset çalışıyor.
- [ ] On-device OCR ve manual fallback çalışıyor.
- [ ] OCR/user/catalog diff admin Workbench'te görünüyor.
- [ ] Product assignment UI ve backend doğrulaması tamam.
- [ ] OWNER/ADMIN_CATALOG dışında private evidence erişimi yok.
- [ ] Request-better-evidence ve user resubmit çalışıyor.
- [ ] Existing product apply ve new candidate publish ayrılmış.
- [ ] Audit/source evidence/cache invalidation atomik.
- [ ] Retention, withdrawal ve GDPR deletion çalışıyor.
- [ ] Rate limit/idempotency/concurrency testleri geçiyor.
- [ ] TR pilot metrics ve cost report mevcut.
- [ ] Rollback/kill-switch rehearsal tamam.

## 27. Uygulama Öncesi Dış Bağımlılıklar

Bunlar mimariyi değiştiren açık sorular değildir; uygulama için gerekli dış
hazırlıklardır:

1. Cloudflare hesabı, private R2 EU-jurisdiction bucket ve secret ownership.
2. Production CORS ve lifecycle configuration.
3. Geçici evidence ve opsiyonel public media için hukuk/privacy metni.
4. Minimum iOS sürümü ve Vision language/device test matrisi.
5. TR pilot admin sorumluları ve hedef review SLA.
6. Mobil repository'de native module uygulanacak branch/release takvimi.

Varsayılan SLA önerisi:

- İlk admin açılışı: 3 iş günü.
- Normal karar: 7 gün.
- User-action response sonrası yeniden inceleme: 3 iş günü.
- Evidence mutlak sınırı: 90 gün.

## 28. Resmi Teknik Referanslar

- Google ML Kit Android Text Recognition v2:
  https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Google ML Kit model installation paths:
  https://developers.google.com/ml-kit/tips/installation-paths
- Apple Vision text recognition:
  https://developer.apple.com/documentation/vision/recognizing-text-in-images
- Cloudflare R2 presigned URLs:
  https://developers.cloudflare.com/r2/api/s3/presigned-urls/
- Cloudflare R2 object lifecycles:
  https://developers.cloudflare.com/r2/buckets/object-lifecycles/
- Cloudflare R2 data location and EU jurisdiction:
  https://developers.cloudflare.com/r2/reference/data-location/
- Cloudflare R2 pricing:
  https://developers.cloudflare.com/r2/pricing/

## 29. Nihai Mimari Kararı

GRUN, kullanıcı ve admin ürün girişlerini tek `Product Review Case` hattında
birleştirecektir.

- Kullanıcı portalı fotoğraf, cihaz OCR ve kullanıcı düzeltmesi toplar.
- Admin portalı manuel veri, import, araştırma ve görev yönetimi sağlar.
- Her iki portal aynı barcode resolver, candidate, validation, evidence, audit,
  assignment ve publication servislerini kullanır.
- Yeni ürün çalışma kaydı `FoodItem` üzerinde yalnız `INTERNAL_REVIEW` olarak
  tutulur ve açık publication gate sayesinde kullanıcıya sızmaz.
- Mevcut ürün için contribution doğrudan veri yazmaz; evidence ve field suggestion
  üretir.
- Son karar merkezi mevcut Product Review Workbench'tir.
- OCR cihazdadır; fotoğraflar private R2'de geçici tutulur.
- Backend yalnız bounded validation ve katalog transaction'larını yürütür.
- Hiçbir OCR, kullanıcı veya admin draft verisi yayın servisini bypass edemez.

Bu karar, yeni ve paralel bir product engine kurmadan mevcut yatırımın tamamını
yeniden kullanır; uygulama/server yükünü düşük, kullanıcı deneyimini kısa ve admin
incelemesini kanıt temelli tutar.
