# Onboarding kilo hedefi ve kas odağı: analiz ve uygulama planı

Tarih: 2026-10-04
Son güncelleme: 2026-10-05
Durum: Çekirdek backend ve mobil uygulama tamamlandı; commit, staging deploy ve cihaz doğrulaması bekliyor.
Kapsam: Backend ve mobil hedef oluşturma, düzenleme, hesaplama ve hedefin tüketildiği ekranlar.
Bu belge uygulama veya AWS deploy yapıldığı anlamına gelmez.

## Uygulama durumu - 2026-10-05

Tamamlananlar:

- `BodyCompositionFocus` veri modeli, Flyway migration ve onboarding/aktif hedef kalıcılığı.
- Kilo yönü ile kas odağının ayrı request/response alanları olarak taşınması.
- Kilo koruma + kas odağında sıfır kalori ayarı ve sıfır haftalık değişim.
- Kas odaklı kilo kaybı ve artışında kontrollü haftalık hız sınırları ve kas odaklı makro profili.
- Mobil onboarding, haftalık hız, plan özeti ve hedef düzenleme ekranlarının yeni modele geçirilmesi.
- Eski `GoalType.BUILD_MUSCLE` kayıtları için geriye uyumlu okuma ve UI dönüştürme.
- Dashboard, AI coaching ve AI nutrition plan context'lerine kas odağının eklenmesi.
- Mobil ve backend hedef yönü toleranslarının ortak `0,5 kg` kuralına getirilmesi.
- Hedefli backend testleri, TypeScript, onboarding ve i18n sözleşme kontrolleri.

Bekleyenler:

- Staging migration/deploy ve eski mobil sürümle geriye uyumluluk testi.
- Yeni mobil build üzerinde iOS/Android cihaz doğrulaması.
- Staging gözlem metrikleri ve gerçek kullanıcı akışı kontrolü.

## 1. Kullanıcı ihtiyacı ve kabul edilen karar

Kullanıcı daha düşük hedef kilo seçtiğinde kas gelişimine de odaklanabilmelidir. Örneğin 90 kg'dan 80 kg'a inmek isteyen kullanıcı, kas gelişimini destekleyen bir plan seçebilmelidir.

Kilo yönü ve kas odağı ayrı kararlar olacak:

| Alan | Kullanıcı seçenekleri | Hesaplamadaki rolü |
| --- | --- | --- |
| Kilo hedefi | Kilo ver / Kiloyu koru / Kilo al | Enerji açığı, bakım veya fazlası |
| Kas odağı | Standart / Kas gelişimini destekle | Uygun hız, protein ve antrenman odağı |

Kas korunması ayrı bir kullanıcı seçeneği olmayacak. Standart kilo verme planları da kas kaybını azaltmayı gözetmeli. Kas odağı daha kontrollü kilo kaybı ve direnç antrenmanı desteği getirir; kas kazanımını garanti etmez.

Temel ürün kuralı: **Kas odağı kilo yönünü değiştirmez.** Kilo yönünü mevcut kilo ile hedef kilo arasındaki ilişki belirler. Kas odağı seçildi diye sisteme otomatik kilo artışı veya kalori fazlası eklenmez.

- Hedef kilo mevcut kilodan düşükse sonuç kilo kaybıdır.
- Hedef kilo mevcut kiloyla aynı tolerans aralığındaysa sonuç kilo korumadır ve haftalık değişim `0 kg` olur.
- Hedef kilo mevcut kilodan yüksekse sonuç kilo artışıdır.
- Kas odağı bu üç sonucun her birinde ayrıca seçilebilir.

Backend sözleşmesi önerisi:

```json
{
  "goalType": "LOSE_WEIGHT",
  "bodyCompositionFocus": "BUILD_MUSCLE"
}
```

`BodyCompositionFocus` değerleri: `STANDARD`, `BUILD_MUSCLE`.
Yeni mobil akışta ana hedef değerleri: `LOSE_WEIGHT`, `MAINTAIN_WEIGHT`, `GAIN_WEIGHT`.
Eski `GoalType.BUILD_MUSCLE`, eski mobil sürümler ve tarihsel kayıtlar için geçiş boyunca desteklenecek.

## 2. Koddan doğrulanan mevcut davranış

### Mobil

- `src/screens/setup/SetupGoal.tsx`: Kilo kaybı, koruma, artış ve kas geliştirme tek seçenek listesinde. `BUILD_MUSCLE`, hedef kilo mevcut kilodan 1 kg'dan fazla düşükse kapanıyor.
- Aynı ekranda kilo kaybı/artışı haftalık hız ekranına giderken kas geliştirme doğrudan aktivite ekranına gidiyor.
- `src/store/setupStore.ts`: Tek `goalType` tutuluyor; `setWeights` kilo farkından `derivedGoal` çıkarıyor. Onboarding GOAL payload'ında bağımsız kas odağı yok.
- `src/utils/onboardingWeeklyPace.ts`: Kas geliştirme pozitif haftalık hızla, kilo kaybı ayrı hız sınırıyla ele alınıyor.
- `src/screens/setup/SetupGoalReview.tsx`: Kas geliştirme açıklamaları ve özetleri yalnız `goalType === BUILD_MUSCLE` ile açılıyor.
- Hedef düzenleme ekranı `src/screens/EditGoals.tsx`; paylaşılan doğrulama ve servis alanları da yeni sözleşmeye uyarlanmalı.

Mobil kökü: `C:/Users/emrah/IdeaProjects/Claude_Grun_frontend`.

### Backend

- `GoalType.java`: Kas geliştirme varsayılan kalori ayarı +500; kilo verme -500. Bu değerler doğrudan nihai kaloriye eklenmiyor, varsayılan haftalık hızın hesaplanmasında kullanılıyor.
- `UserGoalServiceImpl.calculateGoal`: Bakım kalorisi + haftalık hızdan türetilen kalori ayarı; ardından mevcut minimum kalori sınırı uygulanıyor.
- `resolveSafeWeeklyRate`: İşaret yalnız `LOSE_WEIGHT` için negatif; `BUILD_MUSCLE` için pozitif.
- Kilo kaybı üst sınırı `min(1 kg, mevcut kilo * 0.01)`; kas geliştirme üst sınırı `min(0.25 kg, mevcut kilo * 0.003)`.
- `validateGoalDirection`: Kas geliştirmede hedef kilo 1 kg'dan fazla düşükse `GOAL_MUSCLE_DIRECTION` hatası veriliyor.
- Makrolar kalori yüzdelerinden hesaplanıyor. Kilo vermede protein %25, kas geliştirmede %30. Kalori azalınca protein gramı da azalabiliyor.
- `AdvancedMacroTargetPolicy.directionValid`: `BUILD_MUSCLE` için bakım kalorisinden yüksek kalori şartı var.
- `ProgressAnalyticsServiceImpl.goalTrendStatus`: Kas geliştirmede pozitif kilo değişimi ON_TRACK koşulu olarak kullanılıyor.
- `UserGoalEntity`, onboarding draft ve ilgili DTO'larda bağımsız kas odağı alanı yok.

Sonuç: UI kilidini açmak tek başına yeterli değil. Mevcut hesaplama kilo vermek isteyen kullanıcıya kas geliştirme seçildiğinde pozitif kilo değişimi üretir veya hedefi reddeder.

### Doğrulanan mevcut hata: aynı kiloda kas geliştirme

Mevcut sistemde 80 kg olan ve hedef kilosunu 80 kg seçen kullanıcı `BUILD_MUSCLE` seçebilir. Ancak backend bu seçimi kilo koruma olarak değerlendirmez:

- Kas geliştirme için pozitif haftalık hız üretir: `min(0.25 kg, mevcut kilo * 0.003)`.
- Bu hızı `7700 kcal/kg` üzerinden günlük kalori fazlasına çevirir.
- 80 kg örneğinde haftalık yaklaşık `+0.24 kg`, günlük yaklaşık `+264 kcal` çıkar.
- Mobil özet haftalık artışı göstermediği için kullanıcı bu kilo artışı varsayımını açıkça görmez.
- İlerleme analitiği de kas hedefinin başarılı sayılması için pozitif tartı değişimi bekler.

Bu davranış ürün beklentisine aykırıdır. Kullanıcı hedef kilosunu aynı tuttuğunda kas odağı seçse bile sistem haftalık kilo artışı oluşturmamalıdır. `BUILD_MUSCLE` değerinin bugün taşıdığı örtülü "kilo alarak kas geliştir" anlamı kaldırılmalı; kilo yönü ve kas odağı ayrılmalıdır.

## 3. Yeni onboarding ekran davranışı

Mevcut kilo ve hedef kilo adımları korunur. Hedef ekranı iki bölümden oluşur.

### Bölüm 1: Kilo yönü

Ekranda üç seçenek bulunur:

- Kilo ver
- Kiloyu koru
- Kilo al

Hedef kilo girildiğinde uyumlu seçenek otomatik seçilir. Kullanıcı başka bir kilo yönüne dokunursa seçenek sessizce uygulanmaz; hedef kilosunu buna uygun değiştirmesi istenir veya hedef kilo alanına geri yönlendirilir.

Örnekler:

| Mevcut kilo | Hedef kilo | Otomatik ana hedef |
| ---: | ---: | --- |
| 90 kg | 80 kg | Kilo ver |
| 80 kg | 80 kg | Kiloyu koru |
| 70 kg | 75 kg | Kilo al |

### Bölüm 2: Kas odağı

Ana hedefin altında bağımsız bir seçim gösterilir:

```text
Kas gelişimini destekle
Daha uygun protein hedefi, kontrollü kilo değişimi ve direnç antrenmanı odağı.
[ Açık / Kapalı ]
```

- Kapalı: `bodyCompositionFocus = STANDARD`
- Açık: `bodyCompositionFocus = BUILD_MUSCLE`
- Bu seçim üç kilo yönünde de kullanılabilir.
- Hedef kilo değiştiğinde kas odağı korunur; yalnız ana kilo yönü ve haftalık hız yeniden hesaplanır.
- Kullanıcıya kas kazanımının garanti edildiği söylenmez.

Mevcut ayrı `Kas geliştir` ana hedef kartı yeni onboarding'den kaldırılır. Eski kayıtlar ve eski mobil sürümler için backend geçiş desteği devam eder.

### Sonraki ekranlar

- Kilo ver veya kilo al: haftalık hız ekranı gösterilir.
- Kiloyu koru: haftalık hız ekranı atlanır ve hız `0 kg/hafta` olarak kaydedilir.
- Kas odağı açık kilo kaybında hız seçenekleri kas odağına uygun daha kontrollü üst sınırla filtrelenir.
- Plan özeti ana hedefi ve kas odağını ayrı gösterir. Örnek: `Kilo ver` ve `Kas gelişimi odağı`.
- 80 -> 80 kg + kas odağı özetinde `Haftalık değişim: 0 kg` ve bakım kalorisi gösterilir; kilo artış süresi gösterilmez.

## 4. Hedef kombinasyonları ve hesaplama yönü

| Ana hedef | Kas odağı | Beklenen davranış |
| --- | --- | --- |
| Kilo ver | Standart | Kilo kaybı, kas korunmasını gözeten mevcut plan |
| Kilo ver | Kas gelişimi | Kontrollü açık, daha yavaş hız, uygun protein |
| Kiloyu koru | Standart | Bakım kalorisi |
| Kiloyu koru | Kas gelişimi | Bakım çevresinde hedef, kas odağı; otomatik kilo artışı zorunluluğu yok |
| Kilo al | Standart | Kontrollü kilo artışı |
| Kilo al | Kas gelişimi | Kontrollü fazlalık ve kas odaklı protein/hız |

Kas odağı hedef kilo nedeniyle devre dışı bırakılmayacak. Ana kilo yönü ve hedef kilo tutarlılığı ayrıca kontrol edilecek. Birbiriyle çelişen giriş sessizce başka hedefe çevrilmek yerine anlaşılır doğrulama veya açık kullanıcı onayıyla düzeltilecek.

Hesaplama sırası her kombinasyonda aynıdır:

1. Yaş, cinsiyet, boy, kilo ve aktivite düzeyinden bakım kalorisi hesaplanır.
2. Mevcut kilo ile hedef kilo ana kilo yönünü belirler.
3. Seçilen haftalık hız güvenli üst sınırdan geçirilir. Kilo korumada hız zorunlu olarak `0` olur.
4. Ana kilo yönü günlük enerji ayarını belirler: kayıpta negatif, korumada sıfır, artışta pozitif.
5. Kas odağı enerji ayarının işaretini değiştiremez. Yalnız izin verilen hız sınırını, protein politikasını ve ilgili önerileri değiştirir.
6. Minimum kalori ve makro bütçesi korumaları uygulanır.
7. Uygulanan kaloriden efektif haftalık değişim yeniden hesaplanıp response'a yazılır.

| Kombinasyon | Haftalık yön | Kalori yönü | Kas odağının etkisi |
| --- | ---: | --- | --- |
| Kilo ver + Standart | Negatif | Bakımın altında | Standart kayıp hızı ve makrolar |
| Kilo ver + Kas | Negatif | Bakımın altında | Daha kontrollü hız, kas odaklı protein ve antrenman önerileri |
| Koru + Standart | 0 | Bakım | Standart bakım makroları |
| Koru + Kas | 0 | Bakım | Kalori fazlası yok; kas odaklı protein ve antrenman önerileri |
| Kilo al + Standart | Pozitif | Bakımın üstünde | Standart kontrollü artış |
| Kilo al + Kas | Pozitif | Bakımın üstünde | Kas odağına uygun kontrollü artış ve protein |

## 5. Hesaplama politikasının kesinleştirilmesi

Aşağıdaki sayısal aralıklar başlangıç önerisidir; henüz doğrulanmış ve uygulanmış ürün kuralı değildir:

- Kas odaklı kilo kaybında yaklaşık %10-15 bakım kalorisi açığı.
- Alternatif günlük 250-400 kcal aralığı; yüzdeyle birlikte iki ayrı açık olarak uygulanmayacak.
- Haftalık kayıp üst sınırı için yaklaşık vücut ağırlığının %0.5'i.
- Protein için yaklaşık 1.8-2.2 g/kg başlangıç aralığı.

Uygulama öncesinde tek, deterministik politika tanımlanmalı:

1. Bakım kalorisi mevcut enerji tahmin servisinden alınır; aktivite kalorisi ikinci kez eklenmez.
2. Kullanıcının haftalık hız isteği kas odağına uygun sınırlarla değerlendirilir.
3. Kalori hedefi bu hızdan türetilir; ek enerji açığı sınırı varsa aynı hesapta uygulanır. İki yöntem çelişirse daha kontrollü sonuç seçilir.
4. Mevcut minimum kalori korumaları korunur. Son uygulanan kaloriden efektif haftalık hız yeniden hesaplanır.
5. Protein gram bazında belirlenir. Çok yüksek vücut ağırlığında mevcut kilonun doğrudan kullanılması aşırı hedef üretebileceği için referans kilo/yağsız kütle politikası ayrıca kararlaştırılır.
6. Yağ minimumu ve protein hedefi aynı kalori bütçesine sığmalıdır. Kalan enerji karbonhidrata ayrılır; negatif makro veya sessiz protein kırpması olmamalı.
7. Hesaplama sonucu istenen hız, uygulanan hız ve kullanıcıya uygun yerelleştirilmiş açıklamayı döndürür.
8. Hedefe varış süresi yalnız tartı hedefinin yaklaşık süresidir; kas kazanım süresi olarak sunulmaz. Kilo korumada varış tarihi uydurulmaz.
9. Kilo koruma + kas odağında `effectiveWeeklyRateKg = 0`, `calorieAdjustment = 0` ve `goalCalories = maintenanceCalories` temel sözleşmedir. Minimum kalori sınırı bakım kalorisi için ek fazlalık üretmez.

Antrenman sıklığı tek başına direnç antrenmanı kanıtı değildir. Gerekirse mevcut fitness adımına antrenman türü ve deneyim eklenir. Veri bilinmiyorsa temkinli hesaplama kullanılır; kas odağı seçimi engellenmez. Önizleme ve onboarding tamamlama aynı girdilerle aynı sonucu üretmeli.

## 6. Veri modeli ve geriye uyumluluk

- Aktif hedef, onboarding draft, hedef geçmişi/response ve ilgili request'lerde `bodyCompositionFocus` taşınacak.
- Eski istekte alan yoksa `STANDARD`; geçersiz enum değeri anlaşılır 400 hatası.
- Eski `BUILD_MUSCLE` hedefleri deploy sırasında otomatik olarak yeni kalori/makrolara çevrilmeyecek.
- Eski hedefler okunabilir kalacak. Yeni UI bu hedefi kas odağı seçili olarak gösterecek; ana kilo yönünü kayıtlı hedef ve başlangıç kilosundan yorumlayacak. Kullanıcı hedefi yeniden kaydettiğinde yeni kurallarla açık bir yeniden hesaplama yapılacak.
- Eski `BUILD_MUSCLE` payload'ının mevcut hesaplama davranışı geçişte korunacak; yeni UI bağımsız alanları gönderecek. Legacy ve yeni sözleşme birlikte çelişkili gönderilirse açık doğrulama uygulanacak.
- Yeni alanların eklenmesi mevcut kullanıcıların aktif kalori ve makro hedeflerini kendiliğinden değiştirmeyecek.
- Yeniden hesaplama kullanıcı hedefi kaydettiğinde yapılacak; hedef dönemi/geçmişi mevcut kurallarla tutulacak.
- Hesaplama profili tek yerde türetilecek. UI aynı formülleri bağımsız yeniden yazmayacak; yalnız backend sınırlarını kullanacak veya kontrollü önizleme sunacak.

## 7. Sıralı yapılacaklar

### A. Hesaplama kararları ve sözleşme

- [ ] Kilo yönü toleranslarını tek kuralla eşitle: mobil türetme, seçim ve backend doğrulama bugün farklı eşikler kullanıyor.
- [ ] Kas odağına göre kalori açığı/fazlası, haftalık hız ve protein referansını kesinleştir.
- [ ] Makro bütçesi sığmazsa uygulanacak davranışı ve hata/uyarı kodlarını tanımla.
- [ ] Yeni request/response alanlarını, legacy davranışını ve TR/EN ürün metinlerini sabitle.
- [ ] Kilo koruma + kas odağında tartı değişimini zorunlu başarı ölçütü yapma.
- [ ] Kilo koruma + kas odağında kalori ayarını ve haftalık değişimi kesin olarak sıfırla; kas odağından kalori fazlası türetme.

### B. Backend

- [ ] Enum ve additive Flyway migration: aktif hedef ve onboarding draft odağı.
- [ ] `GoalCalculationRequestDto`, `AdvancedGoalRequestDto`, `UserGoalDto`, onboarding goal/complete/state/preview DTO'larına alanı ekle.
- [ ] Onboarding adım kaydetme, devam etme ve tamamlama boyunca odağı koru.
- [ ] `UserGoalServiceImpl` hesaplamasını ana hedef + kas odağıyla çalıştır.
- [ ] `AdvancedMacroTargetPolicy` automatic, controlled ve manual davranışlarını yeni profile uyumlu yap.
- [ ] Manuel makroları sessizce değiştirme; kas odağına uygunluk için mevcut uyarı/onay mekanizmasını kullan.
- [ ] Hedef kaydetme, güncelleme ve yeniden hesaplamada odağı kalıcılaştır.
- [ ] Daily summary, profil ve progress response'larında gerekli odağı taşı; ilgili cache invalidation noktalarını kontrol et.
- [ ] İlerleme durumunda kilo kaybı ve kas odağını birlikte yorumla. Yalnız kilo verisinden kas kazanıldığı sonucunu üretme.

### C. Mobil

- [ ] `SetupGoal`: üç ana kilo hedefi ve ayrı kas odağı seçimi.
- [ ] Ayrı `Kas geliştir` ana hedef kartını yeni akıştan kaldır; kas odağını bağımsız kontrol olarak göster.
- [ ] `setupStore`, persisted state, hydration ve onboarding servis sözleşmelerine yeni alan.
- [ ] Haftalık hız ekranı, önceki/sonraki yönlendirmeler ve plan özetini yeni kombinasyonlara uyumlu yap.
- [ ] `EditGoals`, `goalValidation`, `goalService` ve paylaşılan hedef bileşenlerine aynı davranış.
- [ ] TR/EN etiketler: "Kas gelişimini destekle"; kas kazanımını garanti eden metin kullanma.
- [ ] Profil ve ilerleme ekranlarında birleşik hedefi göster; eski kas hedeflerini doğru aç.
- [ ] Hedef kilo değişince kas odağını kaybetme; uyumsuz eski haftalık hızı backend önizlemesiyle yeniden değerlendir.

### D. Hedefi kullanan diğer alanlar

- [ ] Coaching, beslenme planı ve antrenman planı context'lerine aktif ana hedef ve kas odağını ilet.
- [ ] Bu context'lerin gerçek oluşturulduğu sınıfları uygulama sırasında belirle; tek enum'a bakan dalları envanterle.
- [ ] Yalnız hedef yorumlama ve context değişikliklerini yap; AI transport, kredi, retry ve diğer AI özelliklerine kapsam dışı değişiklik yapma.
- [ ] Next meal, hedef etiketleri ve admin/export gibi hedef tüketicilerinde tek enum varsayımını kontrol et.

### E. Test ve dağıtım

- [ ] Aşağıdaki kabul senaryolarını backend ve mobil testlerle doğrula.
- [ ] Migration'ı mevcut veri üzerinde dene; aktif kullanıcı hedeflerinin değişmediğini doğrula.
- [ ] Backend additive sözleşmesini önce staging'e gönder; eski mobil sürümü test et.
- [ ] Yeni mobil build ile onboarding ve hedef düzenleme uçtan uca testlerini çalıştır.
- [ ] Yeni kayıt ve mevcut kullanıcı için hedef geçmişi/cache tutarlılığını kontrol et.
- [ ] Yeni odağı kullanan hedef sayısı, doğrulama hataları ve uygulanan hız düzeltmelerini staging'de izle.

## 8. Kabul senaryoları

| Senaryo | Kabul koşulu |
| --- | --- |
| 90 -> 80 kg + kas odağı | Seçilebilir, kaydedilebilir; nihai kalori uygun koşullarda bakımın altında |
| Aynı kişi: standart ve kas odaklı kayıp | Politika farkı kontrollü açık/hız ve protein sonucuna yansır |
| 80 -> 80 kg + kas odağı | Seçilebilir; haftalık değişim `0`, kalori ayarı `0`, hedef kalori bakım kalorisine eşit |
| 70 -> 75 kg + kas odağı | Kontrollü kilo artışı; kayıp profili uygulanmaz |
| Çok agresif kayıp isteği | Düzeltilmiş efektif hız ve anlaşılır açıklama; UI ile backend aynı sonuç |
| Kalori minimumu devreye girer | Efektif hız ve tahmini süre uygulanan kaloriye göre hesaplanır |
| Protein/yağ bütçeye sığmaz | Negatif karbonhidrat veya geçersiz kaydedilmiş hedef yok |
| Fitness bilgisi eksik | Kas odağı korunur; bilinmeyen veri biliniyormuş gibi kullanılmaz |
| Manuel hedef | Kullanıcı makroları sessizce değiştirilmez; mevcut onay kuralları çalışır |
| Onboarding yarıda kapatılır | Tekrar açıldığında kilo hedefi, kas odağı ve hız korunur |
| Eski mobil / legacy kas hedefi | Okuma ve kaydetme desteği sürer; otomatik hedef değişimi olmaz |
| Dil TR/EN değişir | Seçenekler, sonuç ve açıklamalar doğru dilde; enum/field adı kullanıcıya sızmaz |
| Tartı sabit, kas odağı aktif | Otomatik kas artışı iddiası yapılmaz; eksik ölçümler açıkça ele alınır |

## 9. Bilimsel dayanak ve sınır

Direnç antrenmanı enerji kısıtlaması sırasında yağsız kütlenin korunmasını destekleyebilir. Bu, her kullanıcıda kas kazanımı garantisi veya bu belgede önerilen tüm sayısal eşiklerin doğrulandığı anlamına gelmez.

- [Kalori kısıtlaması ve direnç antrenmanı çalışması](https://pubmed.ncbi.nlm.nih.gov/3337037/)
- [Protein destekli kilo kaybında direnç antrenmanı çalışması](https://pubmed.ncbi.nlm.nih.gov/29352654/)

Sayısal politika için farklı vücut ölçüleri, aktivite düzeyleri ve kalori bütçeleri üzerinde örnek hesaplama matrisi hazırlanmalı. Geçerli girdilerde imkânsız makro bütçeleri üretmediği doğrulanmadan yeni varsayılanlar devreye alınmamalı.

## 10. Tamamlanma ölçütü

Kullanıcı düşük hedef kiloyla kas odağını seçebiliyor, tüm kaydetme/yeniden açma akışlarında seçimi korunuyor, backend bu kombinasyona uygun kalori/makroları hesaplıyor ve hedefi tüketen ekranlar aynı anlamı gösteriyorsa geliştirme tamamlanmış sayılır. Kod, staging backend dağıtımı ve mobil build doğrulaması ayrı ayrı işaretlenmelidir.
