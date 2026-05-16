# Next Backend Sprint Options

Bu dokuman demo/local akisi toparlandiktan sonra secilebilecek backend sprint hedeflerini karsilastirir.

## Mevcut Durum

Backend artik lokal ortamda Swagger uzerinden demo edilebilir durumda:

- Local admin bootstrap
- Local demo user
- Verified demo food products
- Food/exercise demo logs
- Dashboard daily summary
- Admin review queue
- Admin review audit history
- Local demo cleanup

Bu noktadan sonra yeni sprint secimi urun riskine gore yapilmali.

## Secenek 1 - Mail ve Password Reset

### Kapsam

- Password reset token modeli.
- Reset request endpointi.
- Reset confirm endpointi.
- Mail provider abstraction.
- Local/dev mode mail loglama veya fake sender.

### Neden Onemli

Mobil app publish edilecekse kullanici hesap kurtarma temel beklentidir.

### Risk

- Mail provider secimi.
- Token expiration ve security detaylari.

### Tahmini Oncelik

Yuksek.

## Secenek 2 - i18n TR/ENG

### Kapsam

- Error message kaynak dosyalari.
- `Accept-Language` destegi.
- Validation mesajlarinin lokalizasyonu.

### Neden Onemli

TR/ENG destekli mobil app hedefi icin gereklidir.

### Risk

- Mevcut exception mesajlari daginiksa refactor gerekir.

### Tahmini Oncelik

Orta-Yuksek.

## Secenek 3 - Google/Apple Login

### Kapsam

- OAuth/social identity modeli.
- Google token verification.
- Apple identity verification.
- Existing user linking stratejisi.

### Neden Onemli

Mobil app login friction azaltir.

### Risk

- Apple/Google developer setup.
- Token verification detaylari.
- Account merge/link kararları.

### Tahmini Oncelik

Orta.

## Secenek 4 - Admin Panel API Genisletmesi

### Kapsam

- Admin dashboard summary endpointleri.
- Review queue metrics.
- User count/activity summary.
- Product quality metrics.

### Neden Onemli

Food catalog kalitesi admin surecine bagli. UI sonra gelse bile API hazir olmalidir.

### Risk

- Metrik tanimlari erken degisebilir.

### Tahmini Oncelik

Orta.

## Onerilen Siralama

1. Mail/password reset.
2. i18n altyapisi.
3. Admin panel API metrikleri.
4. Google/Apple login.

## Kisa Karar

Bir sonraki backend sprint icin en dengeli secim `Mail ve Password Reset` isidir.

Gerekce:

- Mobil app icin temel hesap guvenligi ihtiyacidir.
- Backend-only ilerlenebilir.
- UI beklemeden Swagger ve testlerle dogrulanabilir.
- Production hazirlik seviyesini dogrudan artirir.
