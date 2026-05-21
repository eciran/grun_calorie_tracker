# Local Demo Seed Plan

Bu dokuman lokal gelistirme icin demo veri ihtiyacini degerlendirir. AmaÃ§ production migration ile demo veri karistirmadan, lokal test ve Swagger denemelerini kolaylastirmaktir.

## Mevcut Durum

Mevcut API ile:

- Standard user `POST /api/v1/auth/register` uzerinden olusturulabilir.
- Food product barcode lookup Open Food Facts fallback ile urun cacheleyebilir.
- Food/exercise log endpointleri authenticated user ile denenebilir.

Eksikler:

- Admin user olusturmak icin public API yok.
- Verified food product olusturmak icin public/admin create endpoint yok.
- Admin review islemleri icin once admin rolune sahip kullanici gerekiyor.

## Karar

Local demo seed hemen production Flyway migration olarak yazilmayacak.

Neden:

- Demo user ve demo product production verisi degildir.
- Admin password/hash gibi degerler migration icinde tutulmamalidir.
- Food product katalog kalitesi admin review akisiyle yonetilmelidir.
- Demo seed, gercek schema migration tarihcesini kirletmemelidir.

## Onerilen Yaklasim

Ileride `scripts/seed-local-demo.ps1` eklenebilir. Bu script sadece lokal calisir.

Planlanan akÄ±s:

1. API ayakta mi kontrol et:
   - `GET /v3/api-docs`
2. Standard demo user register/login:
   - email env ile verilebilir.
   - default lokal email: `demo.user@grun.local`
3. Admin demo user icin iki secenek:
   - Tercih edilen: ileride local-only admin bootstrap endpoint veya profile-based runner.
   - Gecici: local DB uzerinden role update, ancak bu script icinde acikca lokal-only olarak isaretlenmeli.
4. Demo product:
   - Barcode lookup ile Open Food Facts uzerinden cachelenebilir.
   - Admin review audit/validation akisi ayaga kalktiktan sonra verified hale getirilebilir.
5. Demo logs:
   - Standard user token ile food/exercise log requestleri atilabilir.

## Script Guvenlik Kurallari

- Script production ortamda calismayi reddetmeli.
- Script `.env` okusa bile secret yazdirmamali.
- Varsayilan password sadece lokal demo icin kullanilmali.
- Script idempotent olmali:
  - user varsa yeniden olusturmamali.
  - demo product varsa tekrar insert etmemeli.
  - demo loglari tekrar tekrar sismemeli veya opsiyonel reset parametresi istemeli.

## Simdilik Yapilmayanlar

- Admin user seed implementasyonu.
- Verified food product seed implementasyonu.
- Demo log seed implementasyonu.

## Sonraki Teknik On Kosul

Local demo seed script'i yazmadan once su konular netlesmeli:

- Admin user bootstrap sadece lokal profile ile nasil yapilacak?
- Demo veriler nasil temizlenecek?
- Seed script DB'ye direkt mi yazacak, yoksa sadece API mi kullanacak?
- Demo product verified hale getirme akisi audit history ile nasil kaydedilecek?

