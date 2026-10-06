CREATE TEMP TABLE approved_usda_serving_profiles (
    source_key VARCHAR(255) NOT NULL,
    label VARCHAR(120) NOT NULL,
    unit_type VARCHAR(40) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    gram_weight DOUBLE PRECISION NOT NULL,
    default_option BOOLEAN NOT NULL,
    label_en VARCHAR(120) NOT NULL,
    label_tr VARCHAR(120) NOT NULL
) ON COMMIT DROP;

INSERT INTO approved_usda_serving_profiles VALUES
('USDA_FOODDATA:fdc:1105314','1 medium banana','PIECE',1,118,TRUE,'1 medium banana','1 orta boy muz'),
('USDA_FOODDATA:fdc:1105314','1 small banana','PIECE',1,101,FALSE,'1 small banana','1 küçük boy muz'),
('USDA_FOODDATA:fdc:1105314','1 large banana','PIECE',1,136,FALSE,'1 large banana','1 büyük boy muz'),
('USDA_FOODDATA:fdc:746771','1 orange','PIECE',1,140,TRUE,'1 orange','1 portakal'),
('USDA_FOODDATA:fdc:746771','1 cup orange sections','CUP',1,165,FALSE,'1 cup orange sections','1 su bardağı portakal dilimi'),
('USDA_FOODDATA:fdc:325430','1 small peach','PIECE',1,147,FALSE,'1 small peach','1 küçük boy şeftali'),
('USDA_FOODDATA:fdc:325430','1 medium peach','PIECE',1,161,TRUE,'1 medium peach','1 orta boy şeftali'),
('USDA_FOODDATA:fdc:325430','1 large peach','PIECE',1,173,FALSE,'1 large peach','1 büyük boy şeftali'),
('USDA_FOODDATA:fdc:325430','1 extra large peach','PIECE',1,225,FALSE,'1 extra large peach','1 çok büyük şeftali'),
('USDA_FOODDATA:fdc:325430','1 cup peach slices','CUP',1,154,FALSE,'1 cup peach slices','1 su bardağı şeftali dilimi'),
('USDA_FOODDATA:fdc:169949','1 plum','PIECE',1,66,TRUE,'1 plum','1 erik'),
('USDA_FOODDATA:fdc:169949','1 cup sliced plum','CUP',1,165,FALSE,'1 cup sliced plum','1 su bardağı dilimlenmiş erik'),
('USDA_FOODDATA:fdc:168482','1 sweet potato (5 inch)','PIECE',1,130,TRUE,'1 sweet potato (5 inch)','1 adet tatlı patates (13 cm)'),
('USDA_FOODDATA:fdc:168482','1 cup sweet potato cubes','CUP',1,133,FALSE,'1 cup sweet potato cubes','1 su bardağı küp tatlı patates'),
('USDA_FOODDATA:fdc:321360','5 grape tomatoes','PIECE',5,49.7,TRUE,'5 grape tomatoes','5 adet üzüm domates'),
('USDA_FOODDATA:fdc:321360','1 cup grape tomatoes','CUP',1,152,FALSE,'1 cup grape tomatoes','1 su bardağı üzüm domates'),
('USDA_FOODDATA:fdc:790577','1 edible red onion','PIECE',1,197,TRUE,'1 edible red onion','1 adet ayıklanmış kırmızı soğan'),
('USDA_FOODDATA:fdc:168565','1 medium baby courgette','PIECE',1,11,TRUE,'1 medium baby courgette','1 orta boy bebek kabak'),
('USDA_FOODDATA:fdc:168565','1 large baby courgette','PIECE',1,16,FALSE,'1 large baby courgette','1 büyük boy bebek kabak'),
('USDA_FOODDATA:fdc:170001','1 small cooked onion','PIECE',1,60,FALSE,'1 small cooked onion','1 küçük pişmiş soğan'),
('USDA_FOODDATA:fdc:170001','1 medium cooked onion','PIECE',1,94,TRUE,'1 medium cooked onion','1 orta boy pişmiş soğan'),
('USDA_FOODDATA:fdc:170001','1 large cooked onion','PIECE',1,128,FALSE,'1 large cooked onion','1 büyük pişmiş soğan'),
('USDA_FOODDATA:fdc:170001','1 cup cooked onion','CUP',1,210,FALSE,'1 cup cooked onion','1 su bardağı pişmiş soğan'),
('USDA_FOODDATA:fdc:170001','1 tablespoon chopped cooked onion','TABLESPOON',1,15,FALSE,'1 tablespoon chopped cooked onion','1 yemek kaşığı doğranmış pişmiş soğan'),
('USDA_FOODDATA:fdc:170440','1 small boiled peeled potato','PIECE',1,125,FALSE,'1 small boiled peeled potato','1 küçük haşlanmış kabuksuz patates'),
('USDA_FOODDATA:fdc:170440','1 medium boiled peeled potato','PIECE',1,167,TRUE,'1 medium boiled peeled potato','1 orta boy haşlanmış kabuksuz patates'),
('USDA_FOODDATA:fdc:170440','1 large boiled peeled potato','PIECE',1,300,FALSE,'1 large boiled peeled potato','1 büyük haşlanmış kabuksuz patates'),
('USDA_FOODDATA:fdc:170440','1/2 cup boiled peeled potato','CUP',0.5,78,FALSE,'1/2 cup boiled peeled potato','1/2 su bardağı haşlanmış kabuksuz patates'),
('USDA_FOODDATA:fdc:171287','1 small raw egg','PIECE',1,38,FALSE,'1 small raw egg','1 küçük boy çiğ yumurta'),
('USDA_FOODDATA:fdc:171287','1 medium raw egg','PIECE',1,44,FALSE,'1 medium raw egg','1 orta boy çiğ yumurta'),
('USDA_FOODDATA:fdc:171287','1 large raw egg','PIECE',1,50,TRUE,'1 large raw egg','1 büyük boy çiğ yumurta'),
('USDA_FOODDATA:fdc:171287','1 extra large raw egg','PIECE',1,56,FALSE,'1 extra large raw egg','1 çok büyük çiğ yumurta'),
('USDA_FOODDATA:fdc:171287','1 jumbo raw egg','PIECE',1,63,FALSE,'1 jumbo raw egg','1 jumbo çiğ yumurta'),
('USDA_FOODDATA:fdc:171287','1 cup raw egg','CUP',1,243,FALSE,'1 cup raw egg','1 su bardağı çiğ yumurta'),
('USDA_FOODDATA:fdc:172187','1 large cooked egg','PIECE',1,61,TRUE,'1 large cooked egg','1 büyük pişmiş yumurta'),
('USDA_FOODDATA:fdc:172187','1 cup cooked egg','CUP',1,220,FALSE,'1 cup cooked egg','1 su bardağı pişmiş yumurta'),
('USDA_FOODDATA:fdc:172187','1 tablespoon cooked egg','TABLESPOON',1,13.7,FALSE,'1 tablespoon cooked egg','1 yemek kaşığı pişmiş yumurta'),
('USDA_FOODDATA:fdc:173424','1 large hard-boiled egg','PIECE',1,50,TRUE,'1 large hard-boiled egg','1 büyük haşlanmış yumurta'),
('USDA_FOODDATA:fdc:173424','1 cup chopped hard-boiled egg','CUP',1,136,FALSE,'1 cup chopped hard-boiled egg','1 su bardağı doğranmış haşlanmış yumurta'),
('USDA_FOODDATA:fdc:173424','1 tablespoon hard-boiled egg','TABLESPOON',1,8.5,FALSE,'1 tablespoon hard-boiled egg','1 yemek kaşığı haşlanmış yumurta'),
('USDA_FOODDATA:fdc:170394','1 cooked carrot','PIECE',1,46,TRUE,'1 cooked carrot','1 adet pişmiş havuç'),
('USDA_FOODDATA:fdc:170394','1/2 cup cooked carrot slices','CUP',0.5,78,FALSE,'1/2 cup cooked carrot slices','1/2 su bardağı pişmiş havuç dilimi'),
('USDA_FOODDATA:fdc:170394','1 tablespoon cooked carrot','TABLESPOON',1,9.7,FALSE,'1 tablespoon cooked carrot','1 yemek kaşığı pişmiş havuç'),
('USDA_FOODDATA:fdc:170050','2 medium cooked tomatoes','PIECE',2,246,TRUE,'2 medium cooked tomatoes','2 orta boy pişmiş domates'),
('USDA_FOODDATA:fdc:170050','1 cup cooked tomato','CUP',1,240,FALSE,'1 cup cooked tomato','1 su bardağı pişmiş domates'),
('USDA_FOODDATA:fdc:170033','1 baked potato','PIECE',1,156,TRUE,'1 baked potato','1 adet fırınlanmış patates'),
('USDA_FOODDATA:fdc:170033','1/2 cup baked potato','CUP',0.5,61,FALSE,'1/2 cup baked potato','1/2 su bardağı fırınlanmış patates'),
('USDA_FOODDATA:fdc:169146','2 cooked beetroots','PIECE',2,100,TRUE,'2 cooked beetroots','2 adet pişmiş pancar'),
('USDA_FOODDATA:fdc:169146','1/2 cup cooked beetroot slices','CUP',0.5,85,FALSE,'1/2 cup cooked beetroot slices','1/2 su bardağı pişmiş pancar dilimi'),
('USDA_FOODDATA:fdc:169292','1/2 cup mashed cooked courgette','CUP',0.5,120,TRUE,'1/2 cup mashed cooked courgette','1/2 su bardağı ezilmiş pişmiş kabak'),
('USDA_FOODDATA:fdc:169292','1 cup sliced cooked courgette','CUP',1,180,FALSE,'1 cup sliced cooked courgette','1 su bardağı dilimlenmiş pişmiş kabak'),
('USDA_FOODDATA:fdc:170009','1 cooked parsnip','PIECE',1,160,TRUE,'1 cooked parsnip','1 adet pişmiş yaban havucu'),
('USDA_FOODDATA:fdc:170009','1/2 cup cooked parsnip slices','CUP',0.5,78,FALSE,'1/2 cup cooked parsnip slices','1/2 su bardağı pişmiş yaban havucu'),
('USDA_FOODDATA:fdc:168177','1 small Asian pear','PIECE',1,122,TRUE,'1 small Asian pear','1 küçük Asya armudu'),
('USDA_FOODDATA:fdc:168177','1 large Asian pear','PIECE',1,275,FALSE,'1 large Asian pear','1 büyük Asya armudu'),
('USDA_FOODDATA:fdc:169304','1 cup cooked sweet potato leaves','CUP',1,64,TRUE,'1 cup cooked sweet potato leaves','1 su bardağı pişmiş tatlı patates yaprağı'),
('USDA_FOODDATA:fdc:170380','1/2 cup boiled broccoli spears','CUP',0.5,92,TRUE,'1/2 cup boiled broccoli spears','1/2 su bardağı haşlanmış brokoli sapı'),
('USDA_FOODDATA:fdc:170380','1 cooked package broccoli spears','PACKAGE',1,250,FALSE,'1 cooked package broccoli spears','1 paket pişmiş brokoli sapı'),
('USDA_FOODDATA:fdc:170439','1 boiled potato skin','PIECE',1,34,TRUE,'1 boiled potato skin','1 adet haşlanmış patates kabuğu'),
('USDA_FOODDATA:fdc:170519','1 salted boiled potato skin','PIECE',1,34,TRUE,'1 salted boiled potato skin','1 adet tuzlu haşlanmış patates kabuğu');

-- Remove unsupported options from the mobile-visible set before rebuilding each frozen profile.
UPDATE food_item_serving_options option
SET quality_status = 'NEEDS_REVIEW', is_default = FALSE
FROM food_items item
WHERE option.food_item_id = item.id
  AND item.source_key IN (SELECT DISTINCT source_key FROM approved_usda_serving_profiles)
  AND NOT EXISTS (
      SELECT 1 FROM approved_usda_serving_profiles approved
      WHERE approved.source_key = item.source_key
        AND approved.unit_type = option.unit_type
        AND abs(approved.quantity - option.quantity) < 0.000001
        AND abs(approved.gram_weight - option.gram_weight) < 0.000001
  );

-- Normalize any matching option rather than creating a second identity.
UPDATE food_item_serving_options option
SET label = approved.label,
    is_default = FALSE,
    source = 'ADMIN',
    quality_status = 'VERIFIED'
FROM food_items item, approved_usda_serving_profiles approved
WHERE option.food_item_id = item.id
  AND approved.source_key = item.source_key
  AND approved.unit_type = option.unit_type
  AND abs(approved.quantity - option.quantity) < 0.000001
  AND abs(approved.gram_weight - option.gram_weight) < 0.000001;

INSERT INTO food_item_serving_options (
    food_item_id, label, unit_type, quantity, gram_weight, ml_volume,
    is_default, source, quality_status
)
SELECT item.id, approved.label, approved.unit_type, approved.quantity,
       approved.gram_weight, NULL, FALSE, 'ADMIN', 'VERIFIED'
FROM food_items item
JOIN approved_usda_serving_profiles approved ON approved.source_key = item.source_key
WHERE NOT EXISTS (
    SELECT 1 FROM food_item_serving_options existing
    WHERE existing.food_item_id = item.id
      AND existing.unit_type = approved.unit_type
      AND abs(existing.quantity - approved.quantity) < 0.000001
      AND abs(existing.gram_weight - approved.gram_weight) < 0.000001
);

UPDATE food_item_serving_options option
SET is_default = approved.default_option
FROM food_items item, approved_usda_serving_profiles approved
WHERE option.food_item_id = item.id
  AND approved.source_key = item.source_key
  AND approved.unit_type = option.unit_type
  AND abs(approved.quantity - option.quantity) < 0.000001
  AND abs(approved.gram_weight - option.gram_weight) < 0.000001;

INSERT INTO food_item_serving_option_localizations (
    serving_option_id, language, label, source, active, created_at, updated_at
)
SELECT option.id, localized.language, localized.label, 'migration-v304', TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM food_items item
JOIN approved_usda_serving_profiles approved ON approved.source_key = item.source_key
JOIN food_item_serving_options option
  ON option.food_item_id = item.id
 AND option.unit_type = approved.unit_type
 AND abs(option.quantity - approved.quantity) < 0.000001
 AND abs(option.gram_weight - approved.gram_weight) < 0.000001
CROSS JOIN LATERAL (
    VALUES ('EN', approved.label_en), ('TR', approved.label_tr)
) localized(language, label)
ON CONFLICT (serving_option_id, language)
DO UPDATE SET label = EXCLUDED.label, source = EXCLUDED.source,
              active = TRUE, updated_at = CURRENT_TIMESTAMP;

-- Explicitly quarantine known invalid source/identity options that cannot be replaced safely.
UPDATE food_item_serving_options option
SET quality_status = 'NEEDS_REVIEW', is_default = FALSE
FROM food_items item
WHERE option.food_item_id = item.id
  AND item.source_key IN (
      'USDA_FOODDATA:fdc:169303', -- Sweet potato leaves incorrectly represented as Raw Potato.
      'USDA_FOODDATA:fdc:170049', -- Source publishes no portion conversion.
      'USDA_FOODDATA:fdc:2747659' -- Source publishes no portion conversion.
  );

