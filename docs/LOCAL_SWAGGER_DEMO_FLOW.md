# Local Swagger Demo Flow

Bu dokuman lokal ortamda API'nin uc uca nasil denenebilecegini tanimlar.

## On Kosullar

`.env` icinde lokal profile ve demo seed acik olmali:

```env
SPRING_PROFILES_ACTIVE=local
GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED=true
GRUN_LOCAL_ADMIN_EMAIL=admin@grun.local
GRUN_LOCAL_ADMIN_PASSWORD=LocalAdminPass1!
GRUN_LOCAL_DEMO_SEED_ENABLED=true
GRUN_LOCAL_DEMO_USER_EMAIL=demo.user@grun.local
GRUN_LOCAL_DEMO_USER_PASSWORD=DemoUserPass1!
```

Uygulamayi baslat:

```powershell
.\scripts\run-local.ps1
```

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

## Demo User Akisi

1. Login:
   - Endpoint: `POST /api/v1/auth/login`
   - Body:

```json
{
  "email": "demo.user@grun.local",
  "password": "DemoUserPass1!"
}
```

2. Swagger Authorize:
   - Login response icindeki token'i `Authorize` alanina bearer token olarak gir.
   - Not: Normal register ile olusan yeni kullanicilar email dogrulamasi tamamlanmadan login token alamaz; bu durumda login `403 Email not verified` doner.
   - Demo user lokal seed tarafindan dogrulanmis kabul edilir.

3. Onboarding complete:
   - Endpoint: `POST /api/v1/onboarding/complete`
   - Body:

```json
{
  "name": "Demo User",
  "age": 32,
  "gender": "MALE",
  "height": 180.0,
  "weight": 82.0,
  "bodyFat": 19.2,
  "targetWeight": 78.0,
  "weeklyWeightChangeTargetKg": 0.5,
  "goalType": "LOSE_WEIGHT",
  "activityLevel": "MODERATE"
}
```

   - Beklenen sonuc:
     - `profile` alaninda guncellenen kullanici bilgileri doner.
     - `goal` alaninda backend tarafinda hesaplanip kaydedilen kalori/makro hedefleri doner.
     - `calculation` alaninda mobil UI icin anlik hesaplama sonucu doner.
     - `onboardingCompleted=true` doner.

4. Current goal:
   - Endpoint: `GET /api/v1/goals/me`
   - Beklenen sonuc:
     - Kayitli hedef kalori, protein, yag, karbonhidrat ve hedef kilo doner.
     - Kayitli hedef yoksa `204 No Content` doner.

5. App startup state:
   - Endpoint: `GET /api/v1/app/startup`
   - Beklenen sonuc:
     - `profile` alaninda kullanici profili doner.
     - `goal` alaninda aktif goal doner; goal yoksa `null` olur.
     - `profileComplete`, `hasActiveGoal`, `onboardingCompleted`, `dashboardReady` alanlari mobil routing icin doner.
     - `nextStep` mobil uygulamanin acacagi sonraki ekrani belirtir:
       - `VERIFY_EMAIL`
       - `COMPLETE_ONBOARDING`
       - `OPEN_DASHBOARD`
     - Pratik akista dogrulanmamis yeni kullanici login token alamayacagi icin once email verification confirm/resend akisi tamamlanmalidir.

6. Profile update recalculation signal:
   - Endpoint: `PUT /api/v1/users/me`
   - Body ornegi:

```json
{
  "weight": 83.0
}
```

   - Beklenen sonuc:
     - `goalRecalculationRecommended=true` doner.
     - `goalRecalculationReason` mobil UI icin aciklama doner.
     - Kayitli goal otomatik degismez; kullanici onayindan sonra goal calculate/save akisi calistirilmalidir.

7. Product search:
   - Endpoint: `GET /api/v1/products/search`
   - Query:
     - `q=GRun Demo`
     - `page=0`
     - `size=10`
   - Beklenen sonuc:
     - `GRun Demo Greek Yogurt`
     - `GRun Demo Banana`
     - `GRun Demo Chicken Breast`

8. Dashboard:
   - Endpoint: `GET /api/v1/dashboard/daily-summary`
   - Beklenen sonuc:
     - Demo food logs kalori toplamini gosterir.
     - Demo exercise log yakilan kalori ve sure toplamini gosterir.
     - `foodLogs` ve `exerciseLogs` alanlari secilen gunun diary kayitlarini ayni response icinde dondurur.
     - `hasActiveGoal=true` ve `onboardingCompleted=true` beklenir.
     - `remainingCalories`, `netCalories`, `calorieProgressPercent` ve kalan makro alanlari mobil ana ekran icin doner.

9. Food log correction:
   - Endpoint: `PUT /api/v1/food-logs/{id}`
   - Beklenen sonuc:
     - Yanlis urun, porsiyon, ogun tipi veya log tarihi kullaniciya ait kayit uzerinde duzeltilir.
     - `mealType` sadece `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` degerlerini kabul eder.

10. Food log history:
   - Endpoint: `GET /api/v1/food-logs/history`
   - Query:
     - `start=2026-05-01`
     - `end=2026-05-07`
   - Beklenen sonuc:
     - Tarih araligindaki diary kayitlari log tarihine gore sirali doner.
     - Gecmis gun kayitlari edit/delete islemleri icin id degerleriyle gelir.

11. Food shortcuts:
   - Son kullanilan urunler:
     - Endpoint: `GET /api/v1/products/recent`
     - Query: `limit=10`
   - Favori urunler:
     - Liste: `GET /api/v1/products/favorites`
     - Ekle: `POST /api/v1/products/{id}/favorite`
     - Kaldir: `DELETE /api/v1/products/{id}/favorite`
   - Beklenen sonuc:
     - Mobil food add ekraninda kullanici urun aramadan son kullandigi veya favoriledigi urune ulasir.

12. Meal summaries:
   - Endpoint: `GET /api/v1/food-logs/meals`
   - Query: `date=2026-05-21`
   - Beklenen sonuc:
     - `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` gruplari gelir.
     - Her grup icinde log listesi ve kalori/makro toplami bulunur.

13. Copy meal:
   - Endpoint: `POST /api/v1/food-logs/copy-meal`
   - Body:

```json
{
  "sourceDate": "2026-05-21",
  "targetDate": "2026-05-22",
  "mealType": "BREAKFAST"
}
```

   - Beklenen sonuc:
     - Kaynak gunun secilen ogunundeki food log kayitlari hedef gune kopyalanir.
     - Porsiyon ve gun icindeki saat bilgisi korunur.

14. Custom food:
   - Olustur: `POST /api/v1/products/custom`
   - Listele: `GET /api/v1/products/custom`
   - Minimum body:

```json
{
  "name": "Homemade Lentil Soup",
  "calories": 92.0,
  "protein": 5.8,
  "fat": 2.4,
  "carbs": 12.1,
  "servingSizeGrams": 250.0,
  "servingUnit": "bowl"
}
```

   - Beklenen sonuc:
     - Urun sadece olusturan kullanicinin custom food listesinde gorunur.
     - Bu urun food log ekleme akisinda `foodItemId` ile kullanilabilir.

## Admin Akisi

1. Login:
   - Endpoint: `POST /api/v1/auth/login`
   - Body:

```json
{
  "email": "admin@grun.local",
  "password": "LocalAdminPass1!"
}
```

2. Swagger Authorize:
   - Admin token'i `Authorize` alanina bearer token olarak gir.

3. Review queue:
   - Endpoint: `GET /api/v1/admin/products/review`
   - Query:
     - `verificationStatus=RAW_IMPORTED`
     - `imageStatus=NEEDS_REVIEW`
     - `page=0`
     - `size=20`
   - Beklenen sonuc:
     - `GRun Demo Raw Protein Bar`

4. Product CSV import:
   - Endpoint: `POST /api/v1/admin/products/import`
   - Content type: `multipart/form-data`
   - Form field:
     - `file`: CSV dosyasi
   - Minimum CSV ornegi:

```csv
barcode,name,calories,protein,fat,carbs,display_image_url
3017620422003,Nutella,539,6.3,30.9,57.5,https://cdn.grun.app/products/3017620422003.jpg
8690000000011,GRun Greek Yogurt,65,10,1.5,3.2,
```

   - Beklenen sonuc:
     - Yeni barkodlar insert edilir.
     - Mevcut barkodlar duplicate olusturmadan update edilir.
     - HatalÄ± satirlar `errors` listesinde doner.

5. Product review update:
   - Endpoint: `PATCH /api/v1/admin/products/{id}/review`
   - Body ornegi:

```json
{
  "productName": "GRun Demo Protein Bar",
  "displayImageUrl": "https://cdn.grun.app/demo/protein-bar.jpg",
  "verificationStatus": "VERIFIED",
  "imageSource": "ADMIN_UPLOAD",
  "imageStatus": "APPROVED",
  "reviewNote": "Local demo product reviewed from Swagger."
}
```

6. Audit history:
   - Endpoint: `GET /api/v1/admin/products/{id}/audit`
   - Beklenen sonuc:
     - Review update sirasinda degisen alanlar icin audit kayitlari olusur.

## Notlar

- Demo seed production icin degildir.
- Demo user/product verileri lokal gelistirme ve Swagger denemeleri icindir.
- Mobil uygulama icin onerilen ilk kullanici akisi: login/register -> onboarding complete -> dashboard daily summary.
- Profilde kilo/boy/yas/cinsiyet degisirse hedefler otomatik degistirilmez; mobil taraf kullaniciya hedefi yeniden hesaplatma/onaylama akisi sunmalidir.
- Demo seed tekrar calistiginda ayni demo productlari gunceller, duplicate product uretmez.
- Food/exercise log seed bugunun kayitlari icin idempotent davranir.
- Sadece demo verileri temizlemek icin `.\scripts\cleanup-local-demo.ps1` calistirilabilir.
- Cleanup script PostgreSQL volume'u silmez ve local admin kullaniciyi korur.

