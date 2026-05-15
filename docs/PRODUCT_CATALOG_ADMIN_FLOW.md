# Product Catalog Admin Flow

Bu dokuman food product catalog icin admin review surecinin sade operasyon akisini tanimlar.

## Amac

GRun kendi urun katalog kalitesini zamanla artiracak. Open Food Facts gibi dis kaynaklardan gelen urunler ham veri olarak kabul edilir ve admin review akisiyle dogrulanir.

## Ana Durumlar

### Product Verification

- `RAW_IMPORTED`: Dis kaynaktan yeni gelen, henuz incelenmemis urun.
- `NEEDS_REVIEW`: Veri kalitesi veya gorsel kalitesi manuel kontrol gerektirir.
- `VERIFIED`: Admin tarafindan dogrulanmis urun.
- `REJECTED`: Katalogda kullanilmasi uygun bulunmayan urun.

### Image Review

- `RAW`: Dis kaynaktan gelen ham gorsel.
- `NEEDS_REVIEW`: Gorsel kalite kontrol bekliyor.
- `APPROVED`: Uygulamada gosterilebilir gorsel.
- `REJECTED`: Kullaniciya gosterilmemesi gereken gorsel.

## Admin Is Akisi

1. Imported urunleri listele:
   - `GET /api/admin/products/review`
   - Varsayilan hedef: `RAW_IMPORTED + NEEDS_REVIEW`

2. Urun detayini kontrol et:
   - product name
   - brand
   - nutrition values
   - allergens
   - ingredients
   - external image
   - duplicate barcode riski

3. Gerekirse urun bilgisini duzelt:
   - `curatedProductName`
   - `displayImageUrl`
   - `verificationStatus`
   - `imageSource`
   - `imageStatus`

4. Gorsel karari ver:
   - Kaliteli ve netse `imageStatus = APPROVED`
   - Kotu veya alakasizsa `imageStatus = REJECTED`
   - Admin tarafindan secilen temiz gorsel varsa `displayImageUrl` set edilir.

5. Urun karari ver:
   - Veri guvenilirse `verificationStatus = VERIFIED`
   - Eksik ama kullanilabilir durumdaysa `NEEDS_REVIEW` kalabilir.
   - Uygun degilse `REJECTED`

6. Duplicate kontrolu yap:
   - `GET /api/admin/products/duplicates`
   - Ayni normalized barcode grubundaki urunler incelenir.

7. Duplicate merge uygula:
   - `POST /api/admin/products/duplicates/merge`
   - Target urun korunur.
   - Food logs ve favorites target urune tasinir.
   - Duplicate urunler silinir.

## API Davranis Kurallari

- Admin review endpointleri sadece `ADMIN` rolune aciktir.
- Kullanici tarafindaki product search, ham imported urunleri gosterebilir; mobil UI gorsel seciminde `displayImageUrl` oncelikli kullanmalidir.
- `displayImageUrl` yoksa mobil uygulama kontrollu fallback veya placeholder kullanmalidir.
- `REJECTED` urunlerin kullanici arama sonucundan dislanmasi ileride ayrica ele alinmalidir.

## Sonraki Teknik Iyilestirmeler

- Review durum gecisleri icin daha kati validasyon kurallari.
- `REJECTED` urunlerin search sonucundan filtrelenmesi.
- Admin review listesinde `dataSource`, `reviewPriority`, `usageCount` ve image status filtrelerinin UI tarafinda belirginlestirilmesi.
- Gorsel kalitesi icin otomatik skor veya boyut kontrolu.
- Urun review audit history modeli.
