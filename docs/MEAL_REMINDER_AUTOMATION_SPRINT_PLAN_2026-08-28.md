# GRUN — Öğün ve Günlük Tamamlama Hatırlatmaları Sprint Planı

Tarih: 28 Ağustos 2026<br>
Durum: Plan hazır; uygulama ve canlı gönderim başlamadı.<br>
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
| MR-01 | Davranış sözleşmesi ve test senaryoları | Yok | Planlandı |
| MR-02 | Günlük veri özeti ve doğru kcal kaynağı | MR-01 | Planlandı |
| MR-03 | Karar motoru ve gönderimsiz simülasyon | MR-02 | Planlandı |
| MR-04 | Güvenilir zamanlayıcı ve push kuyruğu | MR-03 | Planlandı |
| MR-05 | Admin API, sürümleme ve yayın güvenliği | MR-03, yayın entegrasyonu için MR-04 | Planlandı |
| MR-06 | Admin kontrol ekranı | MR-05 | Planlandı |
| MR-07 | Mobil yönlendirme ve ölçüm entegrasyonu | MR-04, MR-05 | Planlandı |
| MR-08 | Uçtan uca kabul ve kontrollü yayın | MR-06, MR-07 | Planlandı |

Süre taahhüdü yerine her sprint aşağıdaki kabul kapısıyla kapanır. Backend, admin, mobil ve QA sorumlulukları iş bazında ayrıdır; uygulama başlamadan sahipleri atanır.

## 6. Sprint iş paketleri

### MR-01 — Ürün kuralları ve kontrat

Sorumluluk: Ürün + backend + QA. Gerçek push yok.

- [ ] MR01-01: RECORDED/MISSING/EXCLUDED/UNKNOWN ve önceki öğün bağımlılığı sözleşmesini örnek fixture'larla sabitle.
- [ ] MR01-02: Plansız kullanıcı, kısmi plan, bütünü atlanan öğün, replaced item ve sadece snack durumlarını tanımla; mevcut veriden çıkarılamayan bilgiyi UNKNOWN olarak ayır.
- [ ] MR01-03: Kullanılabilir öğün saati kaynağını doğrula; 10:00/14:30/20:30 fallback ve 60 dakika geçerlilik kurallarını test tablosuna dönüştür.
- [ ] MR01-04: Tercihler, fasting, sessiz saatler, günlük limit, aralık ve gönderim önceliğini sabitle.
- [ ] MR01-05: TR/EN ilk metinlerini ve izinli dinamik alanları tanımla; kcal içermeyen fallback zorunlu olsun.
- [ ] MR01-06: V1 kanal varsayımını sabitle: push + uygulama içi kayıt, fakat liste büyümesini mevcut retention yönetimiyle sınırla. DRY_RUN listeye düşmez.

Kabul: Bölüm 3 ve 7'deki her durum için tek, deterministik beklenen sonuç var. Kayıt eksikliği yemek yenmediği olarak yorumlanmıyor. Mevcut kapalı tercihler korunuyor. Varsayımlar ve ürün kararları birbirinden ayrılmış.

### MR-02 — Günlük veri özeti ve hesaplama

Sorumluluk: Backend + veri/QA.

- [ ] MR02-01: Yalnızca gerekli alanları dönen `DailyMealReminderSnapshot` oluştur: yerel gün/saat dilimi, hedef ve kaynak sürümü, öğün durumları, food/recipe toplamları, fasting ve tercih bilgisi.
- [ ] MR02-02: Dashboard ile ortak kalori/etkin hedef çözümleyicisini kullan veya çıkar; her kullanıcı için bütün dashboard'u üretme.
- [ ] MR02-03: Food/recipe kayıtlarını ve plan-linked tüketimi tekil say; planın kendisini tüketim sayma.
- [ ] MR02-04: Mevcut logDate saklama sözleşmesini doğrula; tarih aralığı [gün başlangıcı, ertesi gün başlangıcı) ve IANA/DST davranışını test et. Bu özellik için eski kayıtların zamanlarını toplu dönüştürme.
- [ ] MR02-05: Öğün ekleme/düzeltme/silme, hedef/plan değişikliği ve kullanıcı timezone değişiminde eski snapshot kullanımını engelle.
- [ ] MR02-06: Aday kullanıcılar için sınırlı projection/batch sorguları ve indeks gereksinimlerini çıkar; N+1 ve tüm kullanıcıları belleğe alma yok.

Kabul: Dashboard ile aynı fixture'larda hedef ve kcal sonuçları eşleşiyor. Tarif, plan, kısmi tüketim ve replacement çift sayılmıyor. Geçmiş tarihe eklenen öğün bugünün hatırlatmasını yanlış durdurmuyor. UNKNOWN teknik hata sessizce sıfır kaloriye dönüşmüyor.

### MR-03 — Karar motoru ve DRY_RUN

Sorumluluk: Backend + QA.

- [ ] MR03-01: Snapshot + policy version + test edilebilir Clock alan, yan etkisiz `MealReminderDecisionEngine` geliştir.
- [ ] MR03-02: BREAKFAST/LUNCH/DINNER/DAILY_CATCHUP adaylarını sırayla değerlendir; karar, reason code, uygun zaman, son geçerlilik, mesaj varyantı ve güvenli parametreler dön.
- [ ] MR03-03: Genel mesajı akşam slotunun alternatifi yap; aynı akşam iki ayrı mesaj üretme. İlk hatırlatmayı kaçıran kullanıcıya eski slotlardan backlog oluşturma.
- [ ] MR03-04: Kcal eligibility ve dil fallback'ini uygula; izinli typed placeholder dışında değer kabul etme.
- [ ] MR03-05: Aynı motoru kullanan sentetik simülasyon ve süre/saklama sınırı olan dry-run karar kaydı ekle. Read-only değerlendirme plan occurrence'ı gibi yan veri oluşturmasın.
- [ ] MR03-06: Saat dilimi değişimi, quiet hours ve genel günlük limitini denetleyen stable neden kodlarını ekle.

Kabul: Aynı girdiler aynı kararı verir. Kahvaltı eksikse akşam öğünü bildirimi sıfırdır. Dry-run sıfır provider çağrısı yapar, gerçek sayaçları ve kullanıcı bildirim listesini değiştirmez.

Örnek nedenler: `PREVIOUS_MEAL_MISSING`, `MEAL_ALREADY_RECORDED`, `NO_DIARY_ACTIVITY`, `PREFERENCE_DISABLED`, `QUIET_HOURS`, `FASTING_ACTIVE`, `DAILY_LIMIT`, `COOLDOWN`, `NO_VALID_TOKEN`, `INVALID_TARGET`, `STALE_SLOT`, `DATA_UNAVAILABLE`, `OUTSIDE_COHORT`, `SYSTEM_DISABLED`. Kcal'nin gizlenme nedeni ile bütün bildirimin atlanma nedeni ayrı alanlardır.

### MR-04 — Zamanlama, tekrar koruması ve teslimat

Sorumluluk: Backend + altyapı + QA. Varsayılan OFF.

- [ ] MR04-01: `nextEvaluationAt` üzerinden sınırlı aday taraması; jitter, işlem süresi ölçümü ve hatalı kullanıcı izolasyonu.
- [ ] MR04-02: Kullanıcı + yerel tarih + slot için DB unique occurrence; policy/template sürümü dedup anahtarına eklenmez. Böylece admin değişikliği aynı gün yeniden gönderim yaratmaz.
- [ ] MR04-03: Kullanıcı günü/sayaç rezervasyonunu ve occurrence claim'ini atomik yap; lease/expiry ve PostgreSQL çoklu instance testleri ekle.
- [ ] MR04-04: Notification/outbox commit'inden sonra ağ çağrısı yap; uzun DB transaction içinde provider bekleme yok.
- [ ] MR04-05: Kuyrukta beklerken güncel preference, kill switch, plan/food ve limit kontrolünü tekrar yap. Uygunluk değişmişse iptal et; gerekirse güncel içerikle yeniden render et.
- [ ] MR04-06: Çoklu cihazda her token attempt'ini ayrı izle; başarılı cihaza yeniden gönderme. Invalid token'ı devre dışı bırak. Sıfır uygun token sonucunu teslim edildi sayma.
- [ ] MR04-07: Provider kabulü, receipt sonucu, hata ve belirsiz sonucu ayır; sınırlı backoff, TTL ve max attempt uygula. Provider'a verilen TTL de slot süresini aşmasın.
- [ ] MR04-08: Su/oruç/adım rutinleriyle dar kapsamlı ortak cooldown entegrasyonu; kritik güvenlik ve AI sonuç bildirimlerinin mevcut davranışını değiştirme.
- [ ] MR04-09: Deployment kill switch kapalıyken PILOT/LIVE dahil sıfır gerçek push; yeni migrations eklemeli ve eski uygulamayla uyumlu olsun.

Teslimat sınırı: DB seviyesinde occurrence ve attempt tekilleştirilir; dış push servisinde uçtan uca exactly-once sözü verilmez. İstek kabul edilmiş olabilirken timeout/crash yaşanırsa `UNKNOWN` olarak kaydet; kanıt olmadan otomatik yeniden gönderip kullanıcıyı tekrar dürtme. İlgili occurrence günlük bütçeyi ihtiyatlı biçimde rezerve eder. Kesin şekilde gönderilmemiş iş için rezervasyon geri alma atomik ve testli olmalıdır.

Kabul: İki worker aynı occurrence'ı eşzamanlı gönderime alamıyor. Restart eski öğünleri yağdırmıyor. Retry daha önce kabul edilmiş token'a gitmiyor. Provider kabulü kullanıcı ekranında gösterildi anlamına gelmiyor. Kapalı mod ve kill switch testleri tüm gönderim yollarını kapsıyor.

### MR-05 — Admin API ve yayın güvenliği

Sorumluluk: Backend + admin güvenliği + QA.

- [ ] MR05-01: Sürümlü taslak/aktif policy modeli, optimistic locking, zamanlama ve limit validation ekle; iki adminin birbirini sessizce ezmesini engelle.
- [ ] MR05-02: Önerilen `/api/v1/admin/meal-reminder-automation` altında config, preview, karar kayıtları, özet, sürüm geçmişi ve durdurma kontratını tanımla. İsimler OpenAPI ile kesinleşir.
- [ ] MR05-03: Endpoint'leri permission matrix'e açıkça ekle: okuma `GROWTH_READ`, taslak yönetim `GROWTH_MANAGE`; yeni path mevcut varsayılan `DASHBOARD_READ` yetkisine düşmesin.
- [ ] MR05-04: Canlı etkinleştirme, kitle genişletme, aktif metin/policy yayınlama ve rollback için mevcut maker-checker yaklaşımını genişlet. Self-approval engeli ve mevcut owner re-auth korunur.
- [ ] MR05-05: Yetkili acil durdurma gerekçeli ve auditli tek işlem olsun; ikinci onayı beklemesin. Yeniden açma onaya tabi olsun.
- [ ] MR05-06: Metinleri Notification Definitions'da tut; otomasyonun korunan tanımlarını eski edit endpoint'i üzerinden onaysız yayınlama yolu bırakma. NotificationDefinitionPolicy'nin dispatch anında farklı sürümle metni ezmesini engelle.
- [ ] MR05-07: Typed placeholder, route allowlist, mesaj boyutu, TR/EN fallback ve numeric alan validasyonu. Admin serbest query/script veya ham kullanıcı verisi çalıştıramasın.
- [ ] MR05-08: Pilot kitleyi backend'de uygula; preview gerçek gönderim yapmasın. Sadece onaylı test hesabına rate-limitli, auditli test gönderimi sağla.
- [ ] MR05-09: Retention, kullanıcı veri silme/export ve audit'te minimal veri sözleşmesini mevcut yaşam döngüsüne bağla; liste/metric/log alanlarında ham sağlık verisi ve token yok.

Kabul: Yetkisiz roller bütün yeni okuma/yazma yollarında engelleniyor. Admin kullanıcı tercihini, deployment kapısını veya hard limitleri aşamıyor. Eski definitions endpoint'i yayın onayını bypass etmiyor. Preview, retry ve test-send gibi alternatif yollar aynı kontrolleri uyguluyor.

### MR-06 — Admin kontrol ekranı

Sorumluluk: Admin frontend + QA.

- [ ] MR06-01: `modules/notifications` altında ayrı Meal Reminder Automation görünümü; mevcut App navigasyonuna küçük entegrasyon.
- [ ] MR06-02: Genel durum, zamanlama/kurallar, metin bağlantıları, senaryo önizleme, karar kayıtları ve yayın geçmişini Bölüm 4'e göre oluştur.
- [ ] MR06-03: Kullanıcı yerel saati ile admin saatini ayır; örnek gün/saat dilimi üzerinden etkiyi göster. "Kaydet" ile "Canlı yayınla" ayrı olsun.
- [ ] MR06-04: Nedenleri okunabilir göster: "Kahvaltı kaydı eksik olduğu için akşam hatırlatması atlandı"; teknik kod ayrıntıda kalsın.
- [ ] MR06-05: Taslak farkı, bekleyen onay, sürüm çatışması, boş/hata/yükleniyor durumları; rol bazlı read-only arayüz.
- [ ] MR06-06: Acil durdurma, durdurma sonrası kuyruk sonucu ve geri dönüşün yeni gönderim tetiklemeyeceğini açıkça göster.
- [ ] MR06-07: Keyboard erişimi, okunabilir formlar, responsive ekran ve mevcut admin production-readiness testlerine yeni kontratlar.

Kabul: Admin sayfadan çıkmadan senaryo için neyin ne zaman/neden gönderileceğini anlayabiliyor. Kayıtlar sayfalı, hassas veriler maskeli. Çalışma modu ve ortam kapısı açıkça farklı. Broadcast/System rules/Push Delivery ekranları geriye dönük çalışıyor.

### MR-07 — Mobil davranış ve ölçüm

Sorumluluk: Mobil + backend + QA.

- [ ] MR07-01: Mevcut mealRemindersEnabled anahtarı ve token lifecycle'ı koru; yeni izin prompt'u veya zorunlu form ekleme.
- [ ] MR07-02: Notification service ve route çözümleyicide tarih/öğün bağlamını destekle; hedefli mesaj doğru öğün ekleme akışına, genel mesaj günlük görünümüne açılsın.
- [ ] MR07-03: Ön planda, arka planda, kapalı uygulamada ve login sonrası navigation'ı test et; eski istemci için güvenli günlük ekranı fallback'i sağla.
- [ ] MR07-04: Push body'deki kcal'yi güncel gerçek sayma; ekranda backend'den güncel günlük özeti yükle. Başka hesaba ait bildirimi yanlış hesapta açma.
- [ ] MR07-05: Etkileşim kaydını notification/occurrence ile idempotent bağla; aynı tıklama veya aynı food log birden fazla dönüşüm sayılmasın.
- [ ] MR07-06: Ölçümler: provider kabulü, receipt sonucu, tekil açılış, açılıştan sonra 2 saat içinde ilgili güne öğün kaydı ve hatırlatma kapatma oranı. Zaman penceresindeki ilişki nedensellik iddiası değildir.
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
