# TR-BULK-TORKU-PDF-20260807T1600Z

Result: `PASS_SOURCE_AUDIT_STALE_PDF_NO_NUTRITION`

The only official Torku PDF URL in the 200-response sitemap was requested using a `.part` target and non-empty gate, but returned the site's 404 page; no PDF artifact was accepted or renamed. Per the same-run fallback rule, eight official Torku product-category pages were fetched. All eight returned HTTP 200 (402,406 bytes total) and contained product markup, but zero pages exposed `kcal`, barcode/GTIN, or nutrition field terms. Therefore no exact join or nutrition candidate can be formed from this official source. No login, CAPTCHA or access-control bypass was attempted.

Commands: `Invoke-WebRequest -Uri https://torku.com.tr/Upload/Contents/TORKU-EDT-URUN-KATALOGU-2022.pdf -OutFile ...pdf.part` (404; part rejected); bounded `Invoke-WebRequest` checks for 8 official Torku category URLs; exact term counts for kcal, barkod/barcode/gtin, besin/protein/karbonhidrat.

Counts: PDF URLs 1; PDF downloads 0; category pages scanned/fetched 8/8; bytes 402,406; pages with kcal 0; barcode/GTIN 0; nutrition terms 0; exact joins 0; valid GTINs 0; nutrition-complete 0; collisions/reject promotions/net-new 0. Achieved v9 2,867; 7,133 remaining.

Report SHA-256: `DA54B60D8EE7529768135ADB792F70EC9E49F0D115522CD2602E2DB8B338B1AA`.

Next: stop the exhausted Torku official-source path; generate a high-volume exact-GTIN query/index batch for Paketbilgisi/neyvar and other indexed product-detail sources, then fetch only exact result URLs with per-100 evidence.
