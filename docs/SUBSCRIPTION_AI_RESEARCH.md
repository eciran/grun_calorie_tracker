# Subscription ve AI Monetization Arastirma Notlari

Son guncelleme: 2026-05-20

Bu dokuman GRun icin subscription, AI kullanim limiti, credit modeli ve rakip uygulama analizlerini tek yerde tutmak icin olusturuldu. Amac her seferinde yeniden arastirma yapmadan kararlarimizi buradan takip etmek ve uzerine konusabilmek.

## Kapsam ve Not

- App Store ve Google Play siralamalari ulke, cihaz, tarih ve arama terimine gore degisebilir.
- Bu dokumandaki rakip listesi 2026-05-20 tarihinde calorie tracker / nutrition tracker / AI calorie tracker aramalari ve resmi uygulama sayfalari uzerinden cikarilmis bir snapshot'tir.
- App Store ve Google Play'de ayni uygulama varsa tek analiz olarak yazildi.
- Fiyatlar bolgeye, kampanyaya, store komisyonuna ve deneme tekliflerine gore degisebilir.

## Bizim Konustugumuz Ana Kararlar

### Ilk Urun Paketi

Ilk cikis icin kullaniciya donuk modelin sade olmasi daha dogru:

```text
FREE
PRO
```

Backend ise ileride genisleyebilmek icin 3 plan desteklemeli:

```text
FREE
PLUS
PRO
```

PLUS ilk etapta public/published olmak zorunda degil. Ileride reklamsiz kullanim, orta seviye AI limiti, gelismis rapor gibi ara degerler netlesirse aktif edilebilir.

### AI Sınırsiz Olmamali

AI tekil istek maliyeti dusuk olsa bile "sinirsiz AI chat" modeli risklidir. Riskler:

- Kullanici genel ChatGPT gibi amac disi kullanabilir.
- Uzun prompt ve uzun cevaplar maliyeti artirir.
- Kotu niyetli veya otomasyonla cok yogun kullanim olabilir.
- App Store / Google Play komisyonu, vergi, server, DB, storage gibi maliyetler de vardir.

Bu yuzden PRO icin bile backend'de gercek limit veya fair usage olmali.

### AI Chat Yerine Gorev Bazli AI

Ilk surumde acik ve genel AI chat kurmamaliyiz.

Dogru model:

```text
AI Task System
- MEAL_SUGGESTION
- WORKOUT_PLAN
- DAILY_FEEDBACK
- WEEKLY_MEAL_PLAN
- WEEKLY_REPORT
- PROGRESS_ANALYSIS
```

Her task icin:

- izin verilen input alanlari,
- token limiti,
- cevap uzunlugu,
- guvenlik kurallari,
- kota/credit maliyeti,
- log/audit kaydi

ayri tutulmali.

### Domain Guardrail

Kullanici AI'i amac disi kullanmaya calisabilir:

```text
Yatirim tavsiyesi ver
Kod yaz
Siyasi analiz yap
Tibbi tani koy
Genel sohbet et
```

Backend tarafinda AI istekleri sadece nutrition, calorie tracking, workout planning ve app hedefleri kapsaminda kabul edilmeli.

Amac disi isteklerde beklenen cevap:

```text
Bu ozellik yalnizca beslenme, kalori takibi ve antrenman planlama icin kullanilabilir.
Bu konuda yardimci olamam.
```

Bu tur istekler mumkunse kota tuketmemeli veya `BLOCKED` olarak ayri kaydedilmeli.

## Onerilen Credit Modeli

AI'i "istek sayisi" ile degil, credit ile yonetmek daha dogru. Cunku her AI aksiyonu ayni maliyette degil.

Baslangic credit onerisi:

```text
FREE: 5 AI credit / ay
PLUS: 50 AI credit / ay
PRO: 250 AI credit / ay
```

Ilk cikista PLUS kullaniciya gosterilmeyecekse:

```text
FREE: 5 AI credit / ay
PRO: 250 AI credit / ay
```

Task bazli credit onerisi:

```text
AI_DAILY_FEEDBACK = 1 credit
AI_MEAL_SUGGESTION = 2 credit
AI_WORKOUT_DAY_PLAN = 3 credit
AI_WORKOUT_WEEK_PLAN = 5 credit
AI_WEEKLY_MEAL_PLAN = 8 credit
AI_PROGRESS_ANALYSIS = 5 credit
AI_WEEKLY_REPORT = 8-12 credit
```

Bu model Wellz gibi AI odakli uygulamalarda da gorulen credit yaklasimina benzer.

## Euro Fiyatlandirma Onerisi

Ilk cikis icin GRun pahali segmentte baslamamali. Marka, verified food database, AI kalitesi ve mobil deneyim oturdukca fiyat artirilabilir.

Onerilen ilk fiyat:

```text
PRO Monthly: EUR 6.99
PRO Yearly: EUR 49.99
```

PLUS ileride acilirsa:

```text
PLUS Monthly: EUR 3.99
PLUS Yearly: EUR 29.99
```

PRO icin pazarlama dili:

```text
High AI limit
Generous AI usage
Fair usage included
```

Teknik olarak "sinirsiz" olmamali.

## Backend Tasarim Onerisi

Subscription tarafinda entity ve enum yapisi string tabanli olmamali.

Onerilen enumlar:

```text
PlanType
- FREE
- PLUS
- PRO

SubscriptionStatus
- ACTIVE
- TRIALING
- EXPIRED
- CANCELED
- PAST_DUE

BillingPeriod
- MONTHLY
- YEARLY

FeatureKey
- AD_FREE
- ADVANCED_REPORTS
- UNLIMITED_HISTORY
- AI_DAILY_FEEDBACK
- AI_MEAL_SUGGESTION
- AI_WORKOUT_PLANNER
- AI_WEEKLY_REPORT
- DATA_EXPORT
- PREMIUM_EXERCISE_LIBRARY
```

AI kullanimi icin ayrica:

```text
AIRequestType
- DAILY_FEEDBACK
- MEAL_SUGGESTION
- WORKOUT_DAY_PLAN
- WORKOUT_WEEK_PLAN
- WEEKLY_MEAL_PLAN
- PROGRESS_ANALYSIS
- WEEKLY_REPORT

AIRequestStatus
- ALLOWED
- BLOCKED
- COMPLETED
- FAILED

AIQuotaPeriod
- MONTHLY
```

Gerekli tablolar:

```text
subscriptions
subscription_entitlements veya plan_entitlements
ai_usage_ledger
ai_recommendation_history
```

## Rakip Uygulama Analizi

### 1. MyFitnessPal

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Free
- Premium
- Premium+

Resmi sayfalara gore Premium tarafinda:

- barcode scan,
- meal scan,
- voice logging,
- custom macro/calorie goals,
- ad-free,
- nutrition insights,
- net carbs

Premium+ tarafinda:

- meal planning,
- grocery delivery/shopping services,
- recipe/meal planning odakli genisletmeler

Analiz:

- MyFitnessPal artik 3 katmanli paket mantigina gecmis durumda.
- AI/meal scan gibi yuksek degerli loglama ozelliklerini premium'a koyuyor.
- Premium+ ile sadece takip degil, planlama ve grocery workflow'u satiliyor.

GRun icin ders:

- Ilk cikista 2 paket yeterli.
- Ama ileride PLUS/PRO ayrimi icin MyFitnessPal modeli referans olabilir:
  - PLUS = ad-free + reports + advanced tracking
  - PRO = AI planner + meal/workout planning

Kaynaklar:

- https://apps.apple.com/us/app/myfitnesspal-calorie-counter/id341232718
- https://play.google.com/store/apps/details?id=com.myfitnesspal.android
- https://support.myfitnesspal.com/hc/en-us/articles/34889191368077-What-s-the-difference-between-Free-Premium-and-Premium

### 2. Lose It!

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Basic / Free
- Premium

Premium ozellikleri:

- photo meal logging / Snap It,
- AI voice logging,
- barcode scanner,
- advanced macro/health tracking,
- intermittent fasting,
- meal planning ve targets

Analiz:

- Lose It daha sade iki paketli model kullaniyor.
- AI voice ve photo meal logging premium degeri olarak konumlandirilmis.
- Yillik premium fiyatinin yaklasik USD 39.99 seviyesinde gorundugu kaynaklar var.

GRun icin ders:

- 2 paketli model pazarda normal ve anlasilir.
- AI loglama ve gelismis takip PRO icin yeterli deger olusturabilir.

Kaynaklar:

- https://apps.apple.com/us/app/lose-it-calorie-counter/id297368629
- https://apps.apple.com/us/app/lose-it-calorie-counter/id297368629?uo=4

### 3. Cronometer

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Basic
- Gold

Gold ozellikleri:

- daha genis zaman araliginda charts/insights,
- ad-free deneyim,
- fasting timer,
- daha gelismis raporlar,
- custom charts,
- verified/lab-analyzed food database vurgusu

Fiyat:

- Resmi sayfada Gold aylik USD 10.99 olarak gorunuyor.
- Yillik fiyat kaynaklarda yaklasik USD 59.88 / USD 59.99 bandinda geciyor.

Analiz:

- Cronometer'in premium degeri AI'dan cok veri kalitesi, micronutrient depth ve raporlar.
- Clinical/nutrient accuracy segmentine oynuyor.

GRun icin ders:

- Food database kalitesi subscription icin ciddi deger olabilir.
- Sadece AI degil, verified product database ve gelismis analiz de PRO degeri olabilir.

Kaynaklar:

- https://cronometer.com/gold/index.html
- https://support.cronometer.com/hc/en-us/articles/360028026971-Subscription-Types

### 4. YAZIO

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Free
- PRO

PRO konumu:

- advanced features,
- insights,
- fasting/meal planning/diet plan odakli kullanim,
- subscription olarak sunuluyor.

Analiz:

- YAZIO iki paketli ve daha fiyat hassas bir segmentte konumlanmis gorunuyor.
- PRO, lifestyle ve planlama ozelliklerini aciyor.

GRun icin ders:

- Avrupa bazli fiyatlandirma icin YAZIO iyi bir referans.
- EUR 6.99/ay ve EUR 49.99/yil ust sinira yakin olabilir; daha agresif baslamak istersek EUR 4.99-5.99/ay denenebilir.

Kaynaklar:

- https://play.google.com/store/apps/details?id=com.yazio.android
- https://apps.apple.com/us/app/annual-plan/id946099227
- https://filecontent.yazio.com/press/international_pricing_awin.pdf

### 5. Lifesum

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Free limited
- Premium

Premium model:

- 1-month,
- 3-month,
- annual auto-renewing subscriptions

Premium degeri:

- full Lifesum experience,
- macro tracker,
- food rating,
- Life Score,
- meal/diet guidance,
- daha fazla plan ve analiz

Analiz:

- Lifesum daha lifestyle/design odakli.
- Paket yapisi sade ama sure alternatifleri fazla: monthly / quarterly / yearly.

GRun icin ders:

- Ilk cikista sadece monthly/yearly yeterli.
- Quarterly plan karmasikligi artirir, sonra test edilebilir.

Kaynak:

- https://apps.apple.com/tt/app/lifesum-premium/id286906691

### 6. Foodvisor

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Free download
- Premium

AI/Premium degeri:

- instant food recognition camera,
- personalized nutrition plan,
- tailored recipes,
- custom fitness program,
- in-depth analysis

Analiz:

- Foodvisor AI food recognition'i urunun merkezine koyuyor.
- Premium ile sadece takip degil, nutrition plan ve fitness program da satiliyor.

GRun icin ders:

- AI meal analysis / photo scan varsa PRO degeri guclenir.
- Ancak AI photo scan hata payi ve kullanici guveni iyi yonetilmeli.

Kaynaklar:

- https://apps.apple.com/us/app/foodvisor-calorie-counter/id1064020872
- https://play.google.com/store/apps/details?id=io.foodvisor.foodvisor

### 7. Cal AI

Platformlar:

- App Store
- Google Play

Subscription yapisi:

- Free plan
- Premium

AI modeli:

- photo based AI calorie tracking,
- free planda gunluk sinirli AI scan oldugunu belirten kaynaklar var,
- subscription ile scanning/advanced features aciliyor.

Analiz:

- Cal AI gibi app'ler "AI scan" ozelligini ana deger olarak kullaniyor.
- Bazi kaynaklarda free plan icin 3 AI scan/day gibi limitler belirtiliyor.

GRun icin ders:

- AI deneme limiti kullaniciya degeri gostermek icin mantikli.
- Bizim icin aylik credit modeli gunluk scan limitinden daha esnek.

Kaynaklar:

- https://apps.apple.com/us/app/cal-ai-calorie-tracker/id6480417616
- https://play.google.com/store/apps/details?id=com.viraldevelopment.calai
- https://trackcalai.com/pricing

### 8. Ellim

Platform:

- iOS agirlikli

Subscription yapisi:

- Free
- Premium

Premium degeri:

- AI workout generation,
- AI routine coach,
- AI meal detection from photos,
- weekly/monthly nutrition dashboard,
- progressive overload insights

Fiyat:

- USD 17.99/month
- USD 99.99/year

Analiz:

- AI features premium'da.
- Free tier oldukca genis tutulmus; premium daha cok AI ve advanced insights satiyor.

GRun icin ders:

- Temel tracking'i free tutup AI + advanced insights'i PRO yapmak dogru.
- EUR 6.99/ay gibi fiyat Ellim'e gore daha erisilebilir kalir.

Kaynak:

- https://www.ellim.app/

### 9. Wellz

Durum:

- Waitlist / coming soon olarak gorunuyor.

Subscription ve AI modeli:

- Credit tabanli.
- AI Agent Chat = 1 credit
- Meal Analysis = 3 credit
- Recipe Generation = 3 credit
- Workout Plan = 4 credit
- Meal Plan = 5 credit
- Weekly Report = 12 credit

Planlar:

- Free starter bonus: 50 one-time credits
- Mindful: 100 credits/month, USD 4.99/month
- Holistic: 500 credits/month, USD 19.99/month
- Performer: 1000 credits/month, USD 34.99/month

Analiz:

- AI maliyetini en iyi yoneten model credit sistemidir.
- Farkli AI aksiyonlarinin farkli maliyeti oldugu acikca kullaniciya anlatiliyor.

GRun icin ders:

- Bizim icin en iyi teknik model: subscription + monthly AI credits.
- PRO icin "sinirsiz" yerine yuksek credit limiti daha guvenli.

Kaynak:

- https://wellz.ai/

### 10. FORGE Workout OS

Platform:

- iOS / beta odakli gorunuyor.

Subscription yapisi:

- Free
- Pro monthly
- Pro yearly

AI modeli:

- AI workout generation,
- AI coach chat,
- exercise help,
- meal scan/barcode,
- kullanici kendi Claude/ChatGPT/Gemini API key'ini baglayabiliyor.

Fiyat:

- USD 11.99/month
- USD 95.88/year

Analiz:

- "Bring your own API key" modeli maliyeti kullaniciya aktarir.
- Normal kullanici icin teknik gelebilir, ama power-user icin ilginc bir opsiyon.

GRun icin ders:

- Ilk surumde kendi API key modeli gereksiz.
- Ileride "advanced settings" veya "developer/power user mode" olarak dusunulebilir.

Kaynak:

- https://forgefitnessapp.com/

## Rakiplerden Cikan Ortak Pattern'ler

### Pattern 1: Temel Tracking Free

Neredeyse tum basarili uygulamalar temel tracking'i tamamen kilitlemiyor.

GRun icin:

```text
Food logging
Exercise logging
Basic dashboard
Basic product search
Limited history
```

free kalmali.

### Pattern 2: AI ve Photo/Voice Logging Premium Deger

MyFitnessPal, Lose It, Foodvisor, Cal AI, Ellim gibi app'lerde AI/photo/voice logging premium tarafinda deger olusturuyor.

GRun icin:

```text
AI meal suggestion
AI workout planner
AI daily feedback
AI weekly report
Photo meal scan
Voice meal logging
```

PRO veya credit tuketen ozellik olmali.

### Pattern 3: Credit Modeli AI Icin Daha Saglam

Wellz credit modelini acik kullaniyor. Bu model AI maliyet kontrolu icin daha profesyonel.

GRun icin:

```text
Subscription plan = aylik credit verir
AI task = credit tuketir
Ek credit paketleri = ileride satilabilir
```

### Pattern 4: 3 Paket Olgun Urunde Anlamli

MyFitnessPal gibi buyuk oyuncular 3 katmana gecmis.

GRun icin:

```text
Ilk cikis: FREE + PRO
Backend destek: FREE + PLUS + PRO
Ileride: PLUS acilabilir
```

### Pattern 5: "Unlimited" Pazarlama Riskli

Bazi app'ler unlimited/no limits dili kullaniyor. Teknik olarak bu riskli.

GRun icin:

```text
PRO = high monthly AI credits
Fair usage applies
```

seklinde ilerlemek daha guvenli.

## GRun Icin Guncel Oneri

### Ilk Public Paketler

```text
FREE
- basic food logging
- basic exercise logging
- basic dashboard
- product search
- barcode lookup
- 5 AI credits / month
- limited history

PRO
- EUR 6.99/month
- EUR 49.99/year
- ad-free
- advanced reports
- unlimited history veya long history
- favorites / frequent foods
- AI meal suggestion
- AI workout planner
- AI progress analysis
- 250 AI credits / month
```

### Backend'de Hazir Tutulacak Paket

```text
PLUS
- EUR 3.99/month
- EUR 29.99/year
- ad-free
- advanced dashboard
- 50 AI credits / month
```

PLUS ilk cikista kullaniciya gosterilmeyebilir.

## Sonraki Teknik Adimlar

1. `SubscriptionEntity.planType` string alanini enum'a cevirmek.
2. `PlanType`, `SubscriptionStatus`, `BillingPeriod`, `FeatureKey` enumlarini eklemek.
3. `PlanEntitlement` veya config tabanli entitlement servisi kurmak.
4. `AIUsageLedger` tasarlamak:
   - user
   - requestType
   - creditsUsed
   - status
   - createdAt
   - provider
   - model
   - promptTokens
   - completionTokens
5. `SubscriptionService.getCurrentEntitlements(user)` endpoint'i eklemek.
6. AI endpointleri eklenmeden once quota guard servisini yazmak.

## Karar Ozeti

GRun icin en mantikli yol:

```text
Kullaniciya ilk etapta 2 paket:
FREE + PRO

Backend'de 3 paket:
FREE + PLUS + PRO

AI icin:
credit/quota tabanli model

PRO icin:
sinirsiz degil, yuksek limit + fair usage
```

Bu model hem fiyatlandirmayi sade tutar hem de AI maliyetini kontrol edilebilir hale getirir.
