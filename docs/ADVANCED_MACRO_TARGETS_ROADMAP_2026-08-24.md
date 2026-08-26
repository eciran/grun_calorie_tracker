# GRUN Gelişmiş Makro Hedefleri Yol Haritası

**Tarih:** 24 Ağustos 2026  
**Durum:** Nihai ürün kararı — uygulama öncesi uzman eşik doğrulaması gerekli  
**Kapsam:** Otomatik, kontrollü ve tamamen manuel kalori/makro hedef yönetimi

## 1. Amaç

GRUN, onboarding sırasında kullanıcının profil, aktivite ve kilo hedeflerinden günlük kalori, protein, karbonhidrat ve yağ hedeflerini otomatik hesaplamaya devam edecektir.

Kullanıcı daha fazla kontrol isterse **Hedefler ve Plan** bölümünden gelişmiş hedef yönetimine geçebilecektir. Gelişmiş hedef yönetimi üç moddan oluşacaktır:

1. GRUN Otomatik
2. Kontrollü Özelleştirme
3. Tamamen Manuel

Otomatik mod bütün kullanıcılara açık olacaktır. FREE kullanıcı otomatik hesaplanan kalori, protein, karbonhidrat ve yağ hedeflerini görebilecektir. Ücretsiz plandaki besin detayı kısıtı mikro besinlerde ve ilgili `MICRONUTRIENT_DETAILS` entitlement'ında kalacaktır.

Kontrollü ve tamamen manuel modlar mevcut `ADVANCED_MACRO_TARGETS` entitlement'ı üzerinden yalnızca Plus ve Pro planlarına sunulacaktır. Bu entitlement otomatik makroların görüntülenmesini değil, gelişmiş hedef oluşturma ve düzenleme işlemlerini kontrol edecektir.

## 1.1. Onaylanan nihai kararlar

- FREE, PLUS ve PRO kullanıcıları GRUN Otomatik hedeflerini ve otomatik hesaplanan kalori/protein/karbonhidrat/yağ değerlerini görebilir.
- Mikro besin detaylarının görünürlüğü mevcut `MICRONUTRIENT_DETAILS` plan kurallarına tabi olmaya devam eder.
- `ADVANCED_MACRO_TARGETS`, kontrollü ve tamamen manuel hedef oluşturma/düzenleme hakkıdır.
- Kontrollü modda kullanıcı en fazla iki kısıt belirler; en az bir değer GRUN tarafından hesaplanır.
- Tamamen manuel modda kullanıcı üç makroyu belirler; günlük kalori bağımsız girilmez, makrolardan türetilir.
- Kontrollü ve tamamen manuel modlar MVP'de yalnızca 18 yaş ve üzeri kullanıcılara açıktır.
- Manuel hedef hedef tipiyle enerji yönü bakımından çelişiyorsa kayıt engellenir.
- Abonelik sona erdiğinde kayıtlı gelişmiş hedef aktif kalır; gelişmiş hedefi yeniden oluşturma/düzenleme kilitlenir, otomatik moda dönüş ücretsizdir.
- Hedef geçmişi bütün planlarda veri doğruluğu için tutulur; yalnızca gelişmiş geçmiş analizi ücretli olabilir.
- Hedef değişikliği kaydedildiği anda ve kullanıcının yerel gününde yürürlüğe girer; geçmiş günler değiştirilmez.
- Güvenlik eşikleri merkezi policy katmanında tutulur ve yayından önce beslenme uzmanı/hukuk incelemesinden geçer.

## 2. Temel ürün ilkeleri

- Onboarding her zaman GRUN Otomatik moduyla tamamlanır.
- Kullanıcıdan ilk kurulum sırasında makro bilgisi bilmesi beklenmez.
- Bütün kullanıcılar otomatik hesaplanan kalori ve üç makro hedefini görebilir.
- Kontrollü ve manuel ayarlar daha sonra **Profil > Hedefler ve Plan > Beslenme Hedefleri** içinden açılır.
- Kontrollü ve manuel ayarlar yalnızca geçerli `ADVANCED_MACRO_TARGETS` entitlement'ı ve 18+ yaş şartıyla oluşturulabilir veya düzenlenebilir.
- Kullanıcının hedefi abonelik durumu değiştiğinde sessizce değiştirilmez.
- Bütün hesaplama ve güvenlik kontrolleri backend'de uygulanır; mobil doğrulamalar yalnızca kullanıcı deneyimi katmanıdır.
- Manuel hedefler AI, dashboard, progress ve öğün önerilerinde otomatik hedefmiş gibi sunulmaz; hedef kaynağı korunur.
- Kullanıcı her zaman GRUN'un güncel otomatik önerisini görebilir ve otomatik moda dönebilir.
- Hedef değişiklikleri versiyonlanır; geçmiş analizler sonradan girilen yeni hedeflerle yeniden yorumlanmaz.
- “Güvenli” gibi klinik kesinlik çağrıştıran ifadeler, uzman tarafından doğrulanmış bir iddia olmadıkça kullanıcı metinlerinde kullanılmaz.

## 3. Modlar

### 3.1. Mod 1 — GRUN Otomatik

Varsayılan moddur ve bütün planlarda kullanılabilir.

Kullanıcı şunları belirler:

- Hedef tipi
- Hedef kilo
- Haftalık kilo değişim hızı
- Aktivite seviyesi

GRUN şunları hesaplar:

- Günlük kalori
- Protein
- Karbonhidrat
- Yağ
- Tahmini hedef süresi
- GRUN hesaplama sınırları içinde uygulanabilir haftalık değişim hızı

Önerilen açıklama:

> Bu hedefler vücut bilgileriniz, aktivite seviyeniz ve seçtiğiniz ilerleme hızına göre GRUN tarafından hesaplandı.

Davranış kuralları:

- Profil veya kilo değişikliklerinde yeniden hesaplama önerilir.
- Yeniden hesaplama kullanıcı onayıyla uygulanır.
- Kullanıcı istediği zaman **GRUN önerisine dön** eylemini kullanabilir.

### 3.2. Mod 2 — Kontrollü Özelleştirme

Plus ve Pro kullanıcıları içindir. Kullanıcıya kontrol verirken planın matematiksel bütünlüğünü GRUN korur.

#### A. Kaloriyi sabit tut

- GRUN'un hesapladığı günlük kalori değişmez.
- Kullanıcı protein, karbonhidrat veya yağ için en fazla iki hedefi değiştirir/kilitler.
- Toplam makro enerjisi kalori hedefine eşit kalır.
- Bir değer değiştirildiğinde diğer makrolar otomatik dengelenir.
- Kullanıcı bir veya en fazla iki makroyu kilitleyebilir.
- Üçüncü makro kalan enerji bütçesinden hesaplanır.
- Üç makronun tamamının kullanıcı tarafından girilmesine bu modda izin verilmez; bu işlem Tamamen Manuel moda aittir.

Örnek:

- Günlük hedef: 2.000 kcal
- Protein: 120 gramdan 150 grama çıkarılır.
- GRUN, kalan kaloriyi karbonhidrat ve yağ arasında yeniden dağıtır.

#### B. Öncelikli makroyu sabit tut

- Kullanıcı yalnızca bir öncelikli makroyu (örneğin protein) sabitler.
- Günlük kalori hedefi GRUN Otomatik hesaplamasından gelir ve kullanıcı tarafından değiştirilmez.
- GRUN kalan iki makroyu hedef tipi ve hesaplama policy'sine göre dağıtır.
- Yeni hedef mevcut otomatik hedefle karşılaştırılır.
- Seçilen değer mevcut kalori bütçesinde geçerli bir dağılım üretmiyorsa kayıt engellenir ve kullanıcıya uygulanabilir aralık gösterilir.

Kontrollü modun ayırt edici kuralı, kullanıcının planın bütün değerlerini belirlememesidir. En az bir makro ve günlük kalori GRUN tarafından hesaplanır.

### 3.3. Mod 3 — Tamamen Manuel

Plus ve Pro kullanıcıları için uzman modudur.

Kullanıcı doğrudan şunları belirler:

- Günlük protein
- Günlük karbonhidrat
- Günlük yağ

Günlük kalori bağımsız bir giriş alanı değildir; üç makrodan hesaplanan salt-okunur sonuçtur.

Makro enerjisi şu formülle hesaplanır:

```text
Protein × 4 + Karbonhidrat × 4 + Yağ × 9 = Günlük makro kalorisi
```

Kalori makrolardan türetilir ve tek doğruluk kaynağı olarak kullanılır. Kullanıcı ayrıca bağımsız bir kalori hedefi giremez. Böylece kalori ve makro hedeflerinin birbiriyle çeliştiği iki ayrı kaynak oluşmaz.

Bu 4/4/9 formülü hedef oluşturmak içindir. Yiyecek etiketlerindeki enerji; lif, şeker alkolleri, alkol ve etiket yuvarlamaları nedeniyle tüketilen makrolardan hesaplanan enerjiyle birebir eşleşmeyebilir. Günlük tüketim kalorileri bu formülle yeniden yazılmaz; mevcut food-log kalori snapshot'ları kullanılmaya devam eder.

Tamamen manuel modda haftalık değişim hızı doğrudan kullanıcı girdisi değildir. GRUN, güncel bakım enerjisi ile makrolardan türetilen kalori arasındaki farktan tahmini hızı hesaplar. Güvenilir tahmin üretilemiyorsa tahmini süre gösterilmez.

Manuel hedefin enerji yönü seçilen hedef tipiyle çelişiyorsa kayıt engellenir. Örneğin kilo verme hedefinde bakım enerjisinin üzerinde bir manuel hedef kaydedilemez; kullanıcı hedef tipini veya makrolarını değiştirmelidir.

Tamamen manuel mod teknik doğrulamaları devre dışı bırakmaz. Sistem:

- Negatif, boş veya sayısal olmayan girişleri engeller.
- Kalori ve makro uyuşmazlığını hesaplar.
- Aşırı veya olağan dışı değerleri işaretler.
- Gerekli durumlarda ikinci onay ister.
- Kullanıcının manuel hedef uyarısını kabul ettiği zamanı kaydeder.
- Özel sağlık durumları için profesyonel destek uyarısı gösterir.
- Kullanıcının 18 yaş ve üzerinde olduğunu backend profil verisinden doğrular.

## 4. Güvenlik ve koruma katmanı

Uyarılar üç seviyede ele alınacaktır.

### 4.1. Bilgilendirme

Kaydetmeyi engellemez:

- GRUN önerisinden küçük sapma
- Dengeli aralığın dışında fakat makul dağılım
- Kalori ve makro arasında küçük yuvarlama farkı
- Önceki hedefe göre belirgin fakat GRUN çalışma sınırları içindeki değişiklik

### 4.2. Güçlü uyarı ve ikinci onay

Kullanıcı uyarıyı gördükten ve açıkça kabul ettikten sonra devam edebilir:

- Protein veya yağın profil için beklenen aralığın belirgin dışında olması
- Kalorinin GRUN önerisinden ciddi ölçüde sapması
- Bir makronun çok düşük belirlenmesi
- Değişikliğin tahmini haftalık kilo hızını ciddi biçimde etkilemesi
- Kısa süre içinde art arda hedef değişikliği yapılması

Önerilen onay metni:

> Girdiğiniz hedefler GRUN'un profilinize göre hesapladığı aralığın dışındadır. Bu değişikliğin beslenme planınızı ve ilerleme tahminlerinizi etkileyebileceğini anlıyorum.

### 4.3. Engelleyici doğrulama

Şu durumlarda kayıt yapılmaz:

- Negatif veya sayısal olmayan değer
- Sıfır toplam enerji
- Teknik olarak imkânsız kombinasyon
- Ürün tarafından tanımlanan mutlak güvenlik sınırlarının aşılması
- Kalori ve makro uyuşmazlığının çözülmemesi
- Hesaplama için gerekli temel profil bilgilerinin bulunmaması
- Kullanıcının 18 yaşından küçük olması
- Manuel hedefin enerji yönünün seçilen hedef tipiyle çelişmesi

### 4.4. Güvenlik kurallarının yapılandırılması

Protein, yağ, karbonhidrat ve kalori eşiklerinin kod içine dağınık biçimde yazılmaması önerilir. Kurallar merkezi bir policy/validator katmanında tutulmalı ve uyarılar sabit kodlarla döndürülmelidir.

Örnek uyarı kodları:

```text
CALORIE_MACRO_MISMATCH
CALORIE_DEVIATION_HIGH
PROTEIN_BELOW_EXPECTED_RANGE
PROTEIN_ABOVE_EXPECTED_RANGE
FAT_BELOW_EXPECTED_RANGE
CARBS_VERY_LOW
FREQUENT_TARGET_CHANGES
PROFILE_REVIEW_RECOMMENDED
```

Nihai eşikler beslenme uzmanı ve hukuk incelemesi sonrasında sabitlenmelidir.

### 4.5. MVP uygunluk sınırı

- Kontrollü ve tamamen manuel hedefler MVP'de yalnızca 18 yaş ve üzeri kullanıcılara açıktır.
- 13–17 yaş kullanıcılar GRUN Otomatik modunda kalır; gelişmiş modlar için paywall yerine uygunluk açıklaması görür.
- GRUN gebelik, emzirme, yeme bozukluğu, böbrek hastalığı veya başka klinik durumlar için özel manuel hedef üretmez.
- Kullanıcı bu durumlardan birini beyan ederse profesyonel destek yönlendirmesi gösterilir. Profilde bu alanlar bulunmuyorsa uygulama klinik durum tespiti yaptığı izlenimi vermez.
- Feragat/onay metni, teknik olarak sakıncalı bir hedefi kabul edilebilir hâle getirmez. Engelleyici policy kuralları kullanıcı onayıyla aşılamaz.

## 5. Kullanıcı bilgilendirmesi ve onay

“Sorumluluk tamamen kullanıcıya aittir” ifadesi tek başına kullanılmamalıdır. Daha açıklayıcı ve kullanıcı dostu bir metin tercih edilmelidir.

Önerilen bilgilendirme:

> Manuel hedefler GRUN'un otomatik önerilerinin yerine geçer. Bu değerler GRUN tarafından kişisel sağlık tavsiyesi olarak önerilmemektedir. Özel bir sağlık durumunuz, gebelik/emzirme durumunuz veya profesyonel bir beslenme planınız varsa değişiklikleri doktorunuz ya da diyetisyeninizle birlikte yapmanız önerilir.

Önerilen kayıt onayı:

> Girdiğim hedeflerin GRUN'un otomatik hesaplamalarından farklı olabileceğini ve uygulamadaki plan, öneri ve analizlerin bu hedeflere göre güncelleneceğini anlıyorum.

Metinler yayından önce hukuk danışmanı tarafından incelenmelidir.

Kabul kaydı yalnızca bir timestamp'ten oluşmamalıdır. Ayrı ve değiştirilemez bir acknowledgement/audit kaydında şu bilgiler tutulmalıdır:

- Hedef versiyonu
- Gösterilen uyarı kodları
- Uyarı/policy metni versiyonu
- Kullanıcının dili/locale bilgisi
- Kabul zamanı
- O anda gösterilen otomatik referans ve kaydedilen gelişmiş hedef özeti

## 6. Mobil kullanıcı akışı

Önerilen konum:

```text
Profil > Hedefler ve Plan > Beslenme Hedefleri
```

Ekranın üstünde mevcut hesaplama yöntemi gösterilir:

```text
Hedef hesaplama yöntemi
● GRUN Otomatik
○ Kontrollü
○ Tamamen Manuel
```

### 6.1. FREE kullanıcı

- Otomatik modu kullanabilir ve otomatik hesaplanan kalori, protein, karbonhidrat ve yağ hedeflerini görebilir.
- Mikro besin detayları mevcut `MICRONUTRIENT_DETAILS` plan kurallarına tabi olmaya devam eder.
- Kontrollü veya tamamen manuel moda dokunduğunda özelliğin kısa açıklamasını görür.
- Plus/Pro yükseltme ekranına yönlendirilir.
- Mevcut hedefi değiştirilmez.

18 yaşından küçük kullanıcılar ücretli plana sahip olsa bile kontrollü/manüel moda alınmaz; paywall yerine yaş uygunluğu ve profesyonel destek açıklaması gösterilir.

### 6.2. PLUS/PRO kullanıcı

1. Modu seçer.
2. Hedefleri düzenler.
3. Canlı kalori ve makro özetini görür.
4. GRUN önerisi ile yeni planı karşılaştırır.
5. Varsa uyarıları görür.
6. Gerekliyse ikinci onay verir.
7. Değişikliği kaydeder.

Önerilen karşılaştırma kartı:

| Hedef | Mevcut | Yeni |
|---|---:|---:|
| Kalori | 2.000 kcal | 2.180 kcal |
| Protein | 130 g | 160 g |
| Karbonhidrat | 240 g | 245 g |
| Yağ | 58 g | 62 g |

Her zaman görünür olması gereken eylemler:

- GRUN önerisine dön
- Değişiklikleri iptal et

## 7. Abonelik davranışı

Mevcut `ADVANCED_MACRO_TARGETS` entitlement'ı kontrollü ve tamamen manuel modları kapsayacaktır.

| Özellik | FREE | PLUS | PRO |
|---|---:|---:|---:|
| GRUN otomatik hedef | Evet | Evet | Evet |
| Hazır makro profilleri | MVP dışı | MVP dışı | MVP dışı |
| Kontrollü özelleştirme | Hayır | Evet | Evet |
| Tamamen manuel | Hayır | Evet | Evet |
| Gelişmiş hedef geçmişi analizi | MVP dışı | MVP dışı | MVP dışı |
| Günlere göre farklı makro | Hayır | Sonraki faz | Sonraki faz |
| Antrenman/dinlenme günü hedefi | Hayır | Hayır | Sonraki faz |

### 7.1. Üyelik sona erdiğinde

- Mevcut manuel veya kontrollü hedef aniden otomatik hedefle değiştirilmez.
- Mevcut hedef aktif kalır ancak ücretli düzenleme seçenekleri kilitlenir.
- Kullanıcı ücretsiz olarak GRUN Otomatik moda dönebilir.
- Yeniden kontrollü veya manuel düzenleme yapmak için geçerli entitlement gerekir.

Bu davranış, ödeme durumu değiştiğinde kullanıcının beslenme hedeflerinin sessizce değiştirilmesini önler.

`ADVANCED_MACRO_TARGETS` kayıtlı hedefi tüketme hakkını değil, kontrollü/manüel hedef oluşturma ve düzenleme hakkını ifade eder. Böylece üyelik sona erdiğinde dashboard, progress ve öneriler son kayıtlı hedefi kullanmaya devam eder.

Hedef geçmişinin backend'de tutulması plan bağımsız bir veri doğruluğu gereksinimidir. Ücretlendirilebilecek alan geçmiş verinin tutulması değil, kullanıcıya sunulan gelişmiş karşılaştırma ve analiz ekranıdır.

## 8. Backend veri modeli

Mevcut tek-satır hedef modeli versiyonlanmış hedef modeline dönüştürülecek ve aşağıdaki alanlar eklenecektir:

```text
calculation_mode
  AUTO
  CONTROLLED
  MANUAL

controlled_strategy
  CALORIES_FIXED
  PRIORITY_MACRO_FIXED
  null

locked_macros
macro_calculated_calories
automatic_reference_calories
automatic_reference_protein
automatic_reference_carbs
automatic_reference_fat
effective_from
effective_until
effective_local_date
effective_time_zone
version
updated_at
```

`locked_macros` ayrı ilişkisel alanlar, enum koleksiyonu veya kontrollü JSON olarak modellenebilir. Seçim mevcut veri standardına göre yapılmalıdır.

Manuel veya kontrollü moda geçildiği anda geçerli otomatik referans değerleri saklanmalıdır. Böylece:

- Sapma ölçülebilir.
- Karşılaştırma doğru gösterilebilir.
- Otomatik hedefe dönüş güvenilir çalışır.
- Analitiklerde hedef kaynağı ayrıştırılabilir.

Uyarı/onay kayıtları yalnızca aktif hedef satırında değiştirilebilir JSON olarak tutulmamalıdır. Ayrı bir `goal_target_acknowledgements` veya eşdeğer audit tablosu hedef versiyonuna bağlanmalıdır.

Gram ve kalori alanlarında kalıcı hassasiyet standardı tanımlanmalıdır. Yeni alanlarda kayan nokta belirsizliğini azaltmak için uygun `NUMERIC` hassasiyeti veya ürünün kabul ettiği tam gram standardı kullanılmalıdır.

## 9. Hedef geçmişi ve versiyonlama

Hedefler yalnızca mevcut satırın üzerine yazılarak yönetilmemelidir.

Her değişiklikte:

1. Önceki hedefin `effective_until` değeri kapatılır.
2. Yeni hedef yeni bir versiyon olarak oluşturulur.
3. Yeni hedefin `effective_from` değeri kaydedilir.
4. Geçmiş analizler ilgili tarihte geçerli hedefi kullanır.

Veritabanı ve servis garantileri:

- Bir kullanıcının aynı anda yalnızca bir aktif hedefi olabilir.
- Aynı kullanıcıya ait hedeflerin etkinlik aralıkları çakışamaz.
- Mevcut hedefler migration sırasında `calculation_mode=AUTO` olarak backfill edilir.
- Mevcut `created_at`, uygun olduğu ölçüde ilk `effective_from` değeri olarak kullanılır.
- Bütün hedef tüketicileri doğrudan `findByUser` sorgusu yerine merkezi `ActiveGoalResolver`/tarihsel hedef resolver kullanır.
- Eşzamanlı cihaz güncellemeleri optimistic locking için `version` alanıyla korunur.
- Tekrarlanan mobil save isteklerinin çift hedef versiyonu üretmemesi için idempotency anahtarı kullanılır.

Yürürlük kararı:

- Yeni hedef kayıt anında aktif olur.
- Dashboard'da kullanıcının o yerel gününün tamamı yeni hedefe göre gösterilir.
- Geçmiş yerel günler değiştirilmez.
- Aynı yerel günde birden fazla değişiklik varsa o gün için son başarıyla kaydedilen hedef analitik hedefi olur.
- UTC zamanıyla birlikte kullanıcının kayıt anındaki time zone ve `effective_local_date` değeri saklanır.

Bu yapılmazsa kullanıcı bugün hedefini değiştirdiğinde geçmiş ayların hedef uyumu da yeni hedefe göre hesaplanabilir ve analitik sonuçlar bozulabilir.

Nihai mimari kararı versiyonlanmış `user_goals`/`goals` yapısıdır. Ayrı bir kopya `goal_history` tablosu oluşturulmaz; aynı hedef tablosundaki etkinlik aralıkları hem aktif hem tarihsel hedeflerin tek doğruluk kaynağı olur. Uyarı kabul kayıtları ise ayrı audit/acknowledgement tablosunda tutulur.

Mevcut hedef silme işleminin semantiği migration sırasında değiştirilmelidir: normal “hedefi kaldır” işlemi aktif versiyonu kapatır; hesap silme/kişisel veri silme işlemi ise retention ve hukuk politikasına uygun olarak bütün hedef ve acknowledgement geçmişini ele alır. Veri dışa aktarma kapsamına hedef geçmişi ve onay kayıtları dahil edilir.

## 10. API önerisi

Mevcut otomatik API'ler korunur:

```http
POST /api/v1/goals/calculate
POST /api/v1/goals/save
GET  /api/v1/goals/me
```

Önerilen yeni endpoint'ler:

```http
POST /api/v1/goals/advanced/preview
POST /api/v1/goals/advanced/save
POST /api/v1/goals/restore-automatic
GET  /api/v1/goals/history
```

Preview işlemi veri kaydetmeden bütün hesaplama, karşılaştırma ve uyarıları döndürmelidir.

Örnek response:

```json
{
  "mode": "CONTROLLED",
  "strategy": "CALORIES_FIXED",
  "calories": 2000,
  "proteinGrams": 150,
  "carbGrams": 213,
  "fatGrams": 61,
  "automaticReference": {
    "calories": 2000,
    "proteinGrams": 130,
    "carbGrams": 240,
    "fatGrams": 58
  },
  "differences": {},
  "warnings": [],
  "requiresAcknowledgement": false,
  "canSave": true,
  "previewToken": "opaque-preview-token",
  "profileVersion": 12,
  "goalVersion": 4
}
```

Advanced preview ve save endpoint'lerinde `ADVANCED_MACRO_TARGETS` entitlement kontrolü backend tarafından zorunlu tutulmalıdır.

API davranış kararları:

- Preview response, alan bazlı eski/yeni farkları ve yapılandırılmış uyarı kodlarını döndürür.
- Save işlemi preview token, hedef versiyonu ve profil versiyonunu doğrular.
- Preview sonrasında profil veya aktif hedef değişmişse eski preview kaydedilmez; istemciden yeniden önizleme istenir.
- Save işlemi bütün hesaplama ve policy kontrollerini backend'de yeniden çalıştırır.
- `restore-automatic`, eski otomatik snapshot'ı tekrar etkinleştirmez; kullanıcının güncel profiliyle yeni bir AUTO hedef versiyonu hesaplar.
- Automatic calculate/save akışındaki tekrarlı hesaplama kaldırılır ve tek merkezi hesaplama/policy sonucu atomik olarak kaydedilir.

## 11. Sistem entegrasyonları

Yeni hedef modu ve aktif hedef aşağıdaki bütün tüketicilerde tutarlı kullanılmalıdır:

- Dashboard kalori ve makro hedefleri
- Günlük kalan hedefler
- Next Meal önerileri
- AI beslenme planı
- Tarif ve öğün önerileri
- Progress analitiği
- Hedef yeniden hesaplama bildirimi
- App startup ve profil özetleri
- Cache invalidation süreçleri

AI bağlamına hedef kaynağı eklenmelidir:

```text
targetMode: MANUAL
userAcknowledgedManualTargets: true
```

AI, manuel hedefi GRUN tarafından önerilmiş veya sağlık açısından doğrulanmış bir hedef gibi sunmamalıdır.

Önerilen ifade:

> Bu öneri, kullanıcı tarafından belirlenen mevcut makro hedeflerine göre oluşturuldu.

Profil bilgileri değiştiğinde:

- Otomatik modda yeniden hesaplama önerilebilir.
- Kontrollü veya manuel modda hedef sessizce değiştirilmez.
- Kullanıcıya yeni otomatik referans gösterilir ve isterse karşılaştırmalı geçiş sunulur.

Egzersiz kalorileri mevcut ürün davranışıyla uyumlu şekilde hedef bütçesine otomatik eklenmez. Gelişmiş hedefler de temel günlük hedef olarak değerlendirilir; ileride ayrı bir “egzersiz kalorisini bütçeye ekle” kararı alınmadıkça kontrollü/manüel hedefler egzersize göre gün içinde değişmez.

## 12. Uygulama fazları

### Faz 0 — Uygulama öncesi zorunlu doğrulamalar

- Bu dokümandaki onaylı üç mod davranışını acceptance criteria'ya dönüştürme
- Kontrollü moddaki bir/iki kilit sınırını test senaryolarına dönüştürme
- Gram/kalori yuvarlama standardını belirleme
- Güvenlik eşiklerini beslenme uzmanıyla doğrulama
- Uyarı kodları ve policy versiyonlama formatını kesinleştirme
- Hukuki metinleri inceleme
- Versiyonlanmış hedef ve acknowledgement şemasının teknik tasarımını onaylama

### Faz 1 — Backend temeli

- Veritabanı migration'ları
- Hedef versiyonlama/geçmiş yapısı
- Mod ve otomatik referans alanları
- Kontrollü dengeleme algoritması
- Merkezi manuel hedef validator/policy katmanı
- Advanced preview ve save servisleri
- Entitlement kontrolleri
- Otomatik hedefe dönüş endpoint'i
- Dashboard ve analitik cache invalidation
- Unit ve integration testleri

### Faz 2 — Mobil temel akış

- `EditGoals` ekranına üç mod seçimi
- FREE kullanıcı için kilitli özellik önizlemesi ve paywall
- Kalori sabit kontrollü mod
- Öncelikli makroyu sabit tutan kontrollü mod
- Tamamen manuel giriş
- Canlı 4/4/9 hesabı
- Otomatik hedefle karşılaştırma kartı
- Bilgilendirme ve güçlü uyarı bileşenleri
- İkinci onay modalı
- Otomatik moda dönüş
- TR/EN metinleri
- Erişilebilirlik ve klavye/ondalık giriş kontrolleri

### Faz 3 — Sistem entegrasyonu

- Dashboard
- Next Meal
- AI Nutrition Plan
- Progress analitiği
- Günlük kalan kalori ve makrolar
- App startup
- Profil değişikliği sonrası yeniden hesaplama akışı
- Hedef geçmişinin tarihsel analizlerde kullanılması

### Faz 4 — QA ve kontrollü yayın

Test matrisi:

- FREE, PLUS ve PRO
- 18 yaş altı uygunluk engeli ve 18+ sınır değerleri
- Yeni ve mevcut kullanıcı
- Üyelik yükseltme ve düşürme
- Türkçe ve İngilizce
- Kilogram ve pound
- Kilo verme, alma, koruma ve kas geliştirme
- Çok düşük ve çok yüksek girişler
- Kalori/makro uyuşmazlığı
- Çevrimdışı ve tekrar deneme davranışı
- Aynı anda iki cihazdan hedef düzenleme
- Dashboard, AI ve progress tutarlılığı
- Geçmiş hedef analitiği
- Accessibility ve ekran okuyucu davranışı

Yayın sırası:

1. Internal/dogfood kullanıcıları
2. Küçük Plus/Pro kohortu
3. Telemetri ve destek kayıtlarının değerlendirilmesi
4. Kademeli genel yayın

## 13. Telemetri ve başarı ölçümü

Önerilen olaylar:

```text
macro_mode_viewed
macro_mode_selected
advanced_macro_paywall_viewed
controlled_macro_previewed
manual_macro_warning_shown
manual_macro_warning_accepted
advanced_macro_age_ineligible
macro_target_saved
automatic_target_restored
macro_target_changed_again
```

Ana metrikler:

- Kontrollü ve manuel moda geçiş oranı
- Paywall dönüşüm oranı
- Önizlemeden kayda dönüşüm oranı
- Kaydetmeden vazgeçme oranı
- Uyarı alma ve kabul etme oranı
- 7 ve 30 günlük yemek kayıt sıklığı
- Otomatik hedefe geri dönüş oranı
- İlk 7 gün içinde tekrarlanan hedef değişikliği
- AI ve Next Meal önerilerinin aktif hedefe uyumu
- Destek talebi ve hata oranı

## 14. MVP kapsamı

İlk yayın için önerilen kapsam:

- GRUN Otomatik mod
- Kalori sabit kontrollü mod
- Öncelikli makroyu sabit tutan kontrollü mod
- Tamamen manuel mod (18+)
- Plus/Pro entitlement kontrolü
- Canlı kalori hesabı
- Merkezi güvenlik kontrolleri
- Uyarılar ve açık kullanıcı onayı
- GRUN Otomatik moda geri dönüş
- Hedef geçmişi
- Dashboard, Next Meal, AI ve Progress entegrasyonu

MVP dışı bırakılması önerilenler:

- Haftanın gününe göre farklı hedefler
- Carb cycling
- Antrenman ve dinlenme günü için ayrı hedefler
- Gelişmiş hedef şablonları
- Uzman/diyetisyen tarafından uzaktan hedef atama

Öncelik, üç modun merkezi policy sınırları içinde, anlaşılır ve bütün sistemde aynı hedefi kullanacak şekilde tutarlı çalışmasıdır.

## 15. Mevcut altyapı notları

Projede hâlihazırda:

- `ADVANCED_MACRO_TARGETS` abonelik özelliği bulunmaktadır.
- Bu özellik Plus ve Pro planlarında açık, Free planda kapalıdır.
- Mobil abonelik modeli `advancedMacroTargets` alanını kullanmaktadır.
- Mevcut hedef veri modelinde kalori, protein, karbonhidrat ve yağ alanları bulunmaktadır.
- Mevcut hedef oluşturma akışı yalnızca profil ve hedef verilerini alıp makroları backend'de otomatik hesaplamaktadır.
- Mobil `EditGoals` ekranı hesaplanan gelişmiş makroları entitlement'a göre gösterebilmekte fakat henüz düzenleme akışı sunmamaktadır.

Mevcut mobil davranışta otomatik makro önizlemesinin `advancedMacroTargets` ile gizlendiği nokta kaldırılmalıdır. FREE kullanıcı otomatik kalori ve üç makroyu görebilmeli; aynı entitlement yalnızca kontrollü/manüel düzenleme girişlerini kilitlemelidir. Mikro besin detaylarının plan kontrolü `MICRONUTRIENT_DETAILS` üzerinden ayrı kalır.

Bu nedenle çalışma yeni bir abonelik özelliği oluşturmaktan çok, mevcut entitlement ve hedef altyapısını güvenli bir düzenleme, versiyonlama ve sistem entegrasyonu modeliyle tamamlamalıdır.

## 16. Uygulama durumu — 25 Ağustos 2026

Tamamlanan hazırlık ve temel geliştirmeler:

- Versiyonlanmış hedef şeması, aktif hedef tekilliği, hedef geçmişi ve acknowledgement audit tablosu hazırlandı.
- `AUTO`, `CONTROLLED` ve `MANUAL` modları ile iki kontrollü strateji backend policy katmanına eklendi.
- Preview, save, otomatik moda dönüş ve hedef geçmişi API akışları oluşturuldu.
- Plus/Pro entitlement ve 18+ kontrolleri backend tarafından zorunlu hale getirildi.
- Mobil hedef ekranına üç mod, kontrollü stratejiler, makro girişleri, önizleme, uyarı kabulü ve paywall yönlendirmesi eklendi.
- FREE kullanıcıların otomatik kalori ile üç makroyu görmesi korundu; yalnızca kontrollü/manüel düzenleme `ADVANCED_MACRO_TARGETS` ile kilitlendi. Mikro besin görünürlüğü ayrı entitlement kapsamında kalır.
- Uyarı kodları kullanıcı arayüzünde TR/EN açıklamalara dönüştürüldü.
- Progress analitiği tarihsel hedefi gün bazında çözümleyecek şekilde güncellendi ve aktif hedef fallback'i korundu.
- Preview token kullanıcı, istek hash'i, profil fingerprint'i ve aktif hedef sürümüne bağlandı; token süresi 15 dakika ve başarılı save sonrasında tek kullanımlıdır.
- `Idempotency-Key` koruması eklendi; aynı anahtar ve payload aynı hedefi döndürür, farklı payload 409 conflict üretir.
- Dashboard ve Energy Balance geçmiş tarih için ilgili hedef sürümünü çözümler; Next Meal bu hedefleri Dashboard üzerinden kullanır.
- AI Nutrition Plan bağlamına hedef modu ve manuel hedef acknowledgement bilgisi eklendi; manuel değerler GRUN tarafından doğrulanmış öneri gibi sunulmaz.
- Mobilde erişilebilir ikinci onay modalı ve güncel profille `GRUN Otomatik’e dön` eylemi eklendi.
- Manuel mod her durumda kullanıcı-tanımlı hedef uyarısı ve açık acknowledgement gerektirir.
- Policy eşikleri environment üzerinden konfigüre edilebilir hale getirildi ve gelişmiş hedef telemetri olayları eklendi.

Yayına hazır kabul edilmeden önce tamamlanması gerekenler:

- Tarif/öğün önerileri ve startup özetlerinin aktif hedef modu cihaz/API smoke testleriyle doğrulanmalı.
- Güvenlik eşikleri ve hukuki metinler beslenme uzmanı/hukuk onayından geçirilerek konfigüre edilebilir policy haline getirilmeli.
- Ekran okuyucu, küçük ekran ve cihaz üstü klavye/ondalık giriş testleri gerçek cihazlarda yapılmalı.
- Internal/dogfood ve küçük Plus/Pro kohortu için operasyonel rollout kararı ile geri alma provası tamamlanmalı.

Kod ve otomatik test kapsamındaki geliştirme tamamlanmıştır. Yukarıdaki uzman/hukuk, gerçek cihaz ve operasyonel rollout kapıları kapanmadan özellik genel yayına açılmamalıdır.
