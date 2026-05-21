# Food Product Review Audit History Plan

Bu dokuman admin product review islemlerinde kim, neyi, ne zaman degistirdi sorularini izlemek icin planlanan audit history modelini tanimlar.

## Hedef

Food product katalog kalitesi buyudukce review islemleri izlenebilir olmali. Bir admin urun adini, dogrulama durumunu, gorsel kaynagini veya gorsel durumunu degistirdiginde bu degisiklikler ayri audit kaydi olarak saklanacak.

## Kapsam

Ilk implementasyon sadece admin review update akisini kapsar:

- `PATCH /api/v1/admin/products/{id}/review`
- Product name degisikligi
- Display image URL degisikligi
- Verification status degisikligi
- Image source degisikligi
- Image status degisikligi

Duplicate merge audit'i ikinci adimda ele alinacak. Merge islemi daha fazla veri hareketi yaptigi icin ayri event tipi ve daha zengin payload gerektirir.

## Planlanan Entity

Yeni entity: `FoodProductReviewAuditEntity`

Planlanan tablo: `food_product_review_audits`

Alanlar:

- `id`
- `food_item_id`
- `reviewed_by`
- `action_type`
- `field_name`
- `old_value`
- `new_value`
- `note`
- `created_at`

## Action Type

Yeni enum: `FoodProductReviewAuditAction`

Baslangic degerleri:

- `REVIEW_UPDATE`
- `STATUS_CHANGE`
- `IMAGE_CHANGE`
- `MERGE`

Ilk implementasyonda `REVIEW_UPDATE`, `STATUS_CHANGE` ve `IMAGE_CHANGE` yeterli olacak. `MERGE` sonraki is olarak kalacak.

## Kayit Kurali

Review update sirasinda sadece gercekten degisen alanlar icin audit kaydi olusturulacak.

Ornek:

- Product name ayni ise audit yazilmaz.
- `verificationStatus` `RAW_IMPORTED` -> `VERIFIED` olursa audit yazilir.
- `imageStatus` `NEEDS_REVIEW` -> `APPROVED` olursa audit yazilir.
- `displayImageUrl` bosken yeni URL verilirse audit yazilir.

## Admin Kimligi

Mevcut service metodu su an admin kullanici bilgisini almiyor:

- `updateProductReview(Long id, FoodProductReviewRequestDto request)`

Audit implementasyonu icin iki secenek var:

1. Controller `AuthenticationPrincipal` ile admin email alir ve service'e parametre olarak gecer.
2. Service security context uzerinden authenticated user email okur.

Tercih edilen yol: controller email'i acik sekilde service'e gecer. Bu servis metodunu test etmeyi ve davranisi okumayi daha net yapar.

Planlanan service imzasi:

- `updateProductReview(Long id, FoodProductReviewRequestDto request, String reviewedBy)`

Geriye uyumluluk gerekiyorsa eski metot kisa sure korunup yeni metoda delege edilebilir.

## API

Audit kayitlarini okumak icin admin-only endpoint eklenecek:

- `GET /api/v1/admin/products/{id}/audit?page=0&size=25`

Response paginated olacak.

Planlanan DTO'lar:

- `FoodProductReviewAuditDto`
- `FoodProductReviewAuditPageDto`

Siralama:

- `createdAt DESC`
- `id DESC`

## Migration

Planlanan Flyway dosyasi:

- `V10__add_food_product_review_audits.sql`

Indexler:

- `idx_food_product_review_audits_food_item_created_at`
- `idx_food_product_review_audits_reviewed_by`
- `idx_food_product_review_audits_action_type`

Foreign key:

- `food_product_review_audits.food_item_id -> food_items.id`

Silme davranisi:

- Ilk tercih `ON DELETE CASCADE` degil.
- Product silme/merge operasyonlari audit ile birlikte tasarlanmadikca audit kaydinin silinmesi istenmez.
- Bu nedenle merge audit tasarimi tamamlanana kadar food item fiziksel silme davranislari dikkatle ele alinmali.

## Test Plani

- Review update tek alan degistirirse tek audit kaydi olusur.
- Review update birden fazla alan degistirirse her alan icin audit kaydi olusur.
- Deger ayniysa audit kaydi olusmaz.
- Validation hatasi olursa audit kaydi olusmaz.
- Audit listeleme sadece admin yetkisiyle calisir.
- Audit listeleme paginated response doner.

## Karar

Audit history product quality surecinin parcasidir, user-facing katalog aramasinin parcasi degildir.

Ilk implementasyon minimal tutulacak:

- Entity
- Migration
- Repository
- Service audit yazimi
- Admin audit listeleme endpointi
- Testler

Merge audit, bulk import audit ve external sync audit sonraki adimlarda genisletilecek.

