# TR-BULK-SOURCE-DISCOVERY-20260807T0049Z

- Stage: `TR-BULK-RETAILER-GROWTH`
- Result: `PASS_BULK_SOURCE_CAPACITY`
- Scope: robots/sitemap and frozen-index discovery only; no product catalog, database or production mutation.

## Commands

```powershell
$urls=@('https://www.a101.com.tr/robots.txt','https://www.ozdilekteyim.com/robots.txt','https://www.sokmarket.com.tr/robots.txt','https://www.migros.com.tr/robots.txt','https://www.carrefoursa.com/robots.txt','https://www.macrocenter.com.tr/robots.txt')
foreach($u in $urls){ curl.exe -L --max-time 15 -sS -o NUL -w '%{http_code} %{size_download}' $u }
curl.exe -L --max-time 15 -sS https://www.a101.com.tr/robots.txt
curl.exe -L --max-time 15 -sS https://www.ozdilekteyim.com/robots.txt
curl.exe -L --max-time 15 -sS https://www.sokmarket.com.tr/robots.txt
```

The two sitemap roots were fetched, every child `<loc>` was enumerated, and each child sitemap was counted with bounded 30-second requests:

```powershell
https://www.ozdilekteyim.com/medias2/sitemap.xml
https://www.sokmarket.com.tr/sitemap/sitemap.xml
```

Frozen source capacity was read from:

```powershell
Get-Content .\outputs\TR_Products\bulk-barcode-reconciliation\source-index.json -Raw | ConvertFrom-Json
```

## Results

- Özdilekteyim: robots 200, product paths allowed, 25 child sitemaps, 69,216 total URLs, 14,061 product-like URLs, 0 failed children. Existing frozen identities: 11,397.
- Cepte Şok: robots 200, only `/arama` disallowed, four child sitemaps, 20,660 market-product + 2,419 extra-product URLs, 0 failed children.
- A101: robots 200, normal product paths allowed; 4,483 frozen product identities already available.
- Immediately reachable non-deduplicated bulk capacity: 41,623 product URLs/identities.
- Migros, CarrefourSA and Macrocenter robots endpoints returned 403 to the command-line client. Automated crawl is deferred; existing frozen URLs and search-index evidence remain usable without bypass attempts.
- Sample indexed pages confirm structured name/package and per-100 nutrition on all three reachable families.
- Parsed products/exact joins/net-new in this discovery slice: 0/0/0. Achieved rich total remains 2,416; four ETİ candidates remain pending v7; 7,584 remain to 10,000.

## Hash

- Discovery report: `360F91CA13578F3412575CE02865E186B5F9635B176B44C3E8D6E4B6D71565EA`

## Next

Implement a checkpointed parser and run the first bounded bulk batch against allowed Özdilekteyim and Cepte Şok product URLs. Normalize per-100 core nutrition and exact-join by GTIN to the 3,682 nutrition no-match queue.
