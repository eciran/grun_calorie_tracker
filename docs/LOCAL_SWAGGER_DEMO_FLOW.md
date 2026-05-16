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
   - Endpoint: `POST /api/auth/login`
   - Body:

```json
{
  "email": "demo.user@grun.local",
  "password": "DemoUserPass1!"
}
```

2. Swagger Authorize:
   - Login response icindeki token'i `Authorize` alanina bearer token olarak gir.

3. Product search:
   - Endpoint: `GET /api/products/search`
   - Query:
     - `q=GRun Demo`
     - `page=0`
     - `size=10`
   - Beklenen sonuc:
     - `GRun Demo Greek Yogurt`
     - `GRun Demo Banana`
     - `GRun Demo Chicken Breast`

4. Dashboard:
   - Endpoint: `GET /api/dashboard/daily-summary`
   - Beklenen sonuc:
     - Demo food logs kalori toplamini gosterir.
     - Demo exercise log yakilan kalori ve sure toplamini gosterir.

## Admin Akisi

1. Login:
   - Endpoint: `POST /api/auth/login`
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
   - Endpoint: `GET /api/admin/products/review`
   - Query:
     - `verificationStatus=RAW_IMPORTED`
     - `imageStatus=NEEDS_REVIEW`
     - `page=0`
     - `size=20`
   - Beklenen sonuc:
     - `GRun Demo Raw Protein Bar`

4. Product review update:
   - Endpoint: `PATCH /api/admin/products/{id}/review`
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

5. Audit history:
   - Endpoint: `GET /api/admin/products/{id}/audit`
   - Beklenen sonuc:
     - Review update sirasinda degisen alanlar icin audit kayitlari olusur.

## Notlar

- Demo seed production icin degildir.
- Demo user/product verileri lokal gelistirme ve Swagger denemeleri icindir.
- Demo seed tekrar calistiginda ayni demo productlari gunceller, duplicate product uretmez.
- Food/exercise log seed bugunun kayitlari icin idempotent davranir.
- Sadece demo verileri temizlemek icin `.\scripts\cleanup-local-demo.ps1` calistirilabilir.
- Cleanup script PostgreSQL volume'u silmez ve local admin kullaniciyi korur.
