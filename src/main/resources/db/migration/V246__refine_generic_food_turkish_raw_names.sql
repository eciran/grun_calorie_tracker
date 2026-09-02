-- Refines Turkish user-facing names without changing preparation state or nutrition.
-- Existing curator changes are preserved unless they still equal the old generated name.
WITH approved(source_key, old_display_name, old_short_display_name, display_name, short_display_name) AS (VALUES
    ('USDA_FOODDATA:fdc:1105314', 'Çiğ Muz', 'Çiğ Muz', 'Muz', 'Muz'),
    ('USDA_FOODDATA:fdc:1750340', 'Çiğ Elma', 'Çiğ Elma', 'Elma', 'Elma'),
    ('USDA_FOODDATA:fdc:746771', 'Çiğ Portakal', 'Çiğ Portakal', 'Portakal', 'Portakal'),
    ('USDA_FOODDATA:fdc:2710832', 'Çiğ Mandalina', 'Çiğ Mandalina', 'Mandalina', 'Mandalina'),
    ('USDA_FOODDATA:fdc:2346413', 'Çiğ Üzüm', 'Çiğ Üzüm', 'Üzüm', 'Üzüm'),
    ('USDA_FOODDATA:fdc:2346409', 'Çiğ Çilek', 'Çiğ Çilek', 'Çilek', 'Çilek'),
    ('USDA_FOODDATA:fdc:2346411', 'Çiğ Yaban Mersini', 'Çiğ Yaban Mersini', 'Yaban Mersini', 'Yaban Mersini'),
    ('USDA_FOODDATA:fdc:2346410', 'Çiğ Ahududu', 'Çiğ Ahududu', 'Ahududu', 'Ahududu'),
    ('USDA_FOODDATA:fdc:173946', 'Çiğ Böğürtlen', 'Çiğ Böğürtlen', 'Böğürtlen', 'Böğürtlen'),
    ('USDA_FOODDATA:fdc:168177', 'Çiğ Armut', 'Çiğ Armut', 'Armut', 'Armut'),
    ('USDA_FOODDATA:fdc:325430', 'Çiğ Şeftali', 'Çiğ Şeftali', 'Şeftali', 'Şeftali'),
    ('USDA_FOODDATA:fdc:169949', 'Çiğ Erik', 'Çiğ Erik', 'Erik', 'Erik'),
    ('USDA_FOODDATA:fdc:2346398', 'Çiğ Ananas', 'Çiğ Ananas', 'Ananas', 'Ananas'),
    ('USDA_FOODDATA:fdc:2710834', 'Çiğ Mango', 'Çiğ Mango', 'Mango', 'Mango'),
    ('USDA_FOODDATA:fdc:2710831', 'Çiğ Kivi', 'Çiğ Kivi', 'Kivi', 'Kivi'),
    ('USDA_FOODDATA:fdc:747447', 'Çiğ Brokoli', 'Çiğ Brokoli', 'Brokoli', 'Brokoli'),
    ('USDA_FOODDATA:fdc:2685573', 'Çiğ Karnabahar', 'Çiğ Karnabahar', 'Karnabahar', 'Karnabahar'),
    ('USDA_FOODDATA:fdc:168462', 'Çiğ Ispanak', 'Çiğ Ispanak', 'Ispanak', 'Ispanak'),
    ('USDA_FOODDATA:fdc:323505', 'Çiğ Kara Lahana', 'Çiğ Kara Lahana', 'Kara Lahana', 'Kara Lahana'),
    ('USDA_FOODDATA:fdc:2346388', 'Çiğ Marul', 'Çiğ Marul', 'Marul', 'Marul'),
    ('USDA_FOODDATA:fdc:169975', 'Çiğ Lahana', 'Çiğ Lahana', 'Lahana', 'Lahana'),
    ('USDA_FOODDATA:fdc:2685575', 'Çiğ Brüksel Lahanası', 'Çiğ Brüksel Lahanası', 'Brüksel Lahanası', 'Brüksel Lahanası'),
    ('USDA_FOODDATA:fdc:169303', 'Çiğ Patates', 'Çiğ Patates', 'Patates', 'Patates'),
    ('USDA_FOODDATA:fdc:168482', 'Çiğ Tatlı Patates', 'Çiğ Tatlı Patates', 'Tatlı Patates', 'Tatlı Patates'),
    ('USDA_FOODDATA:fdc:2258587', 'Çiğ Havuç', 'Çiğ Havuç', 'Havuç', 'Havuç'),
    ('USDA_FOODDATA:fdc:2685576', 'Çiğ Pancar', 'Çiğ Pancar', 'Pancar', 'Pancar'),
    ('USDA_FOODDATA:fdc:2747659', 'Çiğ Yaban Havucu', 'Çiğ Yaban Havucu', 'Yaban Havucu', 'Yaban Havucu'),
    ('USDA_FOODDATA:fdc:790577', 'Çiğ Soğan', 'Çiğ Soğan', 'Soğan', 'Soğan'),
    ('USDA_FOODDATA:fdc:1104647', 'Çiğ Sarımsak', 'Çiğ Sarımsak', 'Sarımsak', 'Sarımsak'),
    ('USDA_FOODDATA:fdc:321360', 'Çiğ Domates', 'Çiğ Domates', 'Domates', 'Domates'),
    ('USDA_FOODDATA:fdc:2346406', 'Çiğ Salatalık', 'Çiğ Salatalık', 'Salatalık', 'Salatalık'),
    ('USDA_FOODDATA:fdc:2258590', 'Çiğ Kırmızı Biber', 'Çiğ Kırmızı Biber', 'Kırmızı Biber', 'Kırmızı Biber'),
    ('USDA_FOODDATA:fdc:2685577', 'Çiğ Patlıcan', 'Çiğ Patlıcan', 'Patlıcan', 'Patlıcan'),
    ('USDA_FOODDATA:fdc:168565', 'Çiğ Kabak', 'Çiğ Kabak', 'Kabak', 'Kabak'),
    ('USDA_FOODDATA:fdc:169251', 'Çiğ Mantar', 'Çiğ Mantar', 'Mantar', 'Mantar'),
    ('USDA_FOODDATA:fdc:174640', 'Çiğ Yulaf', 'Çiğ Yulaf', 'Yulaf', 'Yulaf'),
    ('USDA_FOODDATA:fdc:2512381', 'Çiğ Beyaz Pirinç', 'Çiğ Beyaz Pirinç', 'Beyaz Pirinç', 'Beyaz Pirinç'),
    ('USDA_FOODDATA:fdc:2512380', 'Çiğ Esmer Pirinç', 'Çiğ Esmer Pirinç', 'Esmer Pirinç', 'Esmer Pirinç'),
    ('USDA_FOODDATA:fdc:169736', 'Çiğ Makarna', 'Çiğ Makarna', 'Makarna', 'Makarna'),
    ('USDA_FOODDATA:fdc:168874', 'Çiğ Kinoa', 'Çiğ Kinoa', 'Kinoa', 'Kinoa'),
    ('USDA_FOODDATA:fdc:169699', 'Çiğ Kuskus', 'Çiğ Kuskus', 'Kuskus', 'Kuskus'),
    ('USDA_FOODDATA:fdc:170284', 'Çiğ Arpa', 'Çiğ Arpa', 'Arpa', 'Arpa'),
    ('USDA_FOODDATA:fdc:2710820', 'Çiğ Bulgur', 'Çiğ Bulgur', 'Bulgur', 'Bulgur'),
    ('USDA_FOODDATA:fdc:172420', 'Çiğ Mercimek', 'Çiğ Mercimek', 'Mercimek', 'Mercimek'),
    ('USDA_FOODDATA:fdc:173756', 'Çiğ Nohut', 'Çiğ Nohut', 'Nohut', 'Nohut'),
    ('USDA_FOODDATA:fdc:175193', 'Çiğ Kırmızı Fasulye', 'Çiğ Kırmızı Fasulye', 'Kırmızı Fasulye', 'Kırmızı Fasulye'),
    ('USDA_FOODDATA:fdc:175186', 'Çiğ Siyah Fasulye', 'Çiğ Siyah Fasulye', 'Siyah Fasulye', 'Siyah Fasulye'),
    ('USDA_FOODDATA:fdc:173749', 'Çiğ Kuru Fasulye', 'Çiğ Kuru Fasulye', 'Kuru Fasulye', 'Kuru Fasulye'),
    ('USDA_FOODDATA:fdc:170419', 'Çiğ Bezelye', 'Çiğ Bezelye', 'Bezelye', 'Bezelye'),
    ('USDA_FOODDATA:fdc:174270', 'Çiğ Soya Fasulyesi', 'Çiğ Soya Fasulyesi', 'Soya Fasulyesi', 'Soya Fasulyesi'),
    ('USDA_FOODDATA:fdc:169098', 'Çiğ Portakal Suyu', 'Çiğ Portakal Suyu', 'Portakal Suyu', 'Portakal Suyu'),
    ('USDA_FOODDATA:fdc:169246', 'Çiğ Pırasa', 'Çiğ Pırasa', 'Pırasa', 'Pırasa'),
    ('USDA_FOODDATA:fdc:169260', 'Çiğ Bamya', 'Çiğ Bamya', 'Bamya', 'Bamya'),
    ('USDA_FOODDATA:fdc:2346400', 'Çiğ Taze Fasulye', 'Çiğ Taze Fasulye', 'Taze Fasulye', 'Taze Fasulye'),
    ('USDA_FOODDATA:fdc:2346405', 'Çiğ Kereviz', 'Çiğ Kereviz', 'Kereviz', 'Kereviz'),
    ('USDA_FOODDATA:fdc:169205', 'Çiğ Enginar', 'Çiğ Enginar', 'Enginar', 'Enginar'),
    ('USDA_FOODDATA:fdc:169991', 'Çiğ Pazı', 'Çiğ Pazı', 'Pazı', 'Pazı'),
    ('USDA_FOODDATA:fdc:168448', 'Çiğ Bal Kabağı', 'Çiğ Bal Kabağı', 'Bal Kabağı', 'Bal Kabağı')
), matched AS (
    SELECT f.id AS food_item_id, a.*
    FROM approved a
    JOIN food_items f ON f.source_key = a.source_key
    WHERE f.catalog_type = 'GENERIC_INGREDIENT'
)
INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT food_item_id, 'TR', display_name, short_display_name, 'generic_catalog_naming_v246'
FROM matched
ON CONFLICT (food_item_id, language) DO UPDATE
SET display_name = EXCLUDED.display_name,
    short_display_name = EXCLUDED.short_display_name,
    source = EXCLUDED.source,
    updated_at = CURRENT_TIMESTAMP
WHERE food_item_localizations.display_name = (
          SELECT old_display_name FROM matched WHERE matched.food_item_id = food_item_localizations.food_item_id
      )
  AND coalesce(food_item_localizations.short_display_name, food_item_localizations.display_name) = (
          SELECT old_short_display_name FROM matched WHERE matched.food_item_id = food_item_localizations.food_item_id
      );
