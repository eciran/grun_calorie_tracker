param(
    [string]$BaseReleasePath = ".\outputs\tr-generic-staples-release-v1.csv",
    [string]$CandidatePath = ".\outputs\tr-generic-pantry-usda-candidates-v1.csv",
    [string]$OutputPath = ".\outputs\tr-generic-catalog-v2.csv",
    [string]$ReportPath = ".\outputs\tr-generic-catalog-v2.json"
)

$ErrorActionPreference = "Stop"
$specs = @{
    'dill weed fresh'=@('Fresh Dill','Taze Dereotu','dereotu',10,'PORTION','RAW'); 'mint fresh'=@('Fresh Mint','Taze Nane','nane',10,'PORTION','RAW')
    'leeks raw'=@('Raw Leek','Çiğ Pırasa','pırasa',100,'PORTION','RAW'); 'okra raw'=@('Raw Okra','Çiğ Bamya','bamya',100,'PORTION','RAW')
    'green beans raw'=@('Raw Green Beans','Çiğ Taze Fasulye','taze fasulye',100,'PORTION','RAW'); 'celery raw'=@('Raw Celery','Çiğ Kereviz','kereviz',100,'PORTION','RAW')
    'artichokes raw'=@('Raw Artichoke','Çiğ Enginar','enginar',100,'PORTION','RAW'); 'chard raw'=@('Raw Chard','Çiğ Pazı','pazı',100,'PORTION','RAW')
    'pumpkin raw'=@('Raw Pumpkin','Çiğ Bal Kabağı','bal kabağı',100,'PORTION','RAW'); 'watermelon raw'=@('Raw Watermelon','Karpuz','karpuz',200,'SLICE','RAW')
    'cantaloupe raw'=@('Raw Melon','Kavun','kavun',200,'SLICE','RAW'); 'figs raw'=@('Raw Fig','Taze İncir','incir',50,'PIECE','RAW')
    'apricots raw'=@('Raw Apricot','Taze Kayısı','kayısı',35,'PIECE','RAW'); 'cherries sweet raw'=@('Raw Sweet Cherries','Kiraz','kiraz',100,'PORTION','RAW')
    'pomegranate raw'=@('Raw Pomegranate','Nar','nar',100,'PORTION','RAW'); 'flour wheat all purpose'=@('Wheat Flour','Buğday Unu','un;buğday unu',100,'PORTION','PREPARED')
    'semolina unenriched'=@('Semolina','İrmik','irmik',100,'PORTION','PREPARED'); 'cornmeal whole grain'=@('Whole Grain Cornmeal','Mısır Unu','mısır unu',100,'PORTION','PREPARED')
    'tahini sesame butter'=@('Tahini','Tahin','tahin',15,'TABLESPOON','PREPARED'); 'honey'=@('Honey','Bal','bal',20,'TABLESPOON','PREPARED')
    'molasses'=@('Molasses','Pekmez','pekmez',20,'TABLESPOON','PREPARED'); 'sugar granulated'=@('Granulated Sugar','Toz Şeker','şeker;toz şeker',10,'TABLESPOON','PREPARED')
    'tomato paste canned'=@('Tomato Paste','Domates Salçası','domates salçası;salça',15,'TABLESPOON','PREPARED'); 'cream sour'=@('Sour Cream','Ekşi Krema','ekşi krema',15,'TABLESPOON','PREPARED')
    'egg whole hard boiled'=@('Hard-boiled Egg','Haşlanmış Yumurta','yumurta;haşlanmış yumurta',50,'PIECE','BOILED')
    'beef liver cooked'=@('Cooked Beef Liver','Pişmiş Dana Ciğeri','dana ciğeri;karaciğer',100,'PORTION','COOKED')
    'chicken liver cooked'=@('Cooked Chicken Liver','Pişmiş Tavuk Ciğeri','tavuk ciğeri;karaciğer',100,'PORTION','COOKED')
}

$base = @(Import-Csv -LiteralPath $BaseReleasePath)
$existingKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$base | ForEach-Object { [void]$existingKeys.Add($_.source_key) }
$added = [Collections.Generic.List[object]]::new()
$duplicates = [Collections.Generic.List[string]]::new()
foreach ($source in @(Import-Csv -LiteralPath $CandidatePath)) {
    $query = [string]$source.alias_en
    if (-not $specs.ContainsKey($query)) { throw "Missing pantry specification: $query" }
    if (-not $existingKeys.Add([string]$source.source_key)) { $duplicates.Add([string]$source.source_key); continue }
    $spec = $specs[$query]
    $servingItem = @{label="1 serving $($spec[0])";unitType=$spec[4];quantity=1;gramWeight=$spec[3];mlVolume=$null;defaultOption=$true;labels=@{EN="1 serving $($spec[0])";TR="1 porsiyon $($spec[1])"}}
    $serving = '[' + ($servingItem | ConvertTo-Json -Compress -Depth 6) + ']'
    $added.Add([pscustomobject]@{
        catalog_type='GENERIC_INGREDIENT';data_source='USDA_FOODDATA';source_key=$source.source_key;name=$source.name
        display_name=$spec[0];short_display_name=$spec[0];calories=$source.calories;protein=$source.protein;fat=$source.fat;carbs=$source.carbs
        fiber=$source.fiber;sugar=$source.sugar;sodium=$source.sodium;potassium=$source.potassium;cholesterol=$source.cholesterol;calcium=$source.calcium
        iron=$source.iron;magnesium=$source.magnesium;zinc=$source.zinc;vitamin_a=$source.vitamin_a;vitamin_c=$source.vitamin_c;vitamin_d=$source.vitamin_d
        vitamin_e=$source.vitamin_e;vitamin_b12=$source.vitamin_b12;serving_size_grams=$spec[3];serving_unit='g';market_region='GLOBAL'
        preparation_state=$spec[5];nutrition_basis='SOURCE_REPORTED';alias_en=$query;alias_tr=$spec[2];source_note=$source.source_note
        display_name_en=$spec[0];short_display_name_en=$spec[0];display_name_tr=$spec[1];short_display_name_tr=$spec[1];serving_options_json=$serving
    })
}
$all = @($base + $added)
if (@($all | Group-Object source_key | Where-Object Count -gt 1).Count) { throw 'Duplicate source key in output.' }
$all | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
$report = [ordered]@{status='PASS';rows=$all.Count;baseRows=$base.Count;addedRows=$added.Count;alreadyPresentRows=$duplicates.Count;alreadyPresentSourceKeys=@($duplicates);source='USDA FoodData Central';outputSha256=(Get-FileHash $OutputPath -Algorithm SHA256).Hash}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ReportPath -Encoding utf8
$report | ConvertTo-Json -Depth 5
