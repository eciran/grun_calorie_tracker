# GRUN — Öğün ve Günlük Tamamlama Hatırlatmaları Sprint Planı

Tarih: 28 Ağustos 2026<br>
Durum: MR-01–MR-08 lokal uygulama ve otomatik doğrulama tamamlandı; sekiz zamanlanmış çalışma bitti ve otomasyon PAUSED. MR-07 gerçek cihaz/internal build ve MR-08 dış ortam/yayın kabulü BEKLİYOR (29 Ağustos 2026). Canlı gönderim kapalı ve release verdict NOT READY.<br>
Kapsam: Backend karar/zamanlama akışı, admin yönetimi, mevcut mobil bildirim entegrasyonu ve kontrollü yayın.<br>
Sprint kodu: MR — mevcut projelerin sprint numaralarından bağımsızdır.

## 1. Amaç ve kapsam sınırı

Kullanıcının günlüğünü dostane biçimde tamamlamasını desteklemek; uygun durumda kaydedilmiş öğünlere göre kalan kcal bilgisini göstermek. Amaç belirli miktarda yemek yemeye zorlamak veya her gün bildirim kotasını doldurmak değildir.

Kullanıcının belirlediği ürün kararları:

- Ek bir bildirim izin ekranı veya zorunlu onboarding adımı eklenmeyecek.
- Mevcut işletim sistemi izni, hesap push tercihi ve öğün hatırlatma tercihi korunacak. Kapalı tercih sessizce açılmayacak.
- Bildirimler kullanıcının gün içindeki kayıt durumuna bağlı olacak.
- Kahvaltı eksikken akşam yemeğine özel hatırlatma gönderilmeyecek; gerekirse tek bir genel günlük tamamlama mesajı kullanılacak.
- Dil dostane, yargılamayan ve kayıt odaklı olacak.
- Kalori bilgisi doğru ve güncel hesaplanacak; eksik kayıttan yemek yenmediği sonucu çıkarılmayacak.
- Admin panelinden kolay yönetim, önizleme, neden analizi ve durdurma sağlanacak.

Bu dokümandaki saatler, limitler ve teknik adlar uygulama için önerilen başlangıç sözleşmesidir; mevcut canlı özellik olarak yorumlanmamalıdır. MR-01 test örnekleriyle sözleşmeyi sabitler. Planlama talebi prod aktivasyonu, push gönderimi veya deployment yetkisi değildir.

V1 dışında: AI ile metin üretimi, yeni beslenme hedefi hesaplama, kullanıcı davranışından otomatik öğün saati öğrenme, yeni kampanya sistemi, yeni ücretli paket sınırı, ayrı e-posta/SMS akışı ve zorunlu öğün atlama ekranı.

## 2. İncelenen mevcut altyapı

| Parça | Gözlenen durum | Planlanan kullanım / eksik |
| --- | --- | --- |
| Kullanıcı tercihleri | Push, meal reminders ve quiet hours alanları mevcut | Yeni izin istemeden mevcut tercihleri kullan |
| Mobil Settings | Öğün hatırlatma anahtarı backend tercihine bağlı | Anahtarı koru; tekrar izin popup'ı ekleme |
| Food/recipe logs | BREAKFAST, LUNCH, DINNER, SNACK; yiyecek ve tarif kayıtları ayrı | İki kaynağı birlikte değerlendir |
| Dashboard | Tarihe göre hedef; food + recipe toplamı; hedef eksi tüketim | Aynı hesaplama kaynağı, yeni bağımsız formül yok |
| Öğün planı | PLANNED, LOGGED, PARTIALLY_CONSUMED, SKIPPED, REPLACED durumları mevcut | Plan öğesi ile bütün öğün ayrımını koru |
| UserTimeZoneSupport | IANA saat dilimi desteği mevcut | Yerel gün ve slot değerlendirmesi; UTC iş zamanları |
| Notification definitions | Admin'de kanal, metin, route ve enabled yönetimi mevcut | Metinlerin tek kaynağı olarak genişlet |
| Definition renderer | originalTitle/originalMessage/note değişkenleri mevcut | Tipli, izinli dinamik alanlar ve doğrulama gerekiyor |
| Push delivery | Provider seçimi, token yönetimi ve log mevcut | Öğün üreticisi, güvenilir kuyruk ve sonuç ayrımı gerekiyor |
| Fasting reminders | Saat dilimi, tercih ve sessiz saat kontrolüne örnek mevcut | Davranıştan yararlan; mevcut retry/transaction kodunu körlemesine kopyalama |
| Admin güvenlik | Permission matrix, audit ve kritik işlem onay akışı mevcut | Yeni uçları açıkça sınıflandır; varsayılan yetkiye düşürme |

Önemli sınır: Notification Definitions ekranında yeni bir tanım oluşturmak otomatik bildirim üretmez. Yeni bir backend üreticisi gereklidir. Mevcut kampanya schedule endpoint'indeki doğrudan erişim engeli korunacak; yeni otomasyon bu kontrolü aşan bir toplu gönderim yolu olmayacak.

## 3. Davranış sözleşmesi

### 3.1. Öğünün durumunu belirleme

- `RECORDED`: İlgili yerel gün ve meal type için geçerli food veya recipe tüketim kaydı var. Bu, bütün öğünün eksiksiz kaydedildiği iddiası değildir; o öğüne özel tekrar hatırlatmayı durdurur.
- `MISSING`: İlgili öğün bekleniyor, kayıt yok ve açık bir atlama bilgisi yok.
- `EXCLUDED`: Açık kullanıcı/plan bilgisiyle o gün ilgili öğün beklenmiyor veya bütünü bilinçli atlanmış.
- `UNKNOWN`: Kaynak okuma, plan eşleştirme veya zaman verisi güvenilir değil. Varsayılan olarak gönderim yapma; teknik atlama nedeni üret.

Kullanıcının uygulanabilir planı varsa günün ilgili öğünleri oradan türetilir. Plan yoksa kahvaltı/öğle/akşam yalnızca hatırlatma adaylarıdır; zorunlu beslenme önerisi değildir. SNACK varsayılan zorunlu slot değildir.

Tek bir plan yiyeceğinin SKIPPED olması bütün kahvaltıyı EXCLUDED yapmaz. Plandaki kısmi tüketim veya replacement, gerçek günlük kaydıyla birlikte değerlendirilir. Geçmiş günlerde kahvaltı kaydı bulunmaması tek başına kullanıcının kahvaltı yapmadığının kanıtı değildir. Açık atlama bilgisi yoksa geç öğün mesajını bastırıp genel mesaja dönülür; kullanıcıdan yeni bir cevap istenmez.

### 3.2. Karar önceliği

1. Ortam kapısı, aktif yayın modu, hesap uygunluğu, tercihler ve geçerli cihaz kontrolü.
2. Kullanıcının yerel tarihi, sessiz saatleri, aktif oruç durumu ve slotun geçerliliği.
3. Günlük limit, tekrar anahtarı ve son hatırlatma aralığı.
4. Güncel food/recipe kayıtları ve uygulanabilir öğün planı.
5. Önceki ilgili öğünlerde MISSING varsa geç öğüne özel mesajı bastır.
6. İzinli akşam genel kontrolünde, hâlâ eksik varsa tek bir günlük tamamlama mesajı seç.
7. Kcal koşullarını ve metin sürümünü doğrula; göndermeden hemen önce değişen durumu yeniden kontrol et.

| Örnek durum | Beklenen sonuç |
| --- | --- |
| Sabah kahvaltı MISSING | Bir kahvaltı kayıt hatırlatması |
| Kahvaltı RECORDED/EXCLUDED, öğle MISSING | Öğle kayıt hatırlatması |
| Kahvaltı ve öğle RECORDED/EXCLUDED, akşam MISSING | Akşam kayıt hatırlatması; kcal şartları ayrıca değerlendirilir |
| Akşam kahvaltı veya öğle MISSING | Akşam öğünü mesajı yok; izinli tek genel günlük mesajı |
| Hiç kayıt yok | İlk uygun slotta tek öğün mesajı; sonra en fazla bir genel günlük mesajı |
| Sadece öğle kayıtlı, kahvaltı MISSING | Akşam öğünü mesajı yok; genel günlük mesajı |
| Bütün ilgili öğünlerde kayıt var | Yeni hatırlatma yok; pozitif kalan kcal bunu değiştirmez |
| Sadece tarif üzerinden kahvaltı kaydı var | Kahvaltı RECORDED sayılır |
| Bütün kahvaltı açıkça EXCLUDED | Öğle/akşam bağımlılığını engellemez |
| Aktif oruç veya UNKNOWN veri | Öğün ve kcal mesajı yok |

Sabahki eksik kayıt için akşam "Kahvaltı zamanı" gönderilmez. Bir sonraki slot açıldığında eski slot sona erer; uygulama/restart sonrası geçmiş slotlar toplu gönderilmez. Öğün kaydı sonradan silinse bile daha önce gönderilmiş aynı slot yeniden gönderilmez.

### 3.3. Kalori doğruluğu

`remainingCalories = effectiveDailyTarget - (foodLogCalories + recipeLogCalories)`

- Snapshot kalorileri kullanılır; tüketilen kaloriler makrolardan yeniden türetilmez.
- Hedef kaynağı ve o gün geçerli hedef sürümü korunur; otomatik/manuel ayrımı kaybolmaz.
- Tüketim kaydı oluşmamış plan kalorileri eklenmez; plandan aktarılan kayıt ikinci kez sayılmaz.
- Mevcut dashboard politikası korunur: egzersiz kalorileri yemek bütçesine eklenmez.
- Önceki ilgili öğün MISSING, hiç tüketim kaydı yok, hedef geçersiz veya veri belirsizse kcal gösterilmez.
- Kalan değer sıfır/negatifse kalan kcal varyantı kullanılmaz; suçlayıcı aşım veya telafi mesajı yoktur.
- Kayıtlı pozitif kalan değer mesajda yaklaşık tam kcal olarak gösterilir; hesapta hassasiyet korunur.
- Genel günlük tamamlama mesajında kcal gösterilmez. Eksik kayıtlardan kesin enerji ihtiyacı çıkarılmaz.
- Kullanıcı hedefini veya öğününü değiştirirse gönderim öncesi yeniden hesaplanır. Provider'a teslimden sonraki değişiklik için eski push geri alınmış gibi davranılmaz; açılan uygulama güncel veriyi gösterir.

### 3.4. Başlangıç ayarları ve sınırlar

| Ayar | Önerilen V1 değeri | Yönetim sınırı |
| --- | --- | --- |
| Deployment gönderim kapısı | Kapalı | Sadece ortam/deployment kontrolü; admin bunu aşamaz |
| Admin çalışma modu | OFF | OFF / DRY_RUN / PILOT / LIVE |
| Plansız kullanıcı slotları | 10:00 / 14:30 / 20:30 yerel saat | Versiyonlu ve önizlemeli; uygulanabilir plan saati öncelikli |
| Slot geçerliliği | En fazla 60 dakika | Sonraki slota veya yerel güne taşmaz |
| Öğün/günlük bildirim üst sınırı | 3 / yerel gün | Admin yalnızca 0–3 arasında ayarlayabilir |
| Genel günlük mesajı | En fazla 1 / yerel gün | Üçlük toplam limite dahil |
| Öğün/günlük mesajları arası | En az 180 dakika | Admin 180 dakikanın altına indiremez |
| Kullanıcı quiet hours yoksa | 22:00–08:00 | Otomasyonun varsayılan sessiz aralığı; mevcut kullanıcı aralığı öncelikli |
| Karar taraması | 5 dakika | Yalnızca zamanı gelen kullanıcılar, sınırlı batch |
| Diğer rutin hatırlatmayla çakışma | Yakın 30 dakika içinde öğün mesajını ertele/atla | Su/oruç/adım adaptörleriyle test; güvenlik mesajını engelleme |

Kullanıcının kayıtlı öğün saatleri güvenilir biçimde bulunamıyorsa saat uydurulmaz; varsayılan slot kullanılır. Aynı saatte iki aday çıkarsa tek aday seçilir. Genel günlük mesajı ve akşam öğünü mesajı aynı akşam iki ayrı mesaj olarak üretilmez.

Günlük sayaç kullanıcı bazlıdır; cihaz sayısı veya retry sayısı ile artmaz. Çoklu cihaz gönderimi aynı occurrence altında takip edilir. Saat dilimi değişimi sayacı ve cooldown'u sıfırlayarak fazladan gönderime neden olmamalıdır; yerel güne ek olarak kayan 24 saatlik üçlük güvenlik sınırı uygulanır.

DRY_RUN gerçek Notification/push oluşturmaz ve gerçek gönderim sayacını tüketmez. Özel bir sağlık verisi veya yeni izin toplama akışı bu özellik için açılmaz.

### 3.5. Dostane metin sözleşmesi

| Tür | Türkçe başlangıç metni | İngilizce başlangıç metni |
| --- | --- | --- |
| Kahvaltı | Kahvaltını günlüğüne eklemek ister misin? | Would you like to add your breakfast to your diary? |
| Öğle | Öğle öğünün henüz günlüğünde görünmüyor. Vaktin olduğunda ekleyebilirsin. | Your lunch isn't in your diary yet. You can add it when you have a moment. |
| Akşam | Akşam öğününü de günlüğüne eklemek ister misin? | Would you like to add your dinner to your diary too? |
| Kcal içeren akşam | Kaydettiğin öğünlere göre günlük hedefinde yaklaşık {remainingKcal} kcal kalıyor. Akşam öğününü eklemek ister misin? | Based on your logged meals, about {remainingKcal} kcal remains against your daily target. Would you like to log your dinner? |
| Genel günlük | Bugünkü günlüğünde eksik öğünler olabilir. Vaktin olduğunda tamamlamak ister misin? | There may be meals missing from today's diary. Would you like to complete it when you have a moment? |

"Yemelisin", "başarısız oldun", "kalorini yak", "hedefini aştın" ve tıbbi kesinlik içeren metinler kullanılmaz. Sağlık/öğün detayları admin listelerinde ve loglarda yaygınlaştırılmaz. Kcal göstermeyi admin kapatabilmeli; sessizce yeni hassas kullanıcı verisi ekleyen şablon alanlarına izin verilmemeli.

## 4. Admin kontrol alanı

Konum: Mevcut Notifications bölümü altında **Öğün Hatırlatmaları / Meal Reminders**. Mevcut System rules ve Push Delivery ekranlarıyla bağlantılıdır; ikinci bir bağımsız metin kataloğu oluşturulmaz.

1. **Genel durum:** Ortam kapısı, OFF/DRY_RUN/PILOT/LIVE, aktif sürüm, son tarama, kuyruk ve hata durumu.
2. **Zamanlama ve kurallar:** Yerel slotlar, limitler, sessiz saat varsayılanı, kcal varyantı, pilot kullanıcı grubu; değiştirilemeyen güvenlik sınırları açıklanır.
3. **Metinler:** Mevcut definitions üzerinden TR/EN metinleri; izinli değişken listesi, karakter sınırları, eksik alan doğrulaması ve sürümlü önizleme. İlgili otomasyon metninin yayınlanması onay akışını aşmamalıdır.
4. **Senaryo önizleme:** Sentetik kullanıcı/gün/öğün durumu seçimi; önerilen mesaj, gönderim zamanı, kcal hesabının özeti ve neden kodları. Gerçek gönderim yapmaz.
5. **Karar ve teslimat kayıtları:** Gönderilen/atlanılan/ertelenen/süresi dolan kararlar, provider kabulü, receipt durumu, tıklama; filtre ve sayfalama. Ham token, e-posta, tam günlük veya serbest provider cevabı gösterilmez.
6. **Yayın ve geçmiş:** Taslak farkı, gerekçe, onay, kademeli yayın, önceki sürüme dönüş ve acil durdurma.

Kritik kural: Admin enable, kullanıcı preference veya ortam kapısını bypass edemez. Pilot test düğmesi ancak açıkça seçilmiş izinli test hesabına, auditli ve sınırlı gönderim yapabilir; toplu gönderim düğmesine dönüşmez.

## 5. Sprint özeti ve bağımlılıklar

| Sprint | Teslim | Bağımlılık | Durum |
| --- | --- | --- | --- |
| MR-01 | Davranış sözleşmesi ve test senaryoları | Yok | Tamamlandı — 77 kontrol geçti |
| MR-02 | Günlük veri özeti ve doğru kcal kaynağı | MR-01 | Tamamlandı — 87 hedefli test geçti |
| MR-03 | Karar motoru ve gönderimsiz simülasyon | MR-02 | Tamamlandı — 132 hedefli test geçti |
| MR-04 | Güvenilir zamanlayıcı ve push kuyruğu | MR-03 | Tamamlandı — 143 birleşik test geçti, 2 dış PostgreSQL testi bekliyor |
| MR-05 | Admin API, sürümleme ve yayın güvenliği | MR-03, yayın entegrasyonu için MR-04 | Tamamlandı — 158 birleşik test geçti, 2 dış PostgreSQL testi bekliyor |
| MR-06 | Admin kontrol ekranı | MR-05 | Tamamlandı — production build ve 15 admin release kontrolü geçti |
| MR-07 | Mobil yönlendirme ve ölçüm entegrasyonu | MR-04, MR-05 | Lokal uygulama tamamlandı — gerçek cihaz/internal build kabulü BEKLİYOR |
| MR-08 | Uçtan uca kabul ve kontrollü yayın | MR-06, MR-07 | Lokal release hazırlığı tamamlandı — dış kabul/yayın kapıları BEKLİYOR |

Süre taahhüdü yerine her sprint aşağıdaki kabul kapısıyla kapanır. Backend, admin, mobil ve QA sorumlulukları iş bazında ayrıdır; uygulama başlamadan sahipleri atanır.

## 6. Sprint iş paketleri

### MR-01 — Ürün kuralları ve kontrat

Sorumluluk: Ürün + backend + QA. Gerçek push yok.

- [x] MR01-01: RECORDED/MISSING/EXCLUDED/UNKNOWN ve önceki öğün bağımlılığı sözleşmesini örnek fixture'larla sabitle.
- [x] MR01-02: Plansız kullanıcı, kısmi plan, bütünü atlanan öğün, replaced item ve sadece snack durumlarını tanımla; mevcut veriden çıkarılamayan bilgiyi UNKNOWN olarak ayır.
- [x] MR01-03: Kullanılabilir öğün saati kaynağını doğrula; 10:00/14:30/20:30 fallback ve 60 dakika geçerlilik kurallarını test tablosuna dönüştür.
- [x] MR01-04: Tercihler, fasting, sessiz saatler, günlük limit, aralık ve gönderim önceliğini sabitle.
- [x] MR01-05: TR/EN ilk metinlerini ve izinli dinamik alanları tanımla; kcal içermeyen fallback zorunlu olsun.
- [x] MR01-06: V1 kanal varsayımını sabitle: push + uygulama içi kayıt, fakat liste büyümesini mevcut retention yönetimiyle sınırla. DRY_RUN listeye düşmez.

Kabul: Bölüm 3 ve 7'deki her durum için tek, deterministik beklenen sonuç var. Kayıt eksikliği yemek yenmediği olarak yorumlanmıyor. Mevcut kapalı tercihler korunuyor. Varsayımlar ve ürün kararları birbirinden ayrılmış.

### MR-02 — Günlük veri özeti ve hesaplama

Sorumluluk: Backend + veri/QA.

- [x] MR02-01: Yalnızca gerekli alanları dönen `DailyMealReminderSnapshot` oluştur: yerel gün/saat dilimi, hedef ve kaynak sürümü, öğün durumları, food/recipe toplamları, fasting ve tercih bilgisi.
- [x] MR02-02: Dashboard ile ortak kalori/etkin hedef çözümleyicisini kullan veya çıkar; her kullanıcı için bütün dashboard'u üretme.
- [x] MR02-03: Food/recipe kayıtlarını ve plan-linked tüketimi tekil say; planın kendisini tüketim sayma.
- [x] MR02-04: Mevcut logDate saklama sözleşmesini doğrula; tarih aralığı [gün başlangıcı, ertesi gün başlangıcı) ve IANA/DST davranışını test et. Bu özellik için eski kayıtların zamanlarını toplu dönüştürme.
- [x] MR02-05: Öğün ekleme/düzeltme/silme, hedef/plan değişikliği ve kullanıcı timezone değişiminde eski snapshot kullanımını engelle.
- [x] MR02-06: Aday kullanıcılar için sınırlı projection/batch sorguları ve indeks gereksinimlerini çıkar; N+1 ve tüm kullanıcıları belleğe alma yok.

Kabul: Dashboard ile aynı fixture'larda hedef ve kcal sonuçları eşleşiyor. Tarif, plan, kısmi tüketim ve replacement çift sayılmıyor. Geçmiş tarihe eklenen öğün bugünün hatırlatmasını yanlış durdurmuyor. UNKNOWN teknik hata sessizce sıfır kaloriye dönüşmüyor.

### MR-03 — Karar motoru ve DRY_RUN

Sorumluluk: Backend + QA.

- [x] MR03-01: Snapshot + policy version + test edilebilir Clock alan, yan etkisiz `MealReminderDecisionEngine` geliştir.
- [x] MR03-02: BREAKFAST/LUNCH/DINNER/DAILY_CATCHUP adaylarını sırayla değerlendir; karar, reason code, uygun zaman, son geçerlilik, mesaj varyantı ve güvenli parametreler dön.
- [x] MR03-03: Genel mesajı akşam slotunun alternatifi yap; aynı akşam iki ayrı mesaj üretme. İlk hatırlatmayı kaçıran kullanıcıya eski slotlardan backlog oluşturma.
- [x] MR03-04: Kcal eligibility ve dil fallback'ini uygula; izinli typed placeholder dışında değer kabul etme.
- [x] MR03-05: Aynı motoru kullanan sentetik simülasyon ve süre/saklama sınırı olan dry-run karar kaydı ekle. Read-only değerlendirme plan occurrence'ı gibi yan veri oluşturmasın.
- [x] MR03-06: Saat dilimi değişimi, quiet hours ve genel günlük limitini denetleyen stable neden kodlarını ekle.

Kabul: Aynı girdiler aynı kararı verir. Kahvaltı eksikse akşam öğünü bildirimi sıfırdır. Dry-run sıfır provider çağrısı yapar, gerçek sayaçları ve kullanıcı bildirim listesini değiştirmez.

Örnek nedenler: `PREVIOUS_MEAL_MISSING`, `MEAL_ALREADY_RECORDED`, `NO_DIARY_ACTIVITY`, `PREFERENCE_DISABLED`, `QUIET_HOURS`, `FASTING_ACTIVE`, `DAILY_LIMIT`, `COOLDOWN`, `NO_VALID_TOKEN`, `INVALID_TARGET`, `STALE_SLOT`, `DATA_UNAVAILABLE`, `OUTSIDE_COHORT`, `SYSTEM_DISABLED`. Kcal'nin gizlenme nedeni ile bütün bildirimin atlanma nedeni ayrı alanlardır.

### MR-04 — Zamanlama, tekrar koruması ve teslimat

Sorumluluk: Backend + altyapı + QA. Varsayılan OFF.

- [x] MR04-01: `nextEvaluationAt` üzerinden sınırlı aday taraması; jitter, işlem süresi ölçümü ve hatalı kullanıcı izolasyonu.
- [x] MR04-02: Kullanıcı + yerel tarih + slot için DB unique occurrence; policy/template sürümü dedup anahtarına eklenmez. Böylece admin değişikliği aynı gün yeniden gönderim yaratmaz.
- [x] MR04-03: Kullanıcı günü/sayaç rezervasyonunu ve occurrence claim'ini atomik yap; lease/expiry ve PostgreSQL çoklu instance testleri ekle.
- [x] MR04-04: Notification/outbox commit'inden sonra ağ çağrısı yap; uzun DB transaction içinde provider bekleme yok.
- [x] MR04-05: Kuyrukta beklerken güncel preference, kill switch, plan/food ve limit kontrolünü tekrar yap. Uygunluk değişmişse iptal et; gerekirse güncel içerikle yeniden render et.
- [x] MR04-06: Çoklu cihazda her token attempt'ini ayrı izle; başarılı cihaza yeniden gönderme. Invalid token'ı devre dışı bırak. Sıfır uygun token sonucunu teslim edildi sayma.
- [x] MR04-07: Provider kabulü, receipt sonucu, hata ve belirsiz sonucu ayır; sınırlı backoff, TTL ve max attempt uygula. Provider'a verilen TTL de slot süresini aşmasın.
- [x] MR04-08: Su/oruç/adım rutinleriyle dar kapsamlı ortak cooldown entegrasyonu; kritik güvenlik ve AI sonuç bildirimlerinin mevcut davranışını değiştirme.
- [x] MR04-09: Deployment kill switch kapalıyken PILOT/LIVE dahil sıfır gerçek push; yeni migrations eklemeli ve eski uygulamayla uyumlu olsun.

Teslimat sınırı: DB seviyesinde occurrence ve attempt tekilleştirilir; dış push servisinde uçtan uca exactly-once sözü verilmez. İstek kabul edilmiş olabilirken timeout/crash yaşanırsa `UNKNOWN` olarak kaydet; kanıt olmadan otomatik yeniden gönderip kullanıcıyı tekrar dürtme. İlgili occurrence günlük bütçeyi ihtiyatlı biçimde rezerve eder. Kesin şekilde gönderilmemiş iş için rezervasyon geri alma atomik ve testli olmalıdır.

Kabul: İki worker aynı occurrence'ı eşzamanlı gönderime alamıyor. Restart eski öğünleri yağdırmıyor. Retry daha önce kabul edilmiş token'a gitmiyor. Provider kabulü kullanıcı ekranında gösterildi anlamına gelmiyor. Kapalı mod ve kill switch testleri tüm gönderim yollarını kapsıyor.

### MR-05 — Admin API ve yayın güvenliği

Sorumluluk: Backend + admin güvenliği + QA.

- [x] MR05-01: Sürümlü taslak/aktif policy modeli, optimistic locking, zamanlama ve limit validation ekle; iki adminin birbirini sessizce ezmesini engelle.
- [x] MR05-02: Önerilen `/api/v1/admin/meal-reminder-automation` altında config, preview, karar kayıtları, özet, sürüm geçmişi ve durdurma kontratını tanımla. İsimler OpenAPI ile kesinleşir.
- [x] MR05-03: Endpoint'leri permission matrix'e açıkça ekle: okuma `GROWTH_READ`, taslak yönetim `GROWTH_MANAGE`; yeni path mevcut varsayılan `DASHBOARD_READ` yetkisine düşmesin.
- [x] MR05-04: Canlı etkinleştirme, kitle genişletme, aktif metin/policy yayınlama ve rollback için mevcut maker-checker yaklaşımını genişlet. Self-approval engeli ve mevcut owner re-auth korunur.
- [x] MR05-05: Yetkili acil durdurma gerekçeli ve auditli tek işlem olsun; ikinci onayı beklemesin. Yeniden açma onaya tabi olsun.
- [x] MR05-06: Metinleri Notification Definitions'da tut; otomasyonun korunan tanımlarını eski edit endpoint'i üzerinden onaysız yayınlama yolu bırakma. NotificationDefinitionPolicy'nin dispatch anında farklı sürümle metni ezmesini engelle.
- [x] MR05-07: Typed placeholder, route allowlist, mesaj boyutu, TR/EN fallback ve numeric alan validasyonu. Admin serbest query/script veya ham kullanıcı verisi çalıştıramasın.
- [x] MR05-08: Pilot kitleyi backend'de uygula; preview gerçek gönderim yapmasın. Sadece onaylı test hesabına rate-limitli, auditli test gönderimi sağla.
- [x] MR05-09: Retention, kullanıcı veri silme/export ve audit'te minimal veri sözleşmesini mevcut yaşam döngüsüne bağla; liste/metric/log alanlarında ham sağlık verisi ve token yok.

Kabul: Yetkisiz roller bütün yeni okuma/yazma yollarında engelleniyor. Admin kullanıcı tercihini, deployment kapısını veya hard limitleri aşamıyor. Eski definitions endpoint'i yayın onayını bypass etmiyor. Preview, retry ve test-send gibi alternatif yollar aynı kontrolleri uyguluyor.

### MR-06 — Admin kontrol ekranı

Sorumluluk: Admin frontend + QA.

- [x] MR06-01: `modules/notifications` altında ayrı Meal Reminder Automation görünümü; mevcut App navigasyonuna küçük entegrasyon.
- [x] MR06-02: Genel durum, zamanlama/kurallar, metin bağlantıları, senaryo önizleme, karar kayıtları ve yayın geçmişini Bölüm 4'e göre oluştur.
- [x] MR06-03: Kullanıcı yerel saati ile admin saatini ayır; örnek gün/saat dilimi üzerinden etkiyi göster. "Kaydet" ile "Canlı yayınla" ayrı olsun.
- [x] MR06-04: Nedenleri okunabilir göster: "Kahvaltı kaydı eksik olduğu için akşam hatırlatması atlandı"; teknik kod ayrıntıda kalsın.
- [x] MR06-05: Taslak farkı, bekleyen onay, sürüm çatışması, boş/hata/yükleniyor durumları; rol bazlı read-only arayüz.
- [x] MR06-06: Acil durdurma, durdurma sonrası kuyruk sonucu ve geri dönüşün yeni gönderim tetiklemeyeceğini açıkça göster.
- [x] MR06-07: Keyboard erişimi, okunabilir formlar, responsive ekran ve mevcut admin production-readiness testlerine yeni kontratlar.

Kabul: Admin sayfadan çıkmadan senaryo için neyin ne zaman/neden gönderileceğini anlayabiliyor. Kayıtlar sayfalı, hassas veriler maskeli. Çalışma modu ve ortam kapısı açıkça farklı. Broadcast/System rules/Push Delivery ekranları geriye dönük çalışıyor.

### MR-07 — Mobil davranış ve ölçüm

Sorumluluk: Mobil + backend + QA.

- [x] MR07-01: Mevcut mealRemindersEnabled anahtarı ve token lifecycle'ı koru; yeni izin prompt'u veya zorunlu form ekleme.
- [x] MR07-02: Notification service ve route çözümleyicide tarih/öğün bağlamını destekle; hedefli mesaj doğru öğün ekleme akışına, genel mesaj günlük görünümüne açılsın.
- [x] MR07-03: Ön planda, arka planda, kapalı uygulamada ve login sonrası navigation'ı lokal kontratla test et; eski istemci için güvenli günlük ekranı fallback'i sağla. Gerçek cihaz kanıtı MR07-07'de ayrıca bekliyor.
- [x] MR07-04: Push body'deki kcal'yi güncel gerçek sayma; ekranda backend'den güncel günlük özeti yükle. Başka hesaba ait bildirimi yanlış hesapta açma.
- [x] MR07-05: Etkileşim kaydını notification/occurrence ile idempotent bağla; aynı tıklama veya aynı food/recipe log birden fazla dönüşüm sayılmasın.
- [x] MR07-06: Ölçümler: provider kabulü, receipt sonucu, tekil açılış, açılıştan sonra 2 saat içinde ilgili güne öğün kaydı ve hatırlatma kapatma oranı. Zaman penceresindeki ilişki nedensellik iddiası değildir.
- [ ] MR07-07: Gerçek cihaz ve development/internal build ile push QA; yalnızca simülasyon veya Expo Go gözlemini prod kabulü sayma.

Kabul: İzinli kullanıcıdan yeniden izin istenmiyor. Push kapatma etkili oluyor. Eski notification türleri ve AI sonuç yönlendirmesi bozulmuyor. Foreground/background/cold-start ve logout/login testleri geçiyor. Analytics yalnızca kullanıcı izinleri ve mevcut veri politikası kapsamında işliyor.

### MR-08 — Entegrasyon, performans ve kontrollü yayın

Sorumluluk: QA + backend + admin + mobil + release sahibi.

- [ ] MR08-01: Bölüm 7 matrisini backend, PostgreSQL, admin ve mobil katmanlarında tamamla.
- [ ] MR08-02: Yeni/var olan DB için Flyway doğrula; migration numarasını uygulama anında güncel branch'ten seç. Diğer ekip işlerinin migration'larıyla çakışma yok.
- [ ] MR08-03: Gerçek hedef kitleye uygun veriyle sınırlı scan/queue load testi; scan süresi interval'i aşmasın, N+1/tüm kullanıcı taraması ve dış servis hatasında toplu rollback olmasın.
- [ ] MR08-04: Tercih kapatma, kill switch, provider timeout, DB restart, worker crash ve çoklu instance tatbikatı.
- [ ] MR08-05: DRY_RUN sonuçlarını günün tüm slotları için incele; sonra izinli test hesaplarıyla PILOT ve gerçek cihaz kanıtı.
- [ ] MR08-06: Onaylı aşamalı LIVE yayını: önerilen %5 -> %25 -> %100; her artışta yeterli örneklem ve en az iki tam yerel gün incelensin. Küçük kitlede yalnızca süre doldu diye geçilmesin.
- [ ] MR08-07: Alarm eşikleri ve release sahibi doldurulsun: provider hata oranı, queue lag, opt-out değişimi, görev süresi. Tercih/sessiz saat ihlali veya yanlış kcal/tekrar gönderim kritik durdurma nedenidir.
- [ ] MR08-08: Admin kullanım notu, mobil handoff, config varsayılanları ve rollback runbook'u yaz; gerçekleşen sonuçları kanıtlarıyla kaydet.

Kabul: Yanlış kcal, tercihi ihlal eden gönderim ve uygulama kaynaklı duplicate testleri sıfır. Canlı artışlar onaylı ve auditli. AI sonuç, mail, kampanya, su/oruç/adım akışlarında regression yok. Kill switch bekleyen işleri durduruyor; provider'a önceden kabul edilmiş mesajı geri çekme garantisi verilmiyor.

## 7. Zorunlu kabul matrisi

| ID | Senaryo | Beklenen |
| --- | --- | --- |
| A01 | Push veya meal tercihi kapalı | Sıfır gönderim; preference nedeni |
| A02 | Kahvaltı eksik, akşam slotu | Akşam öğünü mesajı yok; uygun tek genel mesaj |
| A03 | Hiç kayıt yok, üç tarama slotu | En fazla ilk öğün mesajı + bir genel mesaj |
| A04 | Kahvaltı ve öğle kayıtlı, akşam eksik | Tek akşam mesajı; güvenilir pozitif kalan kcal ise varyant |
| A05 | Bütün ilgili öğünler kayıtlı, kcal pozitif | Yeni hatırlatma yok |
| A06 | Tarif kaydı var, food log yok | İlgili öğün kayıtlı, tarif kalorileri dahil |
| A07 | Planlı fakat tüketilmemiş yemek | Tüketim kalorisine eklenmez |
| A08 | Plandan tüketim kaydı / partial / replacement | Çift kcal yok; kaynak kayıt durumu korunur |
| A09 | Tek plan öğesi SKIPPED | Bütün öğün otomatik EXCLUDED değil |
| A10 | Bütün öğün açıkça hariç | Sonraki öğünün bağımlılığını engellemez |
| A11 | Hedef yok/geçersiz, sıfır/negatif kalan | Kcal metni yok; varsa uygun nötr kayıt mesajı |
| A12 | Gönderim öncesi food/recipe/goal değişikliği | Yeniden değerlendirme; eski kcal/yanlış öğün yok |
| A13 | Geceyi aşan quiet hours, aktif fasting | İhlal yok; sabaha backlog yok |
| A14 | DST ileri/geri, timezone değişimi, gece yarısı | Tekil slot, doğru gün, limit reset açığı yok |
| A15 | İki worker / aynı anda iki slot | Atomik claim ve kullanıcı limit rezervasyonu |
| A16 | Restart, expired lease ve geçmiş slot | Süresi geçmiş iş gönderilmez |
| A17 | Bir cihaz başarılı, diğeri başarısız | Başarılı cihaza tekrar yok; kullanıcı sayacı tek |
| A18 | Provider kabulünden sonra timeout/crash | UNKNOWN; kör resend yok |
| A19 | Token yok, invalid token, receipt error | Teslim edildi görünmez; uygun hata/token yönetimi |
| A20 | DRY_RUN veya ortam kapısı kapalı | Gerçek push/Notification ve gerçek bütçe tüketimi yok |
| A21 | Admin policy/template değişimi veya rollback | Aynı gün aynı slot yeniden üretilmez |
| A22 | Yetkisiz admin / self-approval / eski edit yolu | Yetki ve yayın koruması aşılmaz |
| A23 | Genel mesaj ve dinner aynı slotta aday | Tek mesaj; günlük toplamda tek sayı |
| A24 | Başka rutin hatırlatmayla yakın zaman | Belirlenen cooldown uygulanır; kritik mesaj bastırılmaz |
| A25 | Eski mobil sürüm, cold-start, farklı hesap | Güvenli route/login fallback; veri sızıntısı yok |
| A26 | Önizleme ve gerçek karar, aynı snapshot/sürüm | Aynı gerekçe ve mesaj; preview yan etkisiz |
| A27 | Gün içinde yeniden kapatma, kuyrukta iş | Gönderim öncesi iptal |
| A28 | Yinelenen click/log event | Tekil ilişkilendirme; şişen dönüşüm yok |

## 8. Release ve değişiklik disiplini

- Her sprint kendi test kanıtı, değişen dosyaları ve kalan engelleriyle kapanır; planlandı durumu kod tamamlandı anlamına gelmez.
- Geliştirme mevcut kirli çalışma ağacındaki ilgisiz değişiklikleri korumalıdır. Ortak App.tsx, security, push, dashboard ve migration dosyaları uygulama öncesi tekrar kontrol edilmelidir.
- İlk kod dağıtımı varsayılan OFF olur. Admin config veya seed migration mevcut kullanıcıları otomatik LIVE yapmaz.
- Kademeli yayın ayrı ve açık bir operasyon kararıdır. İzinli pilot hesaplar da kullanıcı tercihlerini bypass etmez.
- Rollback önce OFF ve pending işlerin iptaliyle yapılır. Eski sürüme dönüş yeni occurrence veya tekrar push üretmez. Veri silen reverse migration kullanılmaz.
- En son kontrol edilecek dış bağımlılıklar: çalışan push credentials/provider, gerçek cihaz test hesabı, iki kişili onay akışı için yetkili kişiler, performans hedef kitlesi ve release alarm eşikleri.
- Bu planda `.env`, AWS ayarları, build pipeline'ı veya çalışan süreçler değiştirilmeyecektir.

## 9. Kod ve doküman referansları

Aşağıdaki referanslar 28 Ağustos 2026 çalışma ağacı incelemesine dayanır; mevcut diğer çalışmalar nedeniyle uygulama başlangıcında yeniden doğrulanmalıdır.

- `src/main/java/com/grun/calorietracker/dto/NotificationPreferenceDto.java`
- `src/main/java/com/grun/calorietracker/service/support/UserTimeZoneSupport.java`
- `src/main/java/com/grun/calorietracker/service/impl/DashboardServiceImpl.java`
- `src/main/java/com/grun/calorietracker/service/impl/FoodLogsServiceImpl.java`
- `src/main/java/com/grun/calorietracker/entity/MealPlanItemConsumptionEntity.java`
- `src/main/java/com/grun/calorietracker/service/NotificationDefinitionPolicy.java`
- `src/main/java/com/grun/calorietracker/service/impl/PushDeliveryServiceImpl.java`
- `src/main/java/com/grun/calorietracker/service/push/ExpoPushProviderClient.java`
- `src/main/java/com/grun/calorietracker/service/impl/AdvancedFastingReminderServiceImpl.java`
- `src/main/java/com/grun/calorietracker/security/AdminPermissionMatrix.java`
- `src/main/java/com/grun/calorietracker/controller/AdminNotificationCampaignController.java`
- `admin-ui/src/modules/notifications/NotificationDefinitionsView.tsx`
- `admin-ui/src/App.tsx`
- Mobil repo: `src/screens/Settings.tsx`, `src/screens/Notifications.tsx`, `src/services/notificationService.ts`.
- Yerel handoff'lar: `docs/FRONTEND_HANDOFF_MOBILE_PUSH_TRACKING.md`, `docs/ADMIN_PRODUCTION_ROADMAP_2026-07-26.md`, `docs/ADVANCED_FASTING_SPRINT_PLAN_2026-07-26.md`.

Provider sonucu notu: Expo ticket kabulü ile APNs/FCM receipt sonucu ayrı tutulmalıdır; bunlar kullanıcının bildirimi okuduğu kanıtı değildir. Geçici hatalar için sınırlı retry ve invalid token için gönderimi durdurma akışı gerekir. Kaynak: [Expo — Send notifications with the Expo Push Service](https://docs.expo.dev/push-notifications/sending-notifications/) (28 Ağustos 2026 kontrolü).

## 10. MR-01 — Kesinleşen V1 sözleşmesi

Sözleşme sürümü: `meal_reminder_v1`. Bu bölüm önceki taslaktaki belirsizlikleri kapatır; zamanlayıcı veya canlı gönderim henüz uygulanmış değildir.

### 10.1. Kaynak doğrulaması ve öğün durumu

- `MealPlanItemEntity` öğün türünü ve tarihini tutuyor, kalıcı bir öğün saati alanı tutmuyor. `AiNutritionPlanDraftRequestDto.preferredMealTimes` ve draft çıktısındaki `suggestedTime`, güncel kullanıcı zamanlama tercihi değildir. Eski AI request payload'ından saat çıkarılmayacak.
- V1'de zamanlama kaynağı admin'in sürümlü yerel saat ayarı; ilk değerler 10:00, 14:30, 20:30. Kullanıcıya yeni saat/izin sorusu yöneltilmeyecek. Sonraki bir ürün değişikliği kalıcı kullanıcı saat tercihi eklemedikçe plan saatinin önceliği etkinleşmez.
- Mevcut planın ACTIVE olması, bütün günlük öğünleri kapsadığını ispatlamıyor. Planda kahvaltı bulunmaması veya plan öğelerinin tamamının SKIPPED olması tek başına kahvaltının bütünüyle atlandığı kanıtı sayılmayacak. Kısmi plan nedeniyle eksik öğün kendiliğinden EXCLUDED yapılmayacak.
- Mevcut güvenilir bütün-öğün exclusion sinyali yoksa snapshot `explicitWholeMealExclusion=false` üretir. EXCLUDED sözleşmede gelecekteki güvenilir bütün-öğün sinyali için korunur; MR-01 yeni bir skip ekranı veya kullanıcıya soru eklemez.
- Durum önceliği: veri güvenilir değilse UNKNOWN; aksi halde gerçek food veya recipe kaydı varsa RECORDED; aksi halde güvenilir bütün-öğün exclusion varsa EXCLUDED; diğer durumlarda MISSING.
- Gerçek kayıt, eski exclusion bilgisinin önündedir. RECORDED için kcal alt eşiği yoktur; geçerli sıfır kalorili bir kayıt da günlüğe kayıt olarak sayılır. Bu, yeterli beslenme veya öğünün tamamen kaydedildiği iddiası değildir.
- Plandaki PARTIALLY_CONSUMED/REPLACED etiketinin tek başına varlığı yeterli değildir: canonical günlük kaydının doğrulanması gerekir. Kaynak okunamıyor veya ilişki çözülemiyorsa UNKNOWN; yalnızca plan var ama tüketim işlemi hiç yapılmamışsa MISSING.
- Veri kaynağının hiç olmaması ile okunamaması ayrıdır: plansız ve başarılı şekilde boş dönen günlük normal MISSING; başarısız sorgu UNKNOWN. UNKNOWN bulunan gün için mesaj gönderilmez.

### 10.2. Slot, önceki öğün ve tekrar sözleşmesi

Üç occurrence slotu vardır: BREAKFAST, LUNCH, EVENING. DINNER, DINNER_KCAL ve DAILY_CATCHUP mesajları aynı EVENING kimliğini paylaşır. DAILY_CATCHUP ayrı dördüncü slot değildir; ilk denemeden sonra varyant değişmesi ikinci bir akşam gönderimine izin vermez.

| Kontrol | Kesin V1 sonucu |
| --- | --- |
| Kahvaltı MISSING, öğle slotu | Öğle mesajı bastırılır; o saatte geç kahvaltı veya genel mesaj gönderilmez |
| Kahvaltı MISSING, öğle ve akşam RECORDED | EVENING slotunda tek genel günlük mesajı seçilebilir |
| Hiç kayıt yok, sabah slotu kaçırılmış | Öğle için kahvaltı bağımlılığı aşılmaz; akşam en fazla tek genel günlük mesajı |
| Bütün ilgili ana öğünler RECORDED/EXCLUDED | DAY_COMPLETE; kalan kcal pozitif olsa bile yeni mesaj yok |
| Sadece SNACK kaydı var | Ana öğünleri RECORDED yapmaz; akşam genel günlük mesajı, kcal yok |
| Önceki ilgili ana öğün MISSING | Geç öğüne özel mesaj yok; yalnızca EVENING'de genel mesaj alternatifi |
| Önceki ilgili ana öğün UNKNOWN | Genel mesaja dönerek hata gizlenmez; DATA_UNAVAILABLE |
| Öğün kaydı sonradan silinmiş | Daha önce ele alınmış occurrence yeniden gönderilmez |

Daha sonra admin özel saatleri desteklediğinde saatler kahvaltı < öğle < akşam sırasına uymalıdır. Aynı saate birden fazla ana slot tanımlanması doğrulama hatasıyla reddedilir. DST boşluğuna denk gelen özel slot o gün atlanır; tekrarlanan yerel saatte ilk ofset kullanılır ve occurrence kimliği ikinci gönderimi engeller. Varsayılan slotlar bu DST belirsizliğine denk gelmez.

Yerel slotlar: [10:00, 11:00), [14:30, 15:30), [20:30, 21:30). Baslangic dahil, bitis harictir. Sonraki slot ve yerel gun sonu gecerliligi sinirlar. Sessiz saatlerde kacirilan slot ertesi gune tasinmaz. Yerel saatler IANA zaman dilimiyle; cooldown ve kayan 24 saat siniri Instant ile hesaplanir. Sabit UTC ofseti kullanilmaz.

### 10.3. Engel önceliği, kanal ve sayaçlar

Birden çok engel olduğunda ilk neden şu sıradan seçilir: SYSTEM_DISABLED, ACCOUNT_INELIGIBLE, PREFERENCE_DISABLED, OUTSIDE_COHORT, NO_VALID_TOKEN, DATA_UNAVAILABLE, QUIET_HOURS, FASTING_ACTIVE, NOT_DUE, STALE_SLOT, ALREADY_HANDLED, DAILY_LIMIT, ROLLING_LIMIT, COOLDOWN, ROUTINE_COOLDOWN. Sonrasında öğün bağımlılığı, tamamlanma ve genel mesaj sınırı değerlendirilir. Kcal göstermeme nedeni ayrı alandır; örneğin geçersiz hedef nötr öğün mesajını tek başına engellemez.

- Production başlangıcı OFF ve deployment gönderim kapısı false; hiçbir kullanıcı preference kaydı değiştirilmez.
- DRY_RUN ve sentetik preview canlı gönderim kapısı kapalıyken de karar simülasyonu yapabilir, ancak gerçek Notification/outbox veya bütçe rezervasyonu oluşturamaz. Simülasyonun gönderim kapısını aşması gerçek gönderim yetkisi değildir.
- Gerçek akışın V1 kanalı IN_APP_AND_PUSH; geçerli token yoksa uygulama içi listeye her slot için hatırlatma yığılmaz. NO_VALID_TOKEN nedeni tutulur, Notification oluşturulmaz.
- Normalde Notification/outbox atomik kaydedilir, provider çağrısı commit sonrasında yapılır. Provider hatası yeni Notification üretmez; aynı occurrence üzerinde sonuç izlenir. Bu davranış MR-04'te uygulanıp test edilecektir.
- Günlük toplam en fazla 3, kayan 24 saatte en fazla 3, genel günlük mesajı en fazla 1; hepsi kullanıcı bazındadır. Retry ve cihaz sayısı yeni occurrence sayılmaz.
- İki öğün/günlük mesajı arasında tam 180 dakika dolduğunda aralık uygundur. Başka rutin hatırlatma son 30 dakika içindeyse ertelenir; tam 30 dakikada uygundur. Erteleme slotun bitişinden sonraya taşıyorsa STALE_SLOT olur.
- Kullanıcının quiet hours alanları ikisi birden boşsa bu otomasyonun 22:00–08:00 varsayılanı uygulanır. İkisi doluysa kullanıcının aralığı kullanılır. Biri dolu biri boş gibi geçersiz veri DATA_UNAVAILABLE'dır. İki eşit saat, mevcut sözleşmeyle uyumlu olarak özel quiet-hours kısıtı olmadığını belirtir; geçerli slot sınırı yine uygulanır.
- Aktif fasting sırasında kcal ve öğün/günlük hatırlatması bastırılır. Su/oruç/adım rutinleriyle cooldown uyumu MR-04'e aittir; güvenlik ve AI sonuç mesajları bu yeni modülce engellenmez.
- Notification saklama NOTIFICATIONS retention anahtarına bağlı kalır. Decision/outbox/attempt kayıtları için mevcut silme/export zinciri MR-05'te kapsanır; MR-01 yeni kalıcı günlük tablosu eklemez.

### 10.4. Metin ve kcal sözleşmesi

- Beş sabit tanım anahtarı: `meal_reminder_breakfast`, `meal_reminder_lunch`, `meal_reminder_dinner`, `meal_reminder_dinner_kcal`, `meal_reminder_daily_catchup`.
- Yalnızca DINNER_KCAL varyantı `{remainingKcal}` parametresini kabul eder. Diğer metinlerde dinamik kişisel veri yoktur. Başlıkta dinamik parametre yoktur.
- Başlangıç metinleri `MealReminderContract.initialCopy` içindedir; MR-05 için seed kaynağıdır. Runtime metin değişiklikleri mevcut Notification Definitions üzerinden yapılacak; ikinci admin metin kataloğu oluşturulmayacak.
- `tr`, `tr-TR` ve `tr_TR` Türkçe; EN, boş ve desteklenmeyen dil İngilizce fallback kullanır. İlk metinler başlıkta 80, gövdede 240 karakter sınırındadır.
- Kcal formatter yeni hedef/tüketim hesabı yapmaz; MR-02'den gelen canonical kalan değeri kullanır. HALF_UP ile tam sayıya yuvarlanır; 650.50 -> 651. Yuvarlanınca sıfır olan pozitif değer (ör. 0.25) nötr akşam varyantına döner. Negatif/sıfır/null değer kcal varyantında kullanılamaz.
- Önceki ana öğünler RECORDED/EXCLUDED olsa bile gün içinde gerçek tüketim kaydı bulunmuyorsa kcal gösterilmez. Kcal etkin değilse, hedef geçersizse veya güvenilir plan/hesap yoksa nötr varyant seçilir.
- Kcal mesajında mutlaka kayıtlara dayalı ve yaklaşık olduğu belirtilir. Genel günlük mesajı tüketim ihtiyacı veya hedefe yetişme baskısı içermez.

### 10.5. Doğrulama kapsamı ve sonraki sprintlere devir

- `src/main/java/com/grun/calorietracker/service/reminder/MealReminderContract.java`: Sürüm, güvenli sabitler, enum'lar, canonical evidence sınıflandırması, öğün bağımlılıkları ve başlangıç metinleri. Spring bean, scheduler veya provider çağrısı yok.
- `src/test/resources/meal-reminders/contract-v1.json`: 15 kaynak/durum örneği, 36 beklenen karar örneği ve 10 slot sınırı örneği. Decision case'lerde koşullar önceden değerlendirilmiş engel predicate'leridir; tam kullanıcı snapshot'ı değildir.
- `src/test/java/com/grun/calorietracker/service/reminder/MealReminderContractTest.java`: Sınıflandırma ve metin kodu testleri; fixture'ların ana kurallara tutarlılığı, benzersizliği, slot/dil/DST varsayımları.
- Karar motoru bu sprintte yok; MR-03'te oluşturulacak. Karar fixture'larının yapısal/invariant testlerinden geçmesi, üretim karar motorunun çalıştığının kanıtı değildir. MR-03 bu aynı fixture beklentilerini gerçek engine'e bağlamalıdır.
- A01–A28 kabul matrisinin beklenen sonuçları sabittir; ilgili DB, provider, admin ve mobil entegrasyon kanıtları sahip sprintlerinde üretilir. MR-01, bu downstream testleri geçmiş gibi raporlamaz.

## 11. MR-02 — Kesinleşen günlük veri sözleşmesi

- `food_logs.log_date` ve `recipe_logs.log_date` mevcut uygulamada kullanıcının yerel sivil zamanını `LocalDateTime` olarak saklar. Reminder okuması bunu UTC'ye dönüştürmez; kullanıcının IANA zone'u ile yerel tarihi bulur ve sorguyu `[localDate.atStartOfDay(), localDate.plusDays(1).atStartOfDay())` olarak yapar. Geçmiş kayıt dönüşümü veya migration yoktur.
- `DailyMealReminderSnapshotService`, scheduler'ın verdiği en fazla 500 benzersiz kullanıcıdan oluşan aday sayfasını kabul eder; kendi içinde bütün kullanıcıları arayan bir repository bağımlılığı yoktur. Kullanıcıları yerel tarihe göre gruplayıp food, recipe, hedef geçmişi ve aktif fasting verisini toplu sorgular. Böylece aynı tarih grubunda kullanıcı başına sorgu/N+1 oluşmaz.
- Canonical tüketim yalnızca gerçek `food_logs` ve `recipe_logs` kayıtlarıdır. `meal_plan_items` veya `meal_plan_item_consumptions` kalorileri ayrıca toplanmaz; plan-linked food/recipe kaydı kendi günlük tablosunda bir kez sayılır. PARTIALLY_CONSUMED/REPLACED plan durumu ikinci bir kalori kaynağı değildir.
- Dashboard ve reminder, etkin tarihli hedef/fallback seçimini ve iki ondalık kalori aritmetiğini `DailyCalorieBudgetSupport` üzerinden paylaşır. Reminder bütün Dashboard DTO'sunu veya exercise/mikro besin sorgularını üretmez. Snapshot hedef kimliği, optimistic-lock sürümü, çözümleme kaynağı ve varsa calculation mode bilgisini taşır.
- Snapshot kalıcı veya uygulama cache'ine yazılmaz. Her değerlendirme food/recipe/hedef/fasting ve kullanıcı preference/timezone alanlarını yeniden okur; ekleme, düzeltme, silme ve hedef/saat dilimi değişiklikleri sonraki çağrıda görünür. Plan tek başına tüketim olmadığı için plan değişikliği eski kcal snapshot'ı oluşturmaz.
- Food/recipe/hedef okumasındaki teknik hata `calorieDataReliable=false`, nullable kalori alanları ve öğünlerde `UNKNOWN` üretir; boş ve başarılı sorgu ise sıfır toplam + `MISSING` üretir. Aktif fasting sorgusu başarısızsa fasting durumu ayrı olarak `UNKNOWN` olur. Teknik hata sıfır tüketim gibi yorumlanmaz.
- Mevcut `(user_id, log_date)` food/recipe ve `(user_id, status)` fasting indeksleri yeniden kullanılır. Hedef batch çözümü için `V230__add_meal_reminder_snapshot_indexes.sql` ile `(user_id, effective_local_date, effective_from DESC)` indeksi eklenmiştir. Migration yalnızca şema dosyasıdır; bu sprintte canlı veya lokal veritabanına uygulanmamıştır.

## 12. MR-03 — Kesinleşen karar ve dry-run sözleşmesi

- `MealReminderDecisionEngine`, `DailyMealReminderSnapshot`, sürümlü `MealReminderPolicy`, runtime sayaç/uygunluk durumu ve constructor ile verilen test edilebilir `Clock` dışında veri kaynağı kullanmaz. Aynı girdiler aynı immutable `MealReminderDecision` sonucunu üretir; repository, Notification veya provider çağrısı yapmaz.
- Sonuç; aday (`BREAKFAST`, `LUNCH`, `DINNER`, `DAILY_CATCHUP`), ortak occurrence slotu, gönderim uygunluğu, stable reason/kcal reason, policy sürümü, uygunluk ve son geçerlilik Instant'ları, mesaj varyantı, render edilmiş TR/EN fallback metni ve izinli parametreleri taşır. Yalnızca `DINNER_KCAL` tam sayı biçimli `remainingKcal` kabul eder.
- `DINNER`, `DINNER_KCAL` ve `DAILY_CATCHUP` aynı EVENING slotunu kullanır. Kahvaltı/öğle MISSING olduğunda akşam öğününe özel mesaj seçilmez; akşamda en fazla tek genel günlük alternatifi üretilir. Aktif pencere dışında otomatik tarama eski kahvaltı/öğle slotlarını backlog olarak değerlendirmez; claim edilmiş ve süresi dolmuş slot `STALE_SLOT` olur.
- Öncelik sırası ortam/hesap/tercih/cohort/token, veri, quiet hours, fasting, slot, occurrence/limit/cooldown ve öğün durumudur. Kullanıcı günü ile snapshot günü uyuşmazsa, snapshot 10 dakikadan eskiyse, IANA zone/ana öğün/fasting verisi belirsizse `DATA_UNAVAILABLE` ile fail-closed olur. Geceyi aşan quiet hours desteklenir; eşit başlangıç/bitiş özel kısıt yoktur.
- Kcal nedeni bütün bildirimin reason kodundan ayrıdır. Hedef geçersiz, veri belirsiz, diary activity yok, kcal kapalı, kalan pozitif değil veya tam sayıya sıfır yuvarlanıyor durumlarında nötr dinner metni seçilir. Genel günlük metni kcal parametresi taşımaz.
- `MealReminderDryRunService.preview` aynı motoru salt-okunur çalıştırır ve hiçbir satır oluşturmaz. `evaluateAndRecord` yalnızca DRY_RUN modunda, e-posta içermeyen sınırlı subject ref ile redakte karar özeti saklar; parametre, metin gövdesi, günlük içeriği, token veya provider cevabı saklamaz. Satırların `expires_at` değeri 7 gündür ve her kayıt öncesi süresi dolan satırlar temizlenir.
- `V231__add_meal_reminder_dry_run_decisions.sql` yalnız dry-run gözlem tablosu ve expiry/recent lookup indekslerini tanımlar. Gerçek occurrence, Notification, sayaç rezervasyonu ve delivery tabloları MR-04'e aittir; bu sprintte bunların yerine geçecek yan veri oluşturulmadı.

## 13. Sprint yürütme kaydı

Lokal uygulaması tamamlanan sprint: 8/8. Tam dış kabulü tamamlanan sprint: 6/8; MR07-07 gerçek cihaz/internal build ile MR08 PostgreSQL/admin/provider/cihaz/çok günlük yayın kapıları BEKLİYOR. Manuel başlatılan çalışma: 1. Tamamlanmış zamanlanmış otomasyon çalışması: 8/8. `Öğün Hatırlatmaları — 8 Sprint` otomasyonu sekizinci kapanış denetimi sonunda PAUSED durumuna alındı; yetkisiz aktivasyon yapılmadı.

### MR-01 — Tamamlandı

- Başlangıç: 2026-08-29 00:34, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 00:43:45, Europe/London (UTC+01:00).
- Başlatma: Kullanıcının "başlayalım" talebi; tek çalışmada tam MR-01.
- Teslim: Altı MR01 iş maddesi, Bölüm 10'daki kesinleşen sözleşme, yan etkisiz Java sözleşmesi, 15 evidence + 36 karar + 10 saat sınırı fixture'ı.
- Dosyalar: Bu plan; `src/main/java/com/grun/calorietracker/service/reminder/MealReminderContract.java`; `src/test/java/com/grun/calorietracker/service/reminder/MealReminderContractTest.java`; `src/test/resources/meal-reminders/contract-v1.json`.
- Komut: `mvnw.cmd -o -Dmaven.repo.local=D:\GRUN_CalorieTracker\grun_calorie_tracker\.m2\repository -Dtest=MealReminderContractTest test`.
- Test ortamı: Yalnızca komut sürecinde `JAVA_HOME=C:\Program Files\Java\jdk-17`, `MAVEN_OPTS=-Xmx768m -Dfile.encoding=UTF-8`; global Java ayarları değiştirilmedi.
- Sonuç: BUILD SUCCESS; 77 test/kontrol, 0 failure, 0 error, 0 skipped. Derleme + hedefli test süresi 2 dakika 21 saniye. Bütün backend kaynakları ve test kaynakları derlendi; bütün test suite'i çalıştırılmadı.
- Kanıt: `target/surefire-reports/com.grun.calorietracker.service.reminder.MealReminderContractTest.txt` ve aynı sınıfın XML raporu. Raporlar sonraki build'lerde değişebilir; bu kayıt çalıştırma sonucunu saklar.
- Ek kontrol: Plan diff whitespace kontrolü geçti. Derleyicide mevcut deprecated API ve annotation processor uyarıları var; MR-01'i engellemedi.
- Kalan MR-01 engeli: Yok. Karar motoru, veritabanı, admin UI, gerçek cihaz ve provider doğrulamaları henüz uygulanmadı; ilgili sonraki sprintlerin kapsamıdır.
- Korunan sınırlar: Mevcut AI geçmişi ve mobil AI ekranlarındaki ilgisiz değişikliklere dokunulmadı. `.env`, pom.xml, AWS/build ayarları ve mobil kaynaklar değiştirilmedi; gerçek bildirim gönderilmedi, deployment veya migration çalıştırılmadı.

### MR-02 — Tamamlandı

- Başlangıç: 2026-08-29 01:03, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 01:21:41, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 1. zamanlanmış çalışması; tek çalışmada tam MR-02.
- Teslim: Altı MR02 iş maddesi; cache'siz ve sınırlı batch snapshot; food/recipe öğün projection'ları; ortak Dashboard/reminder etkin hedef ve kcal hesabı; aktif fasting toplu okuması; IANA/yerel gün sınırı; teknik hata için UNKNOWN/null davranışı; hedef indeksi.
- Dosyalar: Bu plan; `src/main/java/com/grun/calorietracker/repository/projection/MealReminderMealAggregateProjection.java`; `MealReminderUnresolvedPlanProjection.java`; `src/main/java/com/grun/calorietracker/service/support/DailyCalorieBudgetSupport.java`; `src/main/java/com/grun/calorietracker/service/reminder/DailyMealReminderSnapshot.java`; `DailyMealReminderSnapshotService.java`; `FoodLogsRepository.java`; `RecipeLogRepository.java`; `GoalRepository.java`; `FastingSessionRepository.java`; `MealPlanItemConsumptionRepository.java`; `DashboardServiceImpl.java`; `V230__add_meal_reminder_snapshot_indexes.sql`; `src/test/java/com/grun/calorietracker/service/reminder/DailyMealReminderSnapshotServiceTest.java`.
- Başarılı komut: `mvnw.cmd '-Dtest=MealReminderContractTest,DailyMealReminderSnapshotServiceTest,DashboardServiceImplTest' test`; yalnızca komut sürecinde Java 17 ve mevcut `C:\Users\Lenovo\.m2\repository` kullanıldı.
- Sonuç: BUILD SUCCESS; 87 test, 0 failure, 0 error, 0 skipped. Dağılım: Dashboard 3, MR-02 snapshot 7, MR-01 regresyon 77. Bütün main ve test kaynakları Java 17 ile derlendi; bütün backend suite'i çalıştırılmadı.
- Ara denemeler: Sandbox Maven deposu `C:\.m2\repository` oluşturulamadığı için ilk compile başlamadı. İlk hedefli test çalışması yeni test helper'ındaki iç içe Mockito stubbing hatasıyla 86 testte 2 error verdi; test helper düzeltildi ve yukarıdaki aynı kapsam yeniden çalıştırılarak temiz geçti. Ürün kodunda bu iki hataya bağlı runtime değişiklik yapılmadı.
- Kanıt: `target/surefire-reports/com.grun.calorietracker.service.DashboardServiceImplTest.txt`, `...DailyMealReminderSnapshotServiceTest.txt` ve `...MealReminderContractTest.txt`; `git diff --check` whitespace hatası vermedi (yalnızca mevcut LF/CRLF bilgilendirmeleri).
- Kabul kanıtı: Dashboard ve reminder aynı ortak resolver/calculator'ı kullanıyor; food+recipe ayrı aggregate edilip bir kez birleşiyor; plan tablosu kalori kaynağı değil ve gerçek log bağı olmayan LOGGED/PARTIALLY_CONSUMED/REPLACED kayıt UNKNOWN/null üretiyor; half-open gün sınırları ve Dublin/New York IANA tarih ayrımı test edildi; iki ardışık çağrıda değişen günlük/hedef yeniden okundu; repository hatası UNKNOWN/null oldu; 501 aday reddedildi.
- Kalan MR-02 engeli: Yok. Native sorgular ve V230 gerçek PostgreSQL üzerinde uygulanmadı; migration/DB entegrasyonu MR-08 dış ortam kapısında ayrıca doğrulanmalıdır. Bu, lokal birim/derleme kabulünü engellemedi ve yapılmış gibi raporlanmadı.
- Korunan sınırlar: AI request history ve mobil AI çalışmalarındaki mevcut değişikliklere dokunulmadı. Mobil repo, `.env`, pom.xml, AWS/build/deploy ayarları değiştirilmedi; migration çalıştırılmadı, gerçek Notification/push oluşturulmadı. Varsayılan mod OFF kaldı.

### MR-03 — Tamamlandı

- Başlangıç: 2026-08-29 01:33, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 01:45:42, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 2. zamanlanmış çalışması; tek çalışmada tam MR-03.
- Teslim: Altı MR03 iş maddesi; sürümlü ve güvenlik sınırlarını gevşetemeyen policy; Clock tabanlı yan etkisiz karar motoru; stable reason/kcal reason; EVENING alternatifi; typed kcal parametresi ve TR/EN fallback; timezone/quiet/fasting/limit/cooldown kontrolü; salt-okunur preview ve 7 günlük redakte dry-run kayıt zinciri.
- Dosyalar: Bu plan; `src/main/java/com/grun/calorietracker/service/reminder/MealReminderPolicy.java`; `MealReminderRuntimeState.java`; `MealReminderDecision.java`; `MealReminderDecisionEngine.java`; `MealReminderDryRunService.java`; `src/main/java/com/grun/calorietracker/entity/MealReminderDryRunDecisionEntity.java`; `src/main/java/com/grun/calorietracker/repository/MealReminderDryRunDecisionRepository.java`; `src/main/resources/db/migration/V231__add_meal_reminder_dry_run_decisions.sql`; `src/test/java/com/grun/calorietracker/service/reminder/MealReminderDecisionEngineTest.java`; `MealReminderDryRunServiceTest.java`.
- Başarılı komut: `mvnw.cmd '-Dtest=MealReminderContractTest,DailyMealReminderSnapshotServiceTest,MealReminderDecisionEngineTest,MealReminderDryRunServiceTest,DashboardServiceImplTest' test`; yalnızca komut sürecinde Java 17 ve mevcut `C:\Users\Lenovo\.m2\repository` kullanıldı.
- Sonuç: BUILD SUCCESS; 132 test, 0 failure, 0 error, 0 skipped. Dağılım: Dashboard 3, MR-02 snapshot 7, MR-01 sözleşme 77, MR-03 engine 42, MR-03 dry-run 3. Bütün main ve test kaynakları Java 17 ile derlendi; bütün backend suite'i çalıştırılmadı.
- Ara deneme: İlk hedefli koşu 132 testte 2 failure verdi. Fixture D12, geçersiz hedef kcal nedeninin null kalan değerden önce değerlendirilmesi gerektiğini gösterdi; sıra `INVALID_TARGET` olacak şekilde düzeltildi. DST-gap testi varsayılan 22:00–08:00 quiet hours ile çakıştığı için slot davranışı izole edilemiyordu; test eşit quiet saatleriyle izole edildi. Aynı suite yeniden çalıştırılıp tamamen geçti.
- Kanıt: `target/surefire-reports/com.grun.calorietracker.service.reminder.MealReminderDecisionEngineTest.txt` (42/42), `...MealReminderDryRunServiceTest.txt` (3/3), snapshot/contract/dashboard raporları; `git diff --check` whitespace hatası vermedi (yalnız mevcut LF/CRLF bilgilendirmeleri).
- Kabul kanıtı: MR-01'deki 36 karar fixture'ının tamamı gerçek motorla birebir geçti; deterministik tekrar, DINNER/DAILY_CATCHUP tek EVENING slotu, kaçırılan slotta backlog yok, expired claim STALE, DST gap skip, timezone gün uyuşmazlığı fail-closed, cooldown sınırları, güvenli 651 kcal yuvarlama ve unsupported-language EN fallback ayrıca test edildi. Preview repository ile hiç etkileşmedi; kayıtlı dry-run expiry temizliği ve 7 günlük retention doğrulandı.
- Kalan MR-03 engeli: Yok. V231 gerçek PostgreSQL'e uygulanmadı ve admin preview endpoint/UI henüz yoktur; bunlar sırasıyla MR-08 dış ortam doğrulaması ve MR-05/MR-06 kapsamıdır. Bu sprint gerçek Notification, provider veya sayaç entegrasyonunu tamamlamış gibi raporlanmaz.
- Korunan sınırlar: AI request history ve mobil AI çalışmalarındaki mevcut değişikliklere dokunulmadı. Mobil repo, `.env`, pom.xml, AWS/build/deploy ayarları değiştirilmedi; migration çalıştırılmadı, gerçek kullanıcıya push/Notification gönderilmedi. Varsayılan sözleşme modu OFF kaldı.

### MR-04 — Tamamlandı

- Başlangıç: 2026-08-29 02:03, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 02:28:30, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 3. zamanlanmış çalışması; tek çalışmada tam MR-04. Toplam zamanlanmış otomasyon çalışması: 3/8.
- Teslim: Dokuz MR04 iş maddesi; bounded `nextEvaluationAt` taraması, deterministik jitter/süre/hata kaydı ve kullanıcı izolasyonu; PostgreSQL `SKIP LOCKED` schedule/outbox lease'leri; kullanıcı+yerel gün+slot ve outbox+token DB tekilleştirmesi; atomik günlük/rolling/catchup rezervasyonu; transaction içinde Notification/occurrence/outbox commit'i ve transaction dışında provider I/O; dispatch öncesi taze snapshot/policy/preference/quiet/fasting/limit revalidation ve güncel render; token bazlı attempt/invalid-token kapatma; provider acceptance/receipt/retry/final/UNKNOWN ayrımı, slotla sınırlı TTL, exponential bounded backoff/max attempt; rutin su/oruç/adım cooldown okuması; bağımsız deployment kill switch ve varsayılan `OFF`.
- Ana dosyalar: `MealReminderDeliveryProperties`; `MealReminderScheduler`; `MealReminderCandidateWorker`; `MealReminderClaimService`; `MealReminderReservationService`; `MealReminderOutboxDispatcher`; `MealReminderDispatchStateService`; `MealReminderRuntimeStateFactory`; schedule/budget/occurrence/outbox/attempt entity+repository+enum'ları; `PushProviderClient` ve Expo/FCM/OneSignal TTL/UNKNOWN uyarlamaları; `V232__add_reliable_meal_reminder_delivery.sql`.
- Test dosyaları: `MealReminderClaimServiceTest`; `MealReminderReservationServiceTest`; `MealReminderSchedulerTest`; `MealReminderOutboxDispatcherTest`; `MealReminderDispatchStateServiceTest`; `MealReminderDeliverySqlContractTest`; koşullu `MealReminderPostgresConcurrencyIntegrationTest`.
- Başarılı komutlar: Java 17 ile `mvnw.cmd -DskipTests compile`; `mvnw.cmd -DskipTests test-compile`; yalnız MR04 seti; ardından `mvnw.cmd '-Dtest=MealReminderContractTest,DailyMealReminderSnapshotServiceTest,MealReminderDecisionEngineTest,MealReminderDryRunServiceTest,DashboardServiceImplTest,MealReminderClaimServiceTest,MealReminderOutboxDispatcherTest,MealReminderDispatchStateServiceTest,MealReminderDeliverySqlContractTest,MealReminderReservationServiceTest,MealReminderSchedulerTest,MealReminderPostgresConcurrencyIntegrationTest' test`.
- Sonuç: BUILD SUCCESS; birleşik 143 test, 0 failure, 0 error, 2 skipped. MR04 lokal testleri 9/9 geçti; dış PostgreSQL sınıfındaki 2 test `GRUN_RUN_MEAL_REMINDER_POSTGRES=true` ve gerçek test DB olmadığı için koşullu olarak atlandı. Bütün main/test kaynakları Java 17 ile derlendi; bütün backend suite'i çalıştırılmadı.
- Ara denemeler: Sandbox Maven deposu ilk compile'ı başlatmadı; sistem Java'sı 17 olmadığı için ikinci compile `invalid target release: 17` verdi, yalnız komut sürecinde `JAVA_HOME=C:\Program Files\Java\jdk-17` kullanılarak temiz geçti. Genişletilmiş MR04 testinin ilk denemesi yeni fixture'daki `MIDDAY`/`LUNCH` ad hatasıyla test-compile aşamasında durdu; fixture düzeltildi ve aynı set ile birleşik regresyon yeniden temiz geçti. Ürün çalışma zamanı kodu bu fixture hatası için değiştirilmedi.
- Kabul kanıtı: SQL sözleşmesi unique occurrence/attempt/budget ile bounded `FOR UPDATE SKIP LOCKED` claim'leri doğruladı; kill switch kapalıyken claim ve provider çağrısı sıfır; slot expiry'ye kalan 90 saniye provider TTL'si olarak aktarıldı; UNKNOWN sonucu retry zamanlamadı ve ihtiyatlı rezervasyonu korudu; bir kullanıcı hatası aynı sayfadaki sonraki kullanıcıyı engellemedi; notification/outbox rezervasyon testi dört kaydı aynı servis transaction'ında oluşturdu. Provider kabulü yalnız `PROVIDER_ACCEPTED`, receipt ayrı durumdur.
- Dış kabul kapısı — BEKLİYOR: V232 gerçek PostgreSQL'e uygulanmadı. İki gerçek connection ile `SKIP LOCKED` ve PostgreSQL constraint metadata testleri yazıldı fakat ortam bayrağı/test DB olmadan çalıştırılmadı; MR-08 dış ortam doğrulamasında çalıştırılmalıdır. Bu dış kapı lokal MR-04 uygulama/test kabulünü engellemedi ve yapılmış gibi raporlanmadı.
- Kalan MR-04 engeli: Yok. Canlıya çıkış hazır/aktif değildir; MR-05–MR-08 ve dış kapılar bekliyor.
- Korunan sınırlar: Mevcut AI request recovery ve mobil AI değişiklikleri korunarak dokunulmadı. Mobil repo, `.env`, pom.xml, AWS/build/deploy ayarları değiştirilmedi; migration uygulanmadı, git push/merge yapılmadı, gerçek Notification/push oluşturulmadı. Varsayılan gönderim modu ve deployment kapısı `OFF` kaldı.

### MR-05 — Tamamlandı

- Başlangıç: 2026-08-29 02:33, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 02:56:47, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 4. zamanlanmış çalışması; tek çalışmada tam MR-05. Toplam zamanlanmış otomasyon çalışması: 4/8.
- Teslim: Dokuz MR05 iş maddesi; DB-backed DRAFT/ACTIVE/ARCHIVED policy ve optimistic locking; hard limit/saat sırası/pilot validation; config/history/summary/redakte decisions/sentetik preview/draft/publish-request/rollback-request/emergency-stop/reopen-request/test-send-request ve definition publish kontratları; permission matrix'te açık GROWTH_READ/GROWTH_MANAGE yönlendirmesi; mevcut MFA'lı maker-checker'a policy publish/rollback, metin publish, reopen ve test-send eylemleri; gerekçeli tek-adımlı acil durdurma; korumalı Notification Definitions ve eski edit bypass engeli; TR/EN zorunluluğu, yalnız `{remainingKcal}` typed placeholder'ı ve sabit `diary` route/channel/severity; DB aktif policy'nin scheduler/outbox karar zincirine bağlanması; yalnız pilot kullanıcı, açık kullanıcı tercihleri ve saatte bir test-send sınırı; 30 günlük minimal test-send retention ve kullanıcı silmede cascade; redakte admin karar/özet yanıtları.
- Ana dosyalar: `V233__add_meal_reminder_admin_policy.sql`; `MealReminderPolicyEntity`, `MealReminderAdminTestSendEntity` ve repository'leri; admin policy/preview/reason/definition DTO'ları; `AdminMealReminderAutomationController`; `AdminMealReminderAutomationService` ve implementasyonu; `AdminApprovalActionType`, `AdminApprovalServiceImpl`, `AdminApprovalController`; `AdminPermissionMatrix`; `AdminNotificationDefinitionServiceImpl`; `NotificationDefinitionPolicy`; `MealReminderPolicyFactory` ve MR-04 scheduler/reservation/dispatch/runtime entegrasyonları.
- Test dosyaları: `AdminMealReminderAutomationServiceImplTest`; `MealReminderAdminPermissionMatrixTest`; `MealReminderAdminSqlContractTest`; genişletilen `AdminApprovalServiceImplTest` ve `AdminNotificationDefinitionServiceImplTest`; MR-04 regresyon testleri.
- Başarılı komutlar: Java 17 ile `mvnw.cmd -DskipTests test-compile`; MR05+MR04 hedefli suite; ardından `mvnw.cmd '-Dtest=MealReminderContractTest,DailyMealReminderSnapshotServiceTest,MealReminderDecisionEngineTest,MealReminderDryRunServiceTest,DashboardServiceImplTest,MealReminderClaimServiceTest,MealReminderOutboxDispatcherTest,MealReminderDispatchStateServiceTest,MealReminderDeliverySqlContractTest,MealReminderReservationServiceTest,MealReminderSchedulerTest,MealReminderPostgresConcurrencyIntegrationTest,AdminMealReminderAutomationServiceImplTest,MealReminderAdminPermissionMatrixTest,MealReminderAdminSqlContractTest,AdminApprovalServiceImplTest,AdminNotificationDefinitionServiceImplTest' test`.
- Sonuç: BUILD SUCCESS; birleşik 158 test, 0 failure, 0 error, 2 skipped. İki skip önceki gerçek PostgreSQL concurrency sınıfının dış ortam testleridir. Bütün main/test kaynakları Java 17 ile derlendi; bütün backend suite'i çalıştırılmadı.
- Ara denemeler: İlk test-compile, yeni constructor bağımlılıkları eklenen iki eski fixture nedeniyle durdu; fixture'lar yeni policy/approval bağımlılıklarıyla güncellendi. İlk hedefli koşu 21 testte 1 failure verdi: yeni DTO/migration minimum meal cooldown'u 120 dakika kullanırken mevcut V1 karar sözleşmesi 180 dakika gerektiriyordu. Güvenlik sınırı gevşetilmedi; DB constraint, seed, DTO ve fixture 180 dakikaya yükseltildi, aynı suite ve birleşik regresyon temiz geçti.
- Kabul kanıtı: Policy version conflict yazmadan önce `OptimisticLockException`; hard limit aşımı repository'ye gitmeden red; emergency stop anında ACTIVE policy'yi OFF etkisine aldı ve deployment kapısını açmadı; admin path GET/POST doğrudan GROWTH_READ/GROWTH_MANAGE; öğün policy publish mevcut re-auth checker kapısından yürüdü; eski definition endpoint'i korumalı öğün metnini reddetti; yeni definition publish bilinmeyen placeholder'ı reddetti; V233 tek ACTIVE index, OFF seed, korumalı TR/EN definitions ve token içermeyen cascade test-send kaydı içerdi. Preview sentetik veriden çalışır ve provider/user diary repository bağımlılığı yoktur.
- Dış kabul kapısı — BEKLİYOR: V233 gerçek PostgreSQL'e uygulanmadı; endpoint'ler çalışan admin ortamında smoke edilmedi. Önceki iki gerçek PostgreSQL `SKIP LOCKED` testi de ortam bayrağı/test DB bekliyor. Bunlar MR-08 dış ortam doğrulamasında çalıştırılmalıdır ve yapılmış gibi raporlanmamıştır.
- Kalan MR-05 engeli: Yok. Canlıya çıkış hazır/aktif değildir; MR-06–MR-08, gerçek DB migration, admin UI ve dış kabul kapıları bekliyor.
- Korunan sınırlar: Mevcut AI request recovery ve mobil AI değişikliklerine dokunulmadı. Mobil repo, `.env`, pom.xml, AWS/build/deploy ayarları değiştirilmedi; migration uygulanmadı, git push/merge yapılmadı, gerçek Notification/push oluşturulmadı. V233 aktif policy seed'i `OFF`; deployment ve genel push kapıları kapalı kaldı.

### MR-06 — Tamamlandı

- Başlangıç: 2026-08-29 03:03, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 03:13:20, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 5. zamanlanmış çalışması; tek çalışmada tam MR-06. Toplam zamanlanmış otomasyon çalışması: 5/8.
- Teslim: Yedi MR06 iş maddesi; `modules/notifications/MealReminderAutomationView` ve küçük App navigation/tab/render entegrasyonu; GROWTH_READ görünürlük ve GROWTH_MANAGE salt-okunur/mutation ayrımı; policy mode/deployment gate/general push ayrımı; schedule, quiet hours, hard limit, cooldown, pilot ve kcal draft formu; kaydet ile maker-checker yayın talebinin ayrılması; optimistic conflict, unsaved draft, pending approval ve API loading/error/empty durumları; sentetik kullanıcı-local timezone preview; dostane reason metinleri ve teknik details; redakte/paged karar listesi ve policy history; gerekçeli emergency stop/reopen ve no-backfill açıklaması; Notification Definitions bağlantısı ve korumalı meal copy için normal save yerine gerekçeli maker-checker yayın talebi; keyboard-semantik form/table ve 1050/680px responsive düzen.
- Dosyalar: `admin-ui/src/modules/notifications/MealReminderAutomationView.tsx`; `NotificationDefinitionsView.tsx`; `admin-ui/src/App.tsx`; `types.ts`; `styles.css`; `admin-ui/scripts/test-meal-reminder-automation.mjs`; `admin-ui/package.json`; production build tarafından yenilenen `src/main/resources/static/admin-ui/index.html` ve hash'li asset seti; bu plan.
- Başarılı komutlar: `npm run test:meal-reminder-automation`; iki kez `npm run build` (son değişiklikten sonra tekrar); `npm run test:production`; son build sonrasında tekrar `npm run test:meal-reminder-automation` ve `node scripts/test-production-readiness.mjs`.
- Sonuç: Production TypeScript/Vite BUILD SUCCESS; 636 module transform edildi. Yeni meal reminder kontrat testi geçti. Tam admin production suite'indeki 15 release kontrolü geçti: foundation, growth, engagement, commercial, AI operations, campaign operations, meal reminder automation, catalog, recipe, promotion, system reliability, customer 360, quality workbench, production readiness ve admin security session. Son bundle kanıtı: entry JS 666,832 byte, total JS 1,333,380 byte, CSS 178,595 byte; mevcut bütçelerin altında.
- Kabul kanıtı: Ekran deep-link/nav/tab içinde ve GROWTH_READ matriksinde; mutation düğmeleri GROWTH_MANAGE olmadan disabled/read-only; admin/browser ile örnek kullanıcı timezone saati ayrı; kahvaltı/önceki öğün eksikliği meal-specific evening yerine genel check-in olarak okunabilir; Save draft ve Request publication ayrı; emergency stop sonrası reopen onaylı ve expired slot replay yok; karar tablosu health/email/token göstermediğini açıkça belirtir ve lokal sayfalanır; protected copy CTA'sı `publish-request` kullanır. Production-readiness testleri eski Broadcasts/System Rules/Push Delivery render ve navigasyon kontratlarını da geçti.
- Ara not: Vite mevcut büyük chunk uyarısını verdi; release gate'in geçerli 750,000 byte tek chunk ve 1,500,000 byte toplam JS sınırları aşılmadı. İşlev veya test kapsamı bu uyarı nedeniyle küçültülmedi.
- Dış kabul kapısı — BEKLİYOR: Çalışan/sign-in admin ortamında görsel browser smoke, gerçek API response ve maker-checker iki hesap akışı bu otomasyonda yapılmadı; V233 migration da uygulanmış değildir. Bunlar MR-08 dış ortam doğrulamasında yapılmalıdır ve yapılmış gibi raporlanmamıştır.
- Kalan MR-06 engeli: Yok. Canlıya çıkış hazır/aktif değildir; MR-07–MR-08, gerçek admin ortamı ve dış kabul kapıları bekliyor.
- Korunan sınırlar: Backend çalışma zamanı/AWS/build config, mobil repo ve mevcut AI recovery değişiklikleri değiştirilmedi. Admin production asset'i lokal build ile güncellendi; deployment, migration, git push/merge, PILOT/LIVE aktivasyonu veya gerçek kullanıcı push'u yapılmadı. Gönderim OFF kaldı.

### MR-07 — Lokal uygulama tamamlandı; gerçek cihaz kabulü BEKLİYOR

- Başlangıç: 2026-08-29 02:33, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 04:00:44, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 6. zamanlanmış çalışması; yalnız MR-07 ele alındı. Toplam tamamlanmış zamanlanmış otomasyon çalışması: 6/8. Sprint hedeflenen 30 dakikayı aştı; backend tam derleme ve Spring controller context testi ile iki Maven derleme/test çevrimi beklenenden uzun sürdü, test kapsamı küçültülmedi.
- Teslim: Mevcut push/meal preference ve token lifecycle'ını değiştirmeyen mobil response handler; foreground/background/cold-start/login-resume ve in-app route çözümü; hesap kimliği ön kontrolü ve authenticated backend notification/occurrence sahipliği; mevcut istemciler için `diary` fallback; güncel güne ait breakfast/lunch/dinner mesajını tarih+öğün parametreli food search'e, catch-up/eski bildirimi tarihli Diary'ye yönlendirme; Diary'nin route tarihinden backend daily summary yüklemesi; push kcal metninin veri kaynağı olmaması; notification başına tekil OPEN, food/recipe log başına tekil iki saatlik korelasyon ve explicit true→false preference opt-out kaydı; provider acceptance/receipt/open/correlation/opt-out admin summary metrikleri; tüm provider payload'larında recipient account context; gerçek cihaz QA matrisi ve ölçüm semantiği dokümanı.
- Backend dosyaları: `V234__add_meal_reminder_interactions.sql`; `MealReminderInteractionEntity`, `MealReminderInteractionType`, repository ve service; `MealReminderInteractionRequestDto`, `MealReminderNotificationContextDto`; `NotificationController`; `MealReminderOccurrenceRepository`; `MealReminderDeliveryAttemptRepository`; `UserServiceImpl`; `AdminMealReminderAutomationServiceImpl`; Expo/FCM/OneSignal provider client'ları; `MEAL_REMINDER_MOBILE_MEASUREMENT_2026-08-29.md`.
- Mobil dosyalar: `src/services/mealReminderNotificationService.ts`; `src/hooks/useNotificationRouting.ts`; `src/app/_layout.tsx`; `src/screens/Diary.tsx`; `src/screens/Notifications.tsx`; `src/services/notificationService.ts`; `src/types/native-modules.d.ts`; `qa/meal-reminder-notification-contract.mjs`; `qa/MEAL_REMINDER_PUSH_QA.md`; `package.json`.
- Test dosyaları: `MealReminderInteractionServiceTest` (5); `MealReminderInteractionSqlContractTest` (1); genişletilen `NotificationControllerTest`, `UserServiceImplTest` ve `AdminMealReminderAutomationServiceImplTest`; mobil 13 maddelik statik sözleşme.
- Başarılı komutlar: Mobilde `npm run typecheck`; `npm run qa:meal-reminder` (13/13). Backend'de Java 17 ve proje-local Maven repository ile `NotificationControllerTest` (4/4); yeni interaction/admin hedefli set (10/10); ardından MR01–MR07 regresyon seti `MealReminderContractTest,DailyMealReminderSnapshotServiceTest,MealReminderDecisionEngineTest,MealReminderDryRunServiceTest,DashboardServiceImplTest,MealReminderClaimServiceTest,MealReminderOutboxDispatcherTest,MealReminderDispatchStateServiceTest,MealReminderDeliverySqlContractTest,MealReminderReservationServiceTest,MealReminderSchedulerTest,MealReminderPostgresConcurrencyIntegrationTest,AdminMealReminderAutomationServiceImplTest,MealReminderAdminPermissionMatrixTest,MealReminderAdminSqlContractTest,AdminApprovalServiceImplTest,AdminNotificationDefinitionServiceImplTest,MealReminderInteractionServiceTest,UserServiceImplTest`.
- Sonuç: Mobil TypeScript temiz ve 13/13 kontrat geçti. Backend controller 4/4, son yeni hedefli set 10/10 ve birleşik regresyon 182 testte 0 failure/0 error; dış PostgreSQL concurrency sınıfındaki 2 test ortam olmadığı için SKIP. H2 Spring context sırasında önceki MR-04 scheduler `ON CONFLICT` sorgusuna ilişkin bilinen/ayrı log üretildi, controller suite yine BUILD SUCCESS oldu.
- Ara denemeler: İlk backend hedefli koşu, Spring'in AFTER_COMMIT listener için `REQUIRES_NEW` zorunluluğunu saptadı; propagation açıkça düzeltildi ve controller suite 4/4 geçti. Bir Maven koşusu ortak kullanıcı `.m2` içindeki Flyway jar erişiminde fatal compile hatası verdi; mevcut proje-local offline repository ile güvenli biçimde tekrarlandı ve bütün doğrulamalar geçti. Ürün veya test kapsamı değiştirilmedi.
- Kabul kanıtı: Authorized durumda mevcut kod yalnız `existingStatus !== granted` iken OS permission ister; yeni form/prompt yoktur. Mobil listener hesap kimliği uyuşmazlığını route öncesi reddeder, backend notification+occurrence'ı authenticated user altında arar ve foreign notification'da interaction yazmaz. `OPEN:{notificationId}`, `CONVERSION:FOOD:{id}` ve `CONVERSION:RECIPE:{id}` unique event key'leri DB `ON CONFLICT DO NOTHING` ile çoklu instance/idempotency sağlar. Stale ve DAILY_CATCHUP günlük fallback'tir. Diary tarihli dashboard query çalıştırır. Eski AI `VIEW_AI_RESULT` route sözleşmesi mobil kontratta korunmuştur. Metrik açıklaması iki saatlik ilişkiyi korelasyon olarak etiketler, nedensellik iddiası yapmaz.
- Dış kabul kapısı — BEKLİYOR: Gerçek cihaz ve development/internal build ile foreground/background/killed/login-resume, iki hesap izolasyonu, gerçek Expo/provider receipt ve opt-out gözlemi yapılmadı. Expo Go/simülatör prod kabulü sayılmadı. V234 gerçek PostgreSQL'e uygulanmadı. `qa/MEAL_REMINDER_PUSH_QA.md` kanıt matrisi MR-08 dış kabulüne hazırlandı.
- Durum: MR07-01–MR07-06 tamamlandı. MR07-07 dış ortam gerektirdiği için açık; bu kayıt MR-07'yi tam dış kabul almış göstermiyor. MR-08 lokal release hazırlığı bağımlı kodu kullanabilir, fakat PILOT/LIVE veya tamamlandı kararı bu kapı kapanmadan verilemez.
- Korunan sınırlar: Kullanıcının mevcut mobil AI recovery değişiklikleri ve backend AI değişiklikleri korunup üzerine yazılmadı. `.env`, AWS/build/deploy ayarları, prod config ve kullanıcı tercih varsayılanları değiştirilmedi; migration uygulanmadı, git push/merge yapılmadı, gerçek push üretilmedi, PILOT/LIVE açılmadı. Gönderim modu OFF kaldı.

### MR-08 — Lokal release hazırlığı tamamlandı; dış kabul/yayın kapıları BEKLİYOR

- Başlangıç: 2026-08-29 04:03, Europe/London (UTC+01:00).
- Doğrulama sonu: 2026-08-29 04:17:47, Europe/London (UTC+01:00).
- Başlatma: `n-hat-rlatmalar-8-sprint` otomasyonunun 7. zamanlanmış çalışması; yalnız MR-08 lokal release hazırlığı ele alındı. Toplam tamamlanmış zamanlanmış otomasyon çalışması: 7/8.
- Teslim: MR08-01 A01–A28 kanıt matrisi; MR08-02 V230–V234 tekil/ardışık/current migration kontratı; MR08-03 bounded batch, 500 üst sınır, `SKIP LOCKED`, süre kaydı ve aday/outbox hata izolasyonu kanıtı; MR08-04 kill-switch/preference/timeout/crash/restart/multi-instance tatbikat sözleşmesi; MR08-05–MR08-06 OFF→DRY_RUN→PILOT→5%→25%→100% ve her aşamada iki tam yerel gün kapıları; MR08-07 provider/receipt, queue lag, task duration, opt-out ve sıfır toleranslı kritik alarm eşikleri; MR08-08 emergency rollback, provider-accepted geri alınamazlığı, mobile handoff ve açık dış kanıt listesi. Admin summary ekranına provider acceptance, receipt failure, unique open, iki saatlik meal-log korelasyonu ve opt-out metrikleri ile nedensellik sınırı eklendi.
- Dosyalar: `.gitignore` içindeki iki kesin doküman istisnası; `docs/MEAL_REMINDER_ACCEPTANCE_MATRIX_2026-08-29.md`; `docs/MEAL_REMINDER_RELEASE_RUNBOOK_2026-08-29.md`; `MealReminderReleaseReadinessContractTest.java`; genişletilen `MealReminderOutboxDispatcherTest.java` ve `MealReminderSchedulerTest.java`; `admin-ui/src/types.ts`; `admin-ui/src/modules/notifications/MealReminderAutomationView.tsx`; `admin-ui/scripts/test-meal-reminder-automation.mjs`; lokal production build tarafından yenilenen `src/main/resources/static/admin-ui/index.html` ve hash'li asset seti; bu plan. Mobil kaynak değiştirilmedi; MR-07 handoff'u yeniden doğrulandı.
- Başarılı komutlar: Java 17/proje-local Maven repository ile MR08 hedefli 18 test; ardından `mvnw.cmd '-Dtest=*MealReminder*Test' test`; adminde `npm run test:meal-reminder-automation`, `npm run build`, `npm run test:production`; mobilde `npm run typecheck` ve `npm run qa:meal-reminder`.
- Sonuç: Backend ilk hedefli set 18/18 geçti; 100 adaylık tam varsayılan sayfa süre kontratı eklendikten sonra nihai birleşik MealReminder suite 158 testte 0 failure/0 error ile geçti, dış PostgreSQL concurrency sınıfındaki 2 test ortam olmadığı için SKIP. Admin TypeScript/Vite production build 636 modülle geçti; meal reminder kontratı ve tam production release suite temiz, entry JS 667,894 byte, toplam JS 1,334,442 byte, CSS 178,595 byte ve mevcut bütçeler içinde. Mobil TypeScript temiz ve 13/13 notification routing kontratı geçti.
- Ara denemeler: İlk Maven çağrısı sandbox dışı `C:\.m2\repository` oluşturamadı; ikinci çağrı global `JAVA_HOME` Java 8'e işaret ettiği için `invalid target release: 17` ile test başlamadan durdu. Yalnız komut sürecinde `JAVA_HOME=C:\Program Files\Java\jdk-17`, `MAVEN_OPTS=-Xmx768m -Dfile.encoding=UTF-8` ve proje-local `.m2` kullanılarak aynı kapsam çalıştırıldı ve temiz geçti. Hiçbir global ortam veya ürün config'i değiştirilmedi.
- Kabul kanıtı: Kod varsayılanları deployment OFF, policy OFF, general push OFF ve provider LOG olarak kontrat testinde sabitlendi. Migration klasöründe V230–V234 tam, tekil ve V234 current max olarak doğrulandı. Provider timeout sonrası sonuç UNCERTAIN ve tek provider çağrısıdır; bir outbox exception'ı sonraki outbox'ı engellemez. SQL kontratı bounded `FOR UPDATE SKIP LOCKED`, snapshot kontratı 501 adayın reddi, scheduler ise tek aday hatasında sayfanın devamını ve 100 adaylık tam varsayılan mocked sayfanın 5 saniyelik outbox worker aralığının altında tamamlanmasını doğrular. Runbook isim verilmemiş release rollerini açıkça UNASSIGNED/WAITING bırakır; 5/25/100 aşamalarını, eşikleri ve geri alma sınırını sabitler. Admin metrikleri korelasyonu nedensellik olarak sunmaz.
- Dış kabul kapıları — BEKLİYOR: Fresh ve temsilî mevcut PostgreSQL üzerinde Flyway V230–V234; gerçek iki-instance concurrency, DB restart ve temsilî hacim/süre; signed-in admin maker-checker ve dashboard alarm gözlemi; development/internal build fiziksel cihaz matrisi; gerçek provider receipt/invalid-token; tam gün DRY_RUN; iki tam gün PILOT; 5%/25%/100% aşamalarının her birinde iki tam gün ve yeterli örneklem; isimlendirilmiş release owner/checker/device verifier/operations observer. Bunların hiçbiri yapılmış sayılmadı.
- Durum: MR08-01–MR08-04 ve MR08-07–MR08-08'in otomasyon içinde mümkün lokal kısmı tamamlandı. MR08-02 gerçek DB, MR08-03 temsilî dış yük, MR08-04 dış failure drill, MR08-05 gerçek DRY/PILOT ve MR08-06 LIVE gözlem kriterleri açık olduğundan tam dış kabul ve canlıya çıkış **NOT READY**. Sprint lokal hazırlık olarak kapanmıştır; dış yayın kapıları kapanmadan PILOT/LIVE yapılamaz.
- Korunan sınırlar: Kullanıcının AI recovery ve önceki MR değişiklikleri korundu. `.env`, AWS/deploy, prod/build config, kullanıcı tercih varsayılanları ve migration durumu değiştirilmedi; git push/merge, gerçek push, PILOT/LIVE yapılmadı. Varsayılan gönderim OFF kaldı.
- Sekizinci ve son çalışma kapanış denetimi: 2026-08-29 04:33–04:35:17, Europe/London (UTC+01:00). Plan, tek mevcut `AGENTS.md` talimatı ve iki reponun dirty state'i yeniden okundu; yeni ürün kodu veya config değişikliği yapılmadı. Backend `MealReminderReleaseReadinessContractTest` 3/3, admin `npm run test:meal-reminder-automation` ve mobil `npm run qa:meal-reminder` 13/13 tekrar geçti. Release varsayılanları OFF, V230–V234 migration kontratı ve açık dış kapılar değişmedi. Bu kapanış, zamanlanmış çalışma sayısını 8/8 yaptı ve `n-hat-rlatmalar-8-sprint` otomasyonu uygulama aracıyla PAUSED durumuna alındı.
