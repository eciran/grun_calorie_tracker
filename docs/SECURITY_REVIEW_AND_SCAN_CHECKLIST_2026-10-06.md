# GRUN guvenlik incelemesi ve tarama listesi

## Kapsam ve sonuc

Backend, admin UI, mobil uygulama, web dosyalari ve dagitim dosyalari ilk guvenlik incelemesine alindi. Backend envanterinde 1745 Java kaynak dosyasi, 116 controller ve 395 test kaynak dosyasi var. Desen taramalari bu kaynak agacinda calistirildi; her metodun manuel incelemesi veya her endpoint icin saldiri simulasyonu tamamlanmadi.

Guvenlik korumalari mevcut ancak canliya cikmadan once kapanmasi gereken eksikler bulundu. Bu rapor temiz guvenlik sertifikasi, malware analizi veya tum projede acik olmadigi garantisi degildir. Kod bulgulari mevcut yerel kaynaklara aittir; AWS staging ve production konfigurasiyonlari bu incelemede sorgulanmadi.

Ilk incelemede uygulama kodu, kullanici verileri ve AWS ayarlari degistirilmedi. Sonraki duzeltme adimlarinda S01/S02 icin yerel backend, S04 icin backend ve admin UI kodu/testleri guncellendi; AWS deploy ve gercek kullanici verisi mutasyonu yapilmadi. Onceki paywall testleri bu incelemedeki guvenlik test sayisina dahil edilmedi.

## Oncelikli kod bulgulari

### S01 Admin girisi ve MFA denemelerinde hiz siniri eksigi

Oncelik: P1. Durum: yerel kod duzeltmesi tamamlandi ve test edildi; deploy ve coklu task dogrulamasi bekliyor.

Ilk bulgu: admin login, MFA enrollment/verify/disable ve reauthentication yollarinda uygulama seviyesinde ortak deneme limiti yoktu. Hatali MFA denemeleri tekrarlanan guvenlik bildirimi de uretebiliyordu.

Duzeltme (2026-10-06):

- `RateLimitingFilter`: bu bes POST yolu IP basina ortak 20 deneme/dakika butcesini paylasiyor. GET MFA durum okuma, session restore ve logout bu butceyi tuketmiyor.
- `AdminAuthenticationAttemptGuard`: hesap basina 5 deneme/dakika ve 20 deneme/15 dakika. Dedicated admin login, legacy login'deki admin hesaplari ve MFA degisiklikleri ayni hesap anahtarini kullaniyor. Standart kullanici login limit politikasi degistirilmedi.
- Hesap anahtari trim/lowercase sonrasi SHA-256 ile uretiliyor; limiter anahtarina e-posta, parola veya MFA kodu yazilmiyor.
- Sinir asildiginda AuthenticationManager veya MFA servisinden once 429, `RATE_LIMIT_EXCEEDED` ve `Retry-After` donuyor. Kisa sinir 60, uzun sinir 900 saniye bekleme bildiriyor; bunlar sabit pencerenin azami beklemesidir, kesin kalan sure hesaplanmiyor. Kalici DB hesap kilidi eklenmedi. Basarili denemeler de butceye dahil; login sonrasi sayac sifirlanmiyor.
- MFA failure bildirimleri hesap basina 15 dakikada bir birlestiriliyor. Her basarisiz denemenin audit kaydi korunuyor; recovery code kullanimi alarmlari birlestirilmiyor.
- Varsayilan, prod ve example YAML'a IP/hesap limitleri ve env override adlari eklendi.

Kaniti: 56 unit/HTTP contract testi + 19 Spring/H2 auth regresyon testi = 75 test; failure/error yok. Kapsam: login yollarini degistirerek limit asma, hatali parola ve MFA, limit dolunca dogrulama/oturum uretmeme, hesap buyuk-kucuk harf birlestirme, zaman penceresi sonrasi kurtarma, IP/proxy senaryolari, Redis kesintisinde yerel fallback, audit/bildirim ayrimi ve 429 yanit sozlesmesi.

Kalan ortam kabul kosulu: gercek Redis ve birden fazla ECS task ile ortak hesap/IP limitini kanitla; trusted proxy sayisini ve task'a dogrudan erisimi kontrol et. Redis kesintisinde mevcut fallback task basina yerel limit uygular; bu dagitik ortak limit garantisi degildir. WAF durumu okunmadi. S04 owner enrollment istisnasi bu adimda degistirilmedi.

### S02 Parola sifirlaninca mevcut mobil access token iptal olmuyor

Oncelik: P1. Durum: yerel kod duzeltmesi ve HTTP/H2 regresyon testleri tamamlandi; migration/deploy ve PostgreSQL kabul testleri bekliyor.

Ilk bulgu: parola sifirlama refresh tokenlari ve admin oturumlarini iptal ederken standart kullanici JWT'si yalniz subject ve sureyle dogrulaniyordu. Kullanici hesabi aktif kaldiginda eski access token suresi dolana kadar kullanilabiliyordu.

Duzeltme (2026-10-06):

- `V303__add_auth_token_version.sql`: kullanici, refresh token ve admin session icin surum alani; mevcut satirlar 0 ile baslar. JWT'ler `authVersion` tasir. Parola/hash JWT'ye eklenmez; dahili kullanici surumu JSON kullanici verisine dahil edilmez.
- JWT dogrulama guncel DB surumunu kontrol eder. Standart access, admin access, 5 dakikalik MFA enrollment ve amaca bagli owner reauthentication kanitlari birlikte gecersizlesir. DB guvenlik durumu icin process-local cache kullanilmaz.
- Parola sifirlama, hesap parola guncelleme, mevcut tum cihazlardan cikis ve hesap kapatma/kilitleme yollarinin cagirdigi `revokeAllForUser` kullanici kilidi altinda DB surumunu artirir; refresh ve admin session iptali korunur. Ayni gecerli nesilde kullanilmis refresh token tekrar sunulursa mevcut reuse politikasi access tokenlari da iptal eder.
- Normal tek cihaz logout'u diger cihazlarin token surumunu degistirmez. Emekli nesilden gelen refresh/replay istegi yeni girisin tokenlarini iptal etmez.
- Refresh token ve admin cookie kendi verildikleri surume baglidir. Eski token/cookie yeni surumle token uretemez. Token basimi dogrulanan kullanici snapshot'ini kullanir; eski snapshot yeni guvenlik surumunu kendiliginden edinemez.
- Kullanici surumu normal entity update'lerinde yazilamaz; yalniz ayri revocation sorgusu artirir. Gecikmis profil kaydi surumu geri alamaz. Parola reset transaction'i genel `IllegalArgumentException` icin artik commit etmez; yalniz suresi dolan reset kanitini isaretleme kalici tutulur. Parola ve guvenlik durumu hata halinde birlikte rollback olur.

Kaniti: 26 test sinifinda 193 hedefli test, 0 failure/error/skipped. Bunlar S01/S04 regresyonlarini da kapsar; sayilar onceki turlara eklenerek toplanmaz. Sekiz gercek servis/H2 entegrasyon senaryosu: eski access ile HTTP 401, yeni access/refresh basarisi, parola guncelleme, tum cihaz logout'u, stale entity kaydi, admin cookie/setup/reauth iptali, eski refresh replay'inden sonra yeni girisin korunmasi ve hata halinde transaction rollback. MFA kurulum endpoint'i eski tokeni mevcut method-security sozlesmesiyle 403 reddeder; servis cagrilmaz. Migration SQL'i eski satir/default korunmasi icin H2'de de calistirildi; PostgreSQL migration testi yerine gecmez.

Dagitim kabul kosullari:

1. V303 migration ve yeni backend birlikte dagitilmali; S04 nedeniyle admin UI da guncel paketle yayinlanmali. Eski JWT'de surum claim'i yoksa yalniz kullanici surumu 0 iken kabul edilir; ilk global revocation sonrasinda reddedilir. Deploy oncesindeki parola degisiklikleri icin geriye donuk token iptali uretilmez.
2. Rollout tamamlanip eski ECS task'lari durmadan tum task'larda revoke garantisi verilmez. Surum kontrolu olmayan eski image'a rollback bu korumayi kaldirir; geri donus paketi ayni korumayi tasimali.
3. S03 ile PostgreSQL'de refresh/reset/recovery paralel consume ve logout/refresh yarislari yerelde tamamlandi. Tum login/restore/federated yarislari ve V303 PostgreSQL migration kabul testi halen ayrica tamamlanmali.
4. Staging'de gercek mobil yeniden giris, MFA enrollment/reauth ve birden fazla ECS task ile kabul testi yapilmali. Federated hesap link/unlink sonrasi oturum politikasi bu duzeltmede degistirilmedi; kimlik/yetki matrisinde ayrica incelenecek.

### S03 Refresh token tek kullanim kontrolu atomik degil

Oncelik: P2. Durum: yerel atomik consume duzeltmesi ve izole PostgreSQL 16 eszamanlilik testleri tamamlandi; staging/cok-task kabul testi bekliyor.

Ilk bulgu: `RefreshTokenRepository` tokeni normal sorguyla okuyordu; kosullu consume bulunmuyordu. S02'de eklenen kullanici kilidi ve credential surumu parola revocation'ini koruyor ancak kilitten once okunmus usedAt degerini atomik yeniden dogrulamiyordu. Iki eszamanli istek ayni kullanilmamis tokeni okuyup iki farkli devam tokeni uretebiliyordu; yalniz `@Transactional` bu yarisi engellemez.

Duzeltme (2026-10-06): refresh/reset/recovery kodlari kosullu UPDATE ile tek kez tuketiliyor. Refresh consume ayrica revokedAt, expiresAt ve credential surumunu denetliyor. Reset ve refresh sonrasi eski entity snapshot'i tekrar kaydedilmiyor. Parola reset talebi ve onayi kullanici kilidiyle siralaniyor. Tek token logout'u kosullu UPDATE kullaniyor; paralel yenilemenin usedAt degerini ezemiyor. Beklenmeyen token/JWT uretim hatalari consumption ve replacement'i birlikte rollback ediyor.

Ayni credential neslinde ilk 30 saniyedeki tekrar refresh 409 doner; yeni token basmaz ve kazanan oturumu iptal etmez. Daha eski replay icin mevcut neslin global revoke politikasi korunur; emekli nesil yeni girisi iptal edemez. Mobil mevcut 409 davranisinda token silmiyor. Bu bir idempotent basari/cevap kurtarma modeli degildir: basarili rotation yaniti tamamen kaybolduysa ve yeni token saklanmadiysa yeniden giris gerekebilir.

`PostgresTokenConcurrencyTest`: opt-in `-Dgrun.security.pg=true`; yalniz localhost:55433 uzerinde gecici `security_test` DB. Yedi test: dort paralel refresh'te tek replacement ve gecerli kazanan, iki reset'te tek parola degisikligi, ayni MFA recovery koduyla tek login, reset audit hatasinda rollback, JWT uretim hatasinda rollback ve yeniden deneme, tek-token logout/refresh yarisi, global logout/refresh yarisi. Gercek PostgreSQL 16 kullanildi; uygulamanin yerel veya AWS veritabani degistirilmedi. Bu testler tum federated/login/session ailesi senaryolarinin veya V303 PostgreSQL migration kabulunun yerine gecmez.

### S04 Owner icin MFA kayit zorunlulugu atlanabiliyor

Oncelik: P1 kosullu. Durum: yerel kod/UI duzeltmesi tamamlandi; staging gecis ve gercek authenticator testi bekliyor.

Ilk bulgu: MFA etkin olmayan OWNER normal parola girisiyle admin oturumu acabiliyordu. Genel admin MFA zorunlulugu owner'a uygulanmiyordu. Ek incelemede `beginEnrollment` mevcut MFA'yi yalniz parolayla degistirebiliyor ve eski session'lar MFA durum degisikligini izlemiyordu.

Duzeltme (2026-10-06):

- `AdminMfaAccessPolicy`: OWNER icin MFA daima zorunlu; delegated admin'ler mevcut `grun.security.admin-mfa-required` politikasini izler. STANDARD/PRO kullanicilarin mobil MFA politikasi degismedi.
- Parola dogrulamasindan sonra MFA'siz owner'a normal session/cookie/refresh yerine `mfaEnrollmentRequired=true` ve 300 saniyelik `ADMIN_MFA_ENROLLMENT` amacli token donuyor. Dedicated ve legacy login ayni kisitli yaniti verir.
- JWT filtresi bu tokeni yalniz iki POST yolunda kabul eder: `/api/v1/auth/admin/mfa/enrollment` ve `/api/v1/auth/admin/mfa/enrollment/verify`. Yalniz `ADMIN_MFA_ENROLLMENT` authority'si verilir; ROLE_ADMIN/OWNER ve normal izinler verilmez. Anonim, normal session ve reauth tokenleri bu kurulum endpointlerini acamaz.
- Kurulum endpointleri hesap/IP limitlerine dahil. Servis kilitli/devre disi hesabi, suresi dolmus kurulumu, zaten etkin MFA'yi tekrar kurmayi ve dogrulama replay'ini reddeder. Token dogrulama bittikten sonra da yeni kurulum yapamaz.
- Zorunlu MFA devre disi birakilamaz. Mevcut MFA parolayla degistirilemez. Dogrulama sonrasi eski refresh ve admin session'lar iptal edilir; yeni normal giriste MFA gerekir. MFA durumundan eski session'lar da authenticate/restore/refresh kontrollerinde reddedilir.
- Admin UI'da QR, manuel anahtar, kod dogrulama ve bir defa gosterilen kurtarma kodlari ekrani var. Kurulum tokeni sadece component belleginde; normal token deposuna yazilmasi engellenir. Kurtarma kodlari kaydedildikten sonra yeniden giris yapilir. Giris artik alfanumerik recovery kodlarini da kabul eder.
- Browser testinin yakaladigi MFA 401 hatasi duzeltildi: login challenge genel session-expired mesajina cevrilmiyor; UI `ADMIN_MFA_REQUIRED/INVALID` kodlarini kullanir. Auth'suz login/setup 401'i normal session'i logout ettiren global event'i tetiklemez.
- MFA status yanitina `required` eklendi; admin paneli zorunlu hesaplar icin disable islemini gostermiyor.

Kaniti: 113 hedefli Maven testi (unit + gercek Spring Security filter/method security HTTP + H2 auth regresyonu) basarili. TypeScript kontrolu, izole Vite build ve mevcut admin session/activity kontrat testleri gecti. Playwright fixture testi 1440x1000 ve 390x844 ekranlarda kurulum, QR, kurtarma kodlari ve yeniden MFA challenge akisini dogruladi; gercek backend/kullanici hesabi kullanilmadi. Goruntu ciktisi `outputs/security-review/s04-setup-1440.png` ve `s04-setup-390.png`.

Dagitim ve kurtarma kabul kosullari:

1. Backend ve admin UI ayni paketle yayinlanmali. Izole kontrol build'i `admin-ui/dist/security-s04` altinda; dagitim icin normal `npm run build` ile `static/admin-ui` yeniden uretilmeli. Eski UI ile bu backend'in tek basina dagitimi onayli degil.
2. MFA sifreleme anahtarinin task'lar arasinda ayni ve kalici oldugunu dogrula; gercek owner/backup owner icin QR, MFA login, recovery code, eski session reddi ve MFA'siz owner'in kisitli kurulumu staging'de test et.
3. Root/recovery owner'a da parola-only istisna taninmiyor. Authenticator ve tum recovery kodlari kayipken erisim icin kimlik dogrulamali, denetimli operasyon runbook'u ayrica hazirlanmali; otomatik MFA bypass endpoint'i eklenmedi.
4. S02'de parola sifirlamadan once verilmis kurulum ve reauth tokenlerinin yerel credential surumu kontrolu tamamlandi; ortam kabul kosullari S02 altinda. S03'te recovery code eszamanli tek-kullanim guvencesi izole PostgreSQL testiyle dogrulandi.

### S12 Progress olusturmada istemci ID'si mevcut kaydi hedefleyebiliyor

Oncelik: P1. Durum: yerel duzeltme ve iki hesapli HTTP regresyonu tamam; deploy bekliyor.

`POST /api/v1/progress` ayni response/request DTO'sunu kullaniyordu. `ProgressLogMapper.toEntity` istemci `id` degerini yeni entity'ye kopyaliyordu; repository save/merge mevcut progress satirini authenticated kullaniciya yeniden baglayabilir ve degerlerini degistirebilirdi. Mevcut GET/PUT/DELETE sahiplik sorgulari POST create yolunu korumuyordu.

Duzeltme: create mapper ID kabul etmiyor; server yeni kayit uretiyor. Server-side logDate da sunucuda atanir; onboardingBaseline istemciden kopyalanmaz. HTTP testinde B, A'nin baseline kayit ID'sini ve user/userId/onboardingBaseline alanlarini gonderir: B icin farkli ID'li normal kayit olusur; A'nin sahibi/kilosu/baseline'i degismez. B'nin A kaydina GET/PUT/DELETE istekleri 404. Yeni ve onceki progress/controller/analytics regresyonlari gecti. Gercek DB'de eski ihlal kaydi taranmadi; canli/staging verisinin bozulmus oldugu iddia edilmiyor.

## Sertlestirme ve ek dogrulama

| ID | Alan | Bulgular ve gerekli islem | Durum |
| --- | --- | --- | --- |
| S05 | CORS | Prod/staging HTTPS allowlist ayrildi; local/default Expo ve browser erisimi korundu. Prod+local birlikteyken de local origin reddediliyor. | Dort CORS regresyonu gecti; gercek browser/staging kabul bekliyor |
| S06 | Container | Runtime USER 10001:10001, root-owned read-only JAR ve storage/logs yazma dizinleri ayrildi. Image OS paketleri build sirasinda guncelleniyor. | Gercek image non-root/read-only/cap-drop testi ve OS CVE taramasi gecti; ECS user/volume/IAM kontrolu bekliyor |
| S07 | SSRF | Tarif cache istemcisi baglanti icin kullanilan DNS adreslerini denetliyor; private/metadata/yerel IPv6 ve gecis adresleri reddediliyor. HTTPS/443 zorunlu; otomatik redirect/retry kapali, her redirect yeniden denetleniyor. | Yerel kod ve negatif test tamam; staging redirect/TLS ve egress kabul testi bekliyor |
| S08 | AI maliyet ve dosya istismari | AI IP butcesine ek authenticated hesap butcesi var; IP/endpoint/plan ID degistirerek hesap limitini bolme engellendi. Redis INCR+TTL tek Lua islemi; iki limiter ornegiyle ortak butce testi gecti. Medya read/write byte/path kontrolleri guclendi. | Hedefli eksikler kapatildi; gercek ECS/cok-task, aktif is concurrency ve tam dosya matrisi bekliyor |
| S09 | Public medya | AI photo reference URL'si UUID ve TTL ile korunuyor, herkesin okuyabilecegi bearer URL niteliginde. Paylasim, URL loglama, TTL, EXIF ve cache davranisi test edilmeli. Public avatar/recipe ile ozel evidence ayri tutulmali. | Mevcut kontrol incelendi; sizinti kaniti yok |
| S10 | Mobil web depolama | Native token depolama SecureStore; web fallback localStorage. Web dagitimi yapilirsa XSS sonrasi token erisimi icin ayri oturum modeli gerekiyor. | Kodda dogrulandi; native acik olarak sayilmadi |
| S11 | Uretim profil ayrimi | Prod YAML swagger-public false, secure-cookie true; varsayilan YAML farkli. ECS'de gercek profile/env dogrulanmadan guvenli varsayilmamali. Mobil app.config.js HTTP'yi development disinda kapatiyor; app.json tek basina acik kaniti degil. | Kaynak dogrulandi; dagitim bekliyor |

## Bagimlilik taramalari

`npm audit` mevcut lockfile'lar ile calistirildi; paket kurulumu veya audit fix uygulanmadi. Sayilar paket/metavulnerability kayitlaridir, bagimsiz istismar sayisi degildir.

| Proje | Tarama | Sonuc |
| --- | --- | --- |
| Admin UI | Production dependency scope | 0 raporlanan zafiyet |
| Admin UI | Dev dahil | 4 kayit: 3 high, 1 moderate |
| Mobil | Production dependency scope | 30 kayit: 18 high, 12 moderate |
| Mobil | Dev dahil | 34 kayit: 21 high, 12 moderate, 1 low |
| Java backend | Maven dependency CVE / SBOM | Henuz tamamlanmadi |
| Container | Base image ve final image CVE | Henuz tamamlanmadi; Docker kapali |

Mobil production dependency scope icinde Expo/Metro/CLI build araci zincirleri de bulunuyor. Runtime bundle erisilebilirligi ayri incelenmeli. Admin uyarilari build bagimlilik zincirinde; browserslist, nanoid, postcss ve baseline-browser-mapping kayitlari mevcut. Mobil URI decode, XML isleme, sertifika ve build zinciri kayitlari manuel triaj gerektiriyor. Kor upgrade veya `npm audit fix --force` yapilmamali; Expo SDK uyumlulugu, patch secenekleri ve regresyon testleriyle ilerlenmeli.

Ham raporlar: `outputs/security-review/admin-npm-audit.json`, `admin-npm-production-audit.json`, `mobile-npm-audit.json`, `mobile-npm-production-audit.json`.

## Mevcut korumalar

- BCrypt parola hash'i; kriptografik rastgele ve hash'lenmis refresh/reset tokenlari.
- Google/Apple imza, issuer, audience ve zaman dogrulamasi; Apple nonce kontrolu mevcut. Replay/challenge baglantisi ayrica test edilmeli.
- Admin permission filtresi, owner hassas islem filtresi, reauth ve approval yapilari.
- Admin refresh/session cookie'sinde HttpOnly ve SameSite Strict; prod secure cookie ayari.
- Admin access token bellekte tutuluyor; taranan admin kaynaklarda token localStorage kullanimi gorulmedi.
- Dosya boyut, MIME/signature, managed path ve storage root kontrolleri; rastgele AI foto tokeni ve TTL.
- Incelenen food log/template/health akislarinda kullaniciya scoped sorgular mevcut; tum endpointler icin IDOR testinin yerini tutmaz.
- Local .env Docker context disinda; incelenen Git izlenen credential uzantilarinda gercek .env/PEM/P8/JKS kaydi gorulmedi. Git gecmisi veya tum secret formatlari taranmadi.
- Prod hata stacktrace/message ve actuator detaylari kisitli; medya bucket sablonunda public access block mevcut.

## Calistirilan testler

Iki hedefli Maven test grubu basarili: 49 + 46 = 95 test, 0 failure/error.

Kapsam: JWT filtresi, rate limit, CORS, refresh token, federated auth, account link, owner hassas islem, admin cookie, admin security, upload session, photo reference, avatar inspector, local media/path policy, password reset, export projection ve subscription feature filtresi.

Testlerin gecmesi S01-S11'in kapandigi anlamina gelmez. Mevcut testler eksik guvenlik senaryolarini kapsamayabilir. Yeni race/revocation/MFA abuse senaryolari duzeltme paketine eklenmeli.

## Bastan sona tarama listesi

| Sira | Tarama alani | Kapanis icin kanit | Mevcut durum |
| --- | --- | --- | --- |
| 1 | Endpoint ve veri akisi envanteri | 116 controller icin method/path/role/veri sahibi/limit matrisi | Kaynak sayimi yapildi; matris bekliyor |
| 2 | Kimlik dogrulama | JWT tur ayrimi, imza, sure, issuer/audience, token revoke, sifre reset, email verify | Manuel ilk inceleme ve testler yapildi; eksikler var |
| 3 | Oturum ve eszamanlilik | Refresh/reset/recovery token tek kullanimi; hesap/cihaz degisimi | Atomik tek-kullanim ve yedi PostgreSQL race/rollback testi tamam; staging/cok-task ve genis login matrisi bekliyor |
| 4 | Admin MFA ve yetki | Owner/delegated admin/normal kullanici icin deny matrisi; enrollment bypass yok | S01/S04 yerel kod/test tamam; ortam gecis ve kurtarma kabul testleri bekliyor |
| 5 | Nesne yetkilendirme | A kullanicisi B'nin diary, recipe, plan, draft, feedback, health, export ve evidence verisine erisemez | Secili akislarin iki hesapli HTTP testleri tamam; tum endpoint/DTO/service yetki matrisi bekliyor |
| 6 | Field yetkisi ve mass assignment | Rol, sahip, verified, plan, kredi ve approval alanlari istemciden set edilemez | Ayrintili DTO/service matrisi bekliyor |
| 7 | Injection | SQL/HQL, command, HTML, template, CSV formula, XML/XXE, deserialization | Desen taramasi yapildi; hedefli payload testleri bekliyor |
| 8 | Dosya ve medya | MIME/signature, boyut/pixel limit, path/symlink, EXIF, content-disposition, ozel evidence yetkisi | Dosya servisleri incelendi ve mevcut testler gecti; tam payload matrisi bekliyor |
| 9 | SSRF ve dis kaynaklar | Private/metadata/IPv6/DNS rebinding/redirect engeli ve egress policy | DNS TOCTOU riski bulundu; ag testi bekliyor |
| 10 | AI guvenligi | Prompt injection, girdi/yanit limitleri, PII, kota rezervasyonu/iade, pahali istek eszamanlilik limiti | Endpoint limit kapsami kismi; diger kontroller bekliyor |
| 11 | Odeme | Webhook auth, ownership, replay/idempotency, client proof'a guvenmeme, refund ve paket degisimi | Onceki paywall testleri mevcut; security replay matrisi ayrica bekliyor |
| 12 | Cache ve Redis | Kullanici/bolge/dil izolasyonu, cache poisoning, TTL, public/private response ayrimi | Health scope ornegi incelendi; tam matris bekliyor |
| 13 | Admin/web/mobil frontend | XSS, guvenli depolama, deep link allowlist, log redaction, TLS, izinler | Desen taramasi ve config incelemesi yapildi; cihaz/browser saldiri testleri bekliyor |
| 14 | Dependency ve tedarik zinciri | npm/Maven/native CVE, SBOM, lockfile, registry, install script, imza/provenance | npm raporlari hazir; Java/native reachability bekliyor |
| 15 | Secret taramasi | Tum izlenen dosyalar, Git gecmisi, APK/IPA/JAR/JS bundle ve loglar; degerleri aciga cikarmadan rapor | Yalniz izlenen credential dosya adlari kontrol edildi; kapsamli tarama bekliyor |
| 16 | Docker ve AWS | Non-root, image CVE/digest, IAM least privilege, ECS secrets, S3/RDS/Redis private access, TLS/WAF | Yerel Docker/S3 sablonu incelendi; gercek AWS ayarlari bekliyor |
| 17 | Veri ve mahremiyet | Export erisimi, teknik/secret alanlar, retention, hesap silme, yedekler ve audit log yetkileri | Export projection testi gecti; tam akis bekliyor |
| 18 | Dinamik saldiri/regresyon | Izole ortamda auth/IDOR/SSRF/upload/replay/rate-limit; zarar vermeyen payloadlar | Mevcut unit/contract testleri calisti; kapsamli dinamik tarama bekliyor |
| 19 | Zararlı yazilim ve build butunlugu | Imzali APK/IPA/image, dependency source, CI artifact hash/provenance; cihaz/host antivirus ayri | Malware taramasi yapilmadi; statik kod taramasi bunun yerine gecmez |
| 20 | Surekli koruma | CI guvenlik kontrolleri, advisory alarmi, P1 release gate, istisna bitis tarihi | Kurulum bekliyor |

## Uygulama sirasi ve canliya cikis kapisi

1. S01/S02/S04 yerel kod/UI/test tamam. Migration/backend/admin UI dagitim testini birlikte yap; kurtarma runbook'unu tamamla.
2. S03 atomik refresh/reset/recovery ve PostgreSQL race/rollback testleri yerelde tamam. Staging migration, gercek mobil retry ve cok-task kabulunu tamamla; kayip rotation yaniti kurtarma modeli ayrica degerlendirilmeli.
3. Gunluk/tarif/plan/AI sonucu, feedback, health, export, evidence ve nested item akislarinin secili iki hesapli HTTP testleri tamamlandi. Tum endpointlerin etkin yetki ve tum DTO/service alan matrisi halen ayri kapanis kosulu.
4. Tarif DNS rebinding/MIME/piksel, AI IP+hesap limitleri ve yerel medya path/bounded read kontrolleri yerelde guclendirildi. Gercek redirect/TLS/egress, EXIF, parent-directory TOCTOU, aktif AI is concurrency ve ECS/cok-task kabulunu tamamla.
5. Bagimlilik uyarilarini runtime/build ayrimi ile siniflandir; uyumlu guncellemeleri test et.
6. Secret/Git gecmisi, Java/container/native taramalarini ve gercek AWS ayar kontrolunu tamamla.
7. Mobil build ve staging guvenlik smoke testi; acik P1 bulunmadigini release oncesi kanitla.

Guvenlik kabul kosulu: dogrulanmis yuksek riskli aciklar kapali; kosullu risklerin etki/erisim on kosullari kayitli; kalan istisnalar sorumlu ve bitis tarihli; dagitilan image/build ile incelenen kaynak ayni. Local koddan production guvenligi sonucu cikarilmaz.

## Adim 3 ve 4 yerel dogrulama

Kayitli Spring MVC mapping envanteri `outputs/security-review/registered-endpoint-inventory.json` son sahiplik testinde guncellendi: 577 handler mapping, 117 aktif controller, 244 annotation ile yetki tanimi. S3 evidence endpointleri bu test context'inde etkin; onceki 565/113/240 sayimi farkli konfigurasyona aitti. Kaynak controller sayisi aktif test context'indeki mapping sayimi ile ayni olculmez. Annotation boslugu otomatik yetkisizlik bulgusu degildir; SecurityConfig, admin permission filtresi, abonelik kapilari ve service sahiplik sorgulari da incelenmelidir. Envanter kayitlari bu nedenle `PENDING_EFFECTIVE_AUTHORIZATION_REVIEW` durumundadir.

Iki hesapli gercek Spring Security/MockMvc/H2 testleri:

- Gunluk kaydini sahibi okuyabilir; diger hesap okuyamaz/silemez (404).
- Ozel tarif sahibi tarafindan okunabilir; baska hesap okuyamaz/arsivleyemez, public detail yolu da ozel tarifi acmaz (404).
- Beslenme planinin baska hesap tarafindan okunmasi/silinmesi reddedilir (404).
- AI sonucunu baska hesap okuyamaz veya completion-seen ile degistiremez (400); sahibi okuyabilir ve onaylayabilir (204).
- Profil update'inde id/userId, role, email, password, authTokenVersion, emailVerified ve adminMfaEnabled enjekte edilmesi bu alanlari degistirmez; yalniz DTO'nun izin verdigi name degisir.
- Tokensiz gunluk, plan ve ozel tarif detail erisimi 401 doner.

Dosya ve dis kaynak duzeltmeleri:

- Apache HttpClient'in baglanti DNS resolver'i ayni cozumlenen adres setini kontrol eder; TLS sertifika/hostname dogrulamasi kapatilmadi. Kontrollu resolver testi ilk cozumlemede public, baglanti cozumlemesinde loopback vererek baglantidan once reddi dogrular. IPv4/IPv6, mixed DNS answer ve 12 uygunsuz kaynak URL negatif senaryosu mevcut.
- Body okuma byte siniri ve 15 saniyelik kontrolle sinirli; socket response timeout 15 saniye, connect timeout 8 saniye. Bunlar tum redirect zinciri/DNS dahil tek bir toplam sure garantisi degildir. Sinir asiminda veya redirect'te response hemen kapatilir; kalan body drenajina girilmez.
- Tarif upload ve cache JPEG/PNG/WebP header imzasi ve boyutunu mevcut inspector ile kontrol eder. Upload gercek stream'den byte sinirina kadar okunur; varsayilan 4 MiB boyut korunur. `GRUN_RECIPE_IMAGE_MAX_DECODED_PIXELS` varsayilani 48.000.000. Dosya uzantisi MIME'dan turetilir; istemcinin original filename uzantisina guvenilmez. Yeni dosya CREATE_NEW ile yazilir; final dosya symlink ise public read reddedilir.
- Bu header denetimi tam raster decode, yeniden encode veya EXIF temizligi degildir. Parent-directory symlink ve read/write TOCTOU guvencesi ile tam PNG/JPEG/WebP corruption matrisi ayrica tamamlanmalidir.
- AI generation POST'lari ortak IP butcesini kullanir (mevcut varsayilan 10/dakika); limit asimi 429 ve Retry-After doner. AI history GET bu butceyi tuketmez. IP butcesi account credit kotasinin yerine gecmez; NAT paylasimi ve cok-task dagitik enforcement ayrica degerlendirilmelidir.

Son hedefli calisma: **15 test sinifi, 111 test, 0 failure/error/skipped**. Kapsam yeni IDOR/SSRF/upload/AI limit testleri ile mevcut JWT, credential revocation, refresh, admin MFA, evidence, photo reference ve storage regresyonlari. Onceki 193 testle cakisan testler vardir; sayilar toplanmamalidir. Ortak target klasorundeki eszamanli derleme cakismasindan sonra kontrol `-Dgrun.build.directory=target/security-steps-3-4` ile izole yapildi; normal build directory target olarak kalir.

Bu paket AWS'ye deploy edilmedi; gercek kullanici verisi/abonelik kaydi degistirilmedi. Java dependency CVE/SBOM taramasi ve staging kabul kosullari acik; adim 3/4 genis kapsamli olarak tamamen kapandi sayilmaz.

## Oturum ve kalan sahiplik adimlari - 2026-10-06

Son hedefli kontrol: **12 test sinifi, 110 test, 0 failure/error/skipped**. Onceki test turlariyla cakisir; sayilar toplanmamalidir. Yedi PostgreSQL testi opt-in etkin olarak gercek PostgreSQL 16.13 uzerinde calisti; diger HTTP/servis regresyonlari izole H2 ve mock dis servislerle calisti. Kontrol `target/security-sessions` altinda izole derlendi. Gecici PostgreSQL container'i testlerden sonra kaldirildi; mevcut local DB ve AWS kullanilmadi.

Sahiplik kapsaminda `ResourceOwnershipHttpSecurityTest` 13 HTTP senaryosu kapsiyor:

- Feedback screenshot authorize/complete baska hesaptan 404; storage servisi cagrilmiyor. Sahibinin yetkili upload anahtari hesap/feedback kaydina bagli.
- Health metrik, summary ve silme authenticated hesaba bagli. Iki hesap ayni externalId kullanabiliyor; body/query userId enjekte edilmesi diger hesabin adimlarini okuyamiyor/degistiremiyor/silemiyor. Gercek abonelik kapisi acik; iki sentetik hesapta ACTIVE PRO test verisi kullanildi.
- GDPR export userId/email query ile baska hesaba yonlendirilemiyor; yabanci gunluk verisi response'a girmiyor, sahibinin verisi geliyor.
- Product review mine listesi sahibiyle sinirli; yabanci review evidence/withdraw reddediliyor. Kullanici kendi review'una diger hesabin FINALIZED upload session'ini baglayamiyor. Yabanci upload session okuma/finalize da reddediliyor; status ve storage degismiyor.
- Nested plan item baska hesaptan veya yanlis parent plan altindan islenemiyor. Bulunan ek tutarsizlik duzeltildi: ayni Idempotency-Key tekrarinda da planId dogrulaniyor; yanlis parent 404. log/skip/replace ayni replay kontrolunu kullaniyor. Skip HTTP ve mevcut log replay unit regresyonlari gecti.
- Onceki gunluk/tarif/plan/AI-result/profile mass-assignment ve anonim erisim testleri korundu. Admin session, MFA enrollment ve JWT credential surumu testleri de son gruba dahil.

Bu iki is kaleminin hedefli yerel duzeltme/testleri tamamdir. Tum 577 mapping icin rol/alan/limit matrisi, staging migration/cok-task kabul, kayip refresh yaniti kurtarma modeli ve genel guvenlik listesinin diger maddeleri halen aciktir. AWS deploy veya mobil build alinmadi.

## Adim 3-4 takip duzeltmeleri - 2026-10-06

Bu turda genis liste tamamen kapatilmadi; secili yerel yetkilendirme, alan yetkisi ve kaynak istismari eksikleri ele alindi. S12 progress create acigi duzeltildi. Mapping envanteri authenticated principal kullanimi ve body/part/model input type bilgisiyle genislendi; bunlar etkin yetki veya DTO alan guvenligi kaniti degildir. Tum mapping'lerdeki rol/alan/service matrisi halen pending.

Son kontrol: **16 test sinifi, 104 senaryo; 103 basarili, 0 failure/error, 1 skipped**. Windows normal symlink olusturma yetkisi olmadigi icin symlink senaryosu skip; Windows junction senaryosu gecti. Buyuk regresyon grubu 15 sinif/101 senaryo, ek account interceptor unit grubu 1 sinif/3 senaryo; bu iki son grubun siniflari ayrik. Onceki tekrar calistirmalar bu sayiya eklenmedi. Gercek Redis opt-in concurrency testi etkin olarak calisti. Test container'i kontrol bitiminde durduruldu ve auto-remove ile kaldirildi.

AI hesap limiti:

- MVC interceptor gecerli SecurityContext sonrasi, controller/AI servisinden once denetliyor. Tum mevcut user AI generation yollarinda ortak hesap anahtari kullaniliyor. IP limiti de korunuyor.
- Varsayilan 10 istek/dakika; `GRUN_RATE_LIMIT_AI_ACCOUNT_MAX_REQUESTS_PER_MINUTE` ile ayarlanir. Public/history GET ve login/refresh/odeme akislarini kapsamaz. Redis anahtarinda e-posta veya ham token yok; normalize subject SHA-256 degeri var. Ayni HTTP async redispatch iki kez sayilmaz.
- Sinirda 429/RATE_LIMIT_EXCEEDED ve Retry-After:60; AI servisi cagrilmaz. Iki hesabin butceleri bagimsiz. Gunluk/haftalik endpoint ve IP degisimi tek hesap butcesini sifirlamaz. Plan ID, endpoint, subject casing ve redispatch unit kontrolleri mevcut.
- Mevcut `RequestRateLimiter` Redis/fallback modeli kullanildi. Redis etkinse iki servis ornegi ortak sayac kullanir; Redis yok/kesikse task-local fallback vardir. Prod YAML Redis etkin; gercek ECS env okunmadi. Lua atomik INCR+PEXPIRE, eski expiry'siz anahtari da onarir. Gercek izole Redis'te iki limiter/sekiz thread/80 deneme, toplam 15 kabul ve pozitif TTL kanitlandi.
- Bu bir dakika butcesidir; dagitik aktif is semaphore'u veya provider bazli global concurrency limiti degildir. Mevcut kredi rezervasyon/iade, AI prompt/model veya response davranisi degistirilmedi. Ayni idempotency istegi de admission butcesine dahildir; budget bittiginde GET history/recovery yollarini kullanmak gerekir.

Medya:

- Recipe upload ve LOCAL media store root/ancestor/final path symlink ve canonical redirect denetimi kullaniyor. Windows junction senaryosu gecti. Write/read NOFOLLOW_LINKS ile final dosya linkini takip etmiyor; recipe public read artik kontrol sonrasi yeniden UrlResource acmak yerine sinirli byte snapshot donuyor.
- LOCAL media read gercek stream'de maximum+1 byte okur; onceki Files.size + readAllBytes buyume yarisi giderildi. Checksum dosyasi path guard ve 256 byte siniri kazandi. Yerel storage testleri gecti.
- Root/parent denetimi filesystem capability sandbox'i degildir: adversarial parent-directory rename/junction degisimi ile check/open TOCTOU icin tam garanti verilmez. Depolama dizinleri untrusted OS kullanicilarina writable olmamali. SecureDirectoryStream/OS kabul testi, Linux symlink senaryosu, raster corruption ve EXIF stripping halen acik. Windows normal symlink privilege yoklugunda ilgili negatif test skip olur; junction testi ayri calisti.

AWS deploy, mobil build ve gercek kullanici verisi mutasyonu yapilmadi. Redis yalniz localhost:55434 uzerinde gecici test container'inda kullanildi; mevcut local Redis/DB degistirilmedi. Test ciktisi `target/security-3-4-followup/surefire-reports`, izole build directory `target/security-3-4-followup`.

## Adim 5-6 dependency, secret ve container sonuclari - 2026-10-06

Bu tur yerel kod/build duzeltmesidir. AWS deploy, mobil native build, secret rotation veya Git gecmisi yeniden yazimi yapilmadi. AWS `grun-staging` SSO token'i suresi dolmus dondu; yeniden login istendi. Gercek ECS/IAM/S3/RDS/Redis/TLS/WAF kontrolu bu nedenle yapilamadi. Yerel Docker image sonucu AWS'de calisan image sonucu degildir.

### Dependency duzeltmeleri

- Admin: fresh npm audit 5 bulgu (4 high, 1 moderate) -> 0. Compatible `npm audit fix --ignore-scripts` lockfile guncellemesi; uygulama bagimlilik major surumleri degistirilmedi. Full ve omit-dev npm audit temiz; TypeScript/Vite build gecti. Izole admin build `admin-ui/dist/security-dependencies`; static deploy bundle'a kopyalanmadi.
- Mobil: 35 bulgu (1 critical, 21 high, 13 moderate) -> 31 (0 critical, 17 high, 14 moderate). shell-quote, xmldom, source-map-js ve uyumlu leaf guncellemeleri yapildi. Expo SDK 57 korundu; SDK icinde build-properties, camera, image-manipulator, image-picker, linking ve notifications beklenen patch surumlerine getirildi. `expo install --check`, TypeScript, odeme dortlu contract/behavior ve onboarding goal contract gecti. Gercek cihaz/native build kabulunun yerine gecmez.
- Mobil kalan 31 sayisi 31 bagimsiz runtime acigi anlamina gelmez; Expo/Metro/Tailwind arac zincirinde transitive/metavulnerability kayitlari vardir. Router/query-string/decode-uri-component yolu runtime icin ayrica degerlendirilmeli. Registry node-forge 1.4.0 ve braces 3.0.3 icin ayni zincirde yamali surum sunmuyor; force Expo 44/RN downgrade/Tailwind 4 onerileri uygulanmadi. decode-uri-component 0.5 major degisimi de otomatik override edilmedi. SDK/NativeWind uyumlu upgrade ve runtime URL payload kabul testi acik release kosuludur.
- Backend parent Spring Boot 3.5.0 -> 3.5.16. BOM'dan daha yeni security fixes: Jackson 2.21.7, Netty 4.1.137.Final, Tomcat 10.1.60, pgjdbc 42.7.12, HttpCore 5.4.3, HttpClient 5.6.3, Commons Lang 3.18.0, Log4j API BOM 2.25.5. Major Spring Boot 4/Jackson 3 gecisi yapilmadi.
- Gercek eski repackaged JAR taramasi: 125 dependency/advisory eslesmesi (9 critical, 50 high, 54 medium, 12 low). Son JAR: yalniz 1 conditional critical library kaydi; high/medium/low 0. Bu sayilar reachability veya istismar kaniti degildir.
- CycloneDX 1.6 SBOM XML/JSON 158 component ile `target/security-final-tests/security-bom.*` altinda uretildi. Son JAR SHA-256: `103BE0FDC38B305D99D71DDFE1805B6BD8D91FB20D5EFFE0E9CCB15B00B76934`. Eszamanli baska calismalar varsa bu hash sadece taranan artifact'i tanimlar.

### Kalan Spring library kaydi

`CVE-2026-47884`, Spring MVC XsltView icin scanner CRITICAL kaydi; Spring'in kendi advisory'si MEDIUM olarak derecelendiriyor. On kosul XsltView ile wildcard view rendering. Kaynakta bu view/resolver kullanimi yok. `ViewRenderingSecurityConfig` XsltView/XsltViewResolver bean'lerini initialization oncesi reddediyor; iki negatif test ve normal Spring HTTP context gecti. Bu onlem library bytecode'unu yamalamaz; dinamically/manual olusturulan view icin genel garanti degildir.

Resmi fix: Spring 6.2.20 enterprise veya 7.0.9 OSS. Buyuk framework migration veya enterprise dependency erisimi ayri karar/kabul gerektirir. Scan sonucundan bu kayit silinmedi, `.trivyignore` eklenmedi. `scripts/scan-security-artifact.ps1` high/critical varsa fail-closed release gate verir; bu kayit nedeniyle son deneme beklenen sekilde BLOCKED. Release icin source/reachability kanitli, sorumlu ve bitis tarihli istisna onayi veya resmi library upgrade gerekir; otomatik istisna verilmedi.

### Container ve paketleme

- Docker non-root UID/GID 10001, root-owned 0444 JAR, `/app/storage` ve `/app/logs` application-owned. `/tmp` JVM temporary directory. Root filesystem read-only ile bu uc dizin icin explicit writable tmpfs denenerek gercek container permission testi gecti; AWS task tanimi henuz degistirilmedi.
- `apk upgrade --no-cache` runtime OS yamalarini build'e aliyor. Eski yerel Temurin base 36 OS bulgusu; son gercek uygulama image OS taramasi 0. Image icindeki JAR'da ayni conditional Spring kaydi kaldi.
- Docker context yerel `application.yml`, env dosyalari, signing p8/p12/jks/keystore ve storage'i disliyor. Docker build application-example.yml kullanir; runtime prod env/secrets gerekli. Deploy oncesi env parity testi ayrica gereklidir.
- Son image `grun-security-local:20261006`, yalniz yerelde. Scanner dogrudan Docker socket veya AWS credentials almadı; salt okunur artifact mount ve rapor/cache dizinleri kullanildi.
- Trivy 0.69.3 pinned digest ile release script'e alindi. Aqua'nin resmi supply-chain bildirimi 0.69.4/0.69.5/0.69.6 ve ilgili latest tag'lerini riskli belirtiyor; bunlar kullanilmadi. Digest pin signature/provenance dogrulamasinin yerine gecmez.

### Secret kapsami ve sinirlar

- Backend tum mevcut Git ref gecmisi: 305 commit, yaklasik 119 MB, 5 match. Eski README/.env.example ve test kaynaklarindaki base64 dummy JWT degerleri. Local .env dev fallback ile eslesiyor; ProductionConfigGuard prod'da bu bilinen fallback'i reddediyor. Gercek prod secret'i AWS'den okunmadi; prod ile eslesmeme guvencesi verilmez.
- Mobil tum mevcut Git ref gecmisi: 60 commit, yaklasik 268 MB, 0 match. Bu detector'un bildigi desenlerde sonuc; secret yoklugunun matematiksel kaniti degildir. Git log rename detection sinir uyarisi verdi; tum ref diff'leri tarandi ama perfect provenance/rename history iddiasi yok.
- Backend yerel worktree/artifact/log taramasi: 524 ham eslesme. Cogu tmp AWS CLI ornekleri ve .codex-deploy test ciktisi. .env icinde provider secret'lari mevcut, Git-ignore ve Docker-ignore; degerler rapor/sohbette aciga cikarilmadi. Secili local log match'leri SQL column adlari ve eski generated security password; aktif token expose bulgusu dogrulanmadi. Tumu 524 gercek sizinti olarak raporlanmaz.
- Mobil yerel tarama: 437 match. 435'i Git-ignore Maestro `.tools` test ciktisi/QA credential ve adb private key; 2'si Git-ignore google-services.json client API key. Bunlar remote prod secret sizintisi kaniti degildir. QA arsivlerine erisim/kopyalama politikasi ve Firebase key application/API restrictions hala dogrulanmali; otomatik silme/rotation yapilmadi.
- Admin son izole JS bundle: yaklasik 4 MB, 0 secret match. APK/IPA/native Gradle/Pod lock artifact bulunamadi; native dependency CVE, signing ve mobil release artifact secret scan henuz yapilamadi.
- Raporlar `outputs/security-review/*-redacted.json`; ham secret degerlerini icermeyen Gitleaks --redact=100 kullanildi. Bu klasor yerel rapordur; remote third-party kaynaklara yukleme yapilmadi.

### Son regresyon ve acik release kapilari

22 backend test sinifi / 256 senaryo: 255 passed, 0 failure/error, 1 Windows normal symlink privilege nedeniyle skipped. Guvenlik, CORS, JWT, IDOR, XSLT guard, SSRF/media, subscription ownership/verification/webhook/kredi ve admin MFA kapsami. Izole H2/mock provider kullandi; bu tur real PostgreSQL/Redis race testleri tekrar edilmedi. Bunlar onceki turdaki toplamlarla toplanmaz.

Admin session/security testleri gecti; tum production test zinciri son asamada existing HEIC chunk 750 KB budget nedeniyle durdu. Eski lazy-view testi pages/modules ile sinirlandi (MFA bootstrap auth view eager kalir), eski quality queue label testi current UI metnine uyarlandi; security yetki assert'leri kaldirilmadi. Production bundle/performance gate basarili sayilmadi. Mobil qa:account API URL olmadigindan calismadi; gercek kullanici olusturma veya remote mutasyon yapilmadi.

Acik kapanis sirasi:

1. Conditional XSLT dependency kaydi icin resmi framework fix/exception karari; gate su anda BLOCKED.
2. Mobil 31 dependency/metavulnerability: SDK/NativeWind uyumlu upgrade, runtime URL payload matrisi, native artifact scan.
3. AWS SSO sonrasi salt okunur ECS/IAM/private storage/TLS/WAF ve task/env parity; local non-root image ortamda uyumlu mu kabul.
4. Admin existing HEIC/parca/bundle budget; local build degisikliklerinin static bundle'a kontrollu aktarimi.
5. Tum endpoint/DTO/service rol ve field matrisi, payment replay genis matrisi, cache isolation, injection payloadlari, dagitik aktif AI concurrency; onceki checklist pending maddeleri korunuyor.
6. Linux symlink, parent-directory TOCTOU capability modeli, raster/EXIF ve gercek TLS/egress/cok-task testleri.
7. MFA recovery runbook, native/device/staging smoke ve release kaynak/image eslesmesi.

Bu nedenle 5-6'nin yerel tarama/uyumlu duzeltme parcasi yapildi; tum guvenlik kapilari kapanmis veya canli release onaylanmis degildir.

Kaynaklar: [Spring 3.5 reference](https://docs.spring.io/spring-boot/3.5/reference/index.html), [Spring CVE-2026-47884](https://spring.io/security/cve-2026-47884/), [Tomcat security](https://tomcat.apache.org/security-10.html), [Jackson 2.21 release](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21), [Aqua scanner incident](https://www.aquasec.com/blog/trivy-supply-chain-attack-what-you-need-to-know/), [Trivy Java coverage](https://www.trivy.dev/docs/latest/coverage/language/java/), [Gitleaks](https://github.com/gitleaks/gitleaks).

## Yontem kaynaklari

- [OWASP API Security Top 10](https://api-security.owasp.org/editions/2023/en/0x11-t10/): yetkilendirme, kimlik ve kaynak tuketimi kapsami.
- [npm audit dokumantasyonu](https://docs.npmjs.com/cli/npm-audit/): audit raporu ve force seceneginin major degisiklik riski.
- [Apache HttpClient connection manager](https://hc.apache.org/components/httpcomponents-client-5.4.x/5.4.4/httpclient5/apidocs/org/apache/hc/client5/http/impl/io/PoolingHttpClientConnectionManagerBuilder.html): baglanti DNS resolver'inin yapilandirilmasi.
