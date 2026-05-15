# Data Seed Strategy

Bu dokuman GRun backend icin lokal, staging ve production ortamlarinda baslangic verilerinin nasil yonetilecegini tanimlar.

## Temel Karar

Her veri tipi ayni yolla seed edilmeyecek.

- Kucuk, stabil ve teknik referans veriler Flyway migration ile gelir.
- Buyuk ve sik degisecek katalog verileri Flyway ile doldurulmaz.
- Food product catalog zamanla admin review, barcode lookup, import job ve kullanim verisiyle buyutulur.

## Flyway Ile Seed Edilecek Veriler

Flyway sadece kucuk ve surumle birlikte gelmesi gereken veriler icin kullanilacak.

Uygun ornekler:

- Baslangic exercise catalog kayitlari.
- Stabil lookup/reference verileri.
- Sistem icin zorunlu default ayarlar.

Mevcut ornek:

- `V8__seed_initial_exercise_items.sql`

Kural:

- Seed migration idempotent yazilmali.
- Ayni teknik anahtar varsa tekrar insert etmemeli.
- Production'da manuel degistirilebilecek buyuk veri setleri migration ile ezilmemeli.

## Food Product Catalog Stratejisi

Food product verisi buyuk, degisken ve kalite kontrol gerektiren bir katalogdur. Bu nedenle tum urunleri migration dosyalariyla DB'ye doldurmak dogru degil.

Food product catalog su kaynaklardan buyur:

1. Barcode lookup
   - Kullanici bir barkod aradiginda once local DB kontrol edilir.
   - Yoksa Open Food Facts fallback denenir.
   - Bulunan urun ham veri olarak cache edilir.
   - Status: `RAW_IMPORTED`
   - Image status: `NEEDS_REVIEW`

2. Admin review
   - Admin ham urunleri inceler.
   - Product name, display image, verification status ve image status duzenlenir.
   - Kaliteli urunler `VERIFIED` ve gerekirse `APPROVED` image status alir.

3. Admin import
   - Ileride CSV/XLSX veya provider import job eklendiginde kontrollu batch import yapilir.
   - Import edilen urunler dogrudan verified sayilmaz.
   - Import sonrasi review queue'ya girer.

4. Usage-based priority
   - Kullanilan urunlerin `usageCount` degeri artar.
   - Admin review listesinde cok kullanilan ve dusuk kaliteli urunler daha yuksek oncelik alir.

## Buyuk Katalog Verisi

100 binlerce food product kaydi icin strateji:

- Hepsini ilk gunden lokal DB'ye koymak zorunlu degil.
- Aktif kullanici ihtiyacina gore cache etmek daha kontrollu baslangic saglar.
- Buyuk import gerekiyorsa batch job ile parca parca yapilmali.
- Import edilen her urun kalite status'u ile takip edilmeli.
- Arama performansi icin index ve pagination zorunlu kalir.

## Lokal Gelistirme Verisi

Lokal ortamda iki veri tipi ayrilmali:

- Zorunlu schema/reference verisi:
  - Flyway migration ile gelir.
- Deneme/test verisi:
  - Manuel API requestleriyle veya ileride local-only seed script ile olusturulur.

Lokal-only seed script gerekiyorsa production migration icine konulmayacak.

Planlanan dosya:

- `scripts/seed-local-data.ps1`

Bu script ileride opsiyonel olabilir:

- demo admin user
- demo standard user
- birkac verified food product
- birkac food/exercise log

Bu adim simdilik implement edilmedi.

## Production Ilk Kurulum

Production ilk kurulumda:

1. Flyway schema ve kucuk reference seed migrationlarini calistirir.
2. Food catalog bos veya minimal verified urunlerle baslar.
3. Kullanici lookup yaptikca Open Food Facts fallback urunleri cachelenir.
4. Admin review sureci katalog kalitesini artirir.
5. Buyuk import karari kullanici sayisi, maliyet ve veri kalitesi ihtiyacina gore verilir.

## Neden Tum Veriyi DB'ye Hemen Doldurmuyoruz?

- Veri dogrulugu garanti degil.
- Gorsel kalitesi degisken.
- Buyuk import storage ve review maliyeti uretir.
- Kullanicinin hic aramayacagi urunler gereksiz yer kaplar.
- Kalitesiz veri mobil deneyimi zayiflatir.

## Kisa Vadeli Yol Haritasi

1. Exercise catalog seed Flyway ile devam eder.
2. Food product lookup/cache akisi korunur.
3. Admin review validation kurallari guclendirilir.
4. Review audit history implement edilir.
5. Local-only demo seed script ihtiyaci tekrar degerlendirilir.
6. Buyuk food product import ancak admin review ve audit altyapisi olgunlastiktan sonra tasarlanir.
