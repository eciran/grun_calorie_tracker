ALTER TABLE achievement_definitions
    ADD COLUMN IF NOT EXISTS title_tr VARCHAR(120),
    ADD COLUMN IF NOT EXISTS description_tr VARCHAR(500);

UPDATE achievement_definitions
SET title_tr = localized.title_tr,
    description_tr = localized.description_tr
FROM (VALUES
    ('PROFILE_COMPLETED', 'Profil Tamamlandı', 'Kişiselleştirilmiş hedefler için gereken temel profil alanlarını tamamla.'),
    ('FIRST_GOAL_SET', 'İlk Hedef Belirlendi', 'İlk kalori ve makro hedefini oluştur.'),
    ('FIRST_MEAL_LOGGED', 'İlk Öğün Kaydedildi', 'İlk yiyecek kaydını ekle.'),
    ('THREE_MEALS_DAY', 'Tam Gün Kaydedildi', 'Kahvaltı, öğle ve akşam öğünlerini aynı gün kaydet.'),
    ('SEVEN_DAY_FOOD_STREAK', '7 Günlük Beslenme Serisi', 'Yedi farklı günde yiyecek kaydı ekle.'),
    ('FIRST_WORKOUT_LOGGED', 'İlk Antrenman Kaydedildi', 'İlk egzersizini kaydet.'),
    ('WEEKLY_BURN_1000', 'Haftalık 1000 Kalori', 'Son yedi günde egzersizle 1000 kcal yak.'),
    ('FIRST_FAST_COMPLETED', 'İlk Oruç Tamamlandı', 'İlk oruç seansını tamamla.'),
    ('SEVEN_DAY_FASTING_STREAK', '7 Günlük Oruç Serisi', 'Yedi farklı günde oruç seansı tamamla.'),
    ('FIRST_WEIGHT_LOGGED', 'İlk Kilo Kaydedildi', 'İlk ilerleme kaydını ekle.'),
    ('WEIGHT_PROGRESS_1KG', '1 kg İlerleme', 'Başlangıç kilondan hedefine doğru en az 1 kg ilerle.'),
    ('FIRST_WATER_LOGGED', 'İlk Su Kaydı', 'İlk su tüketimini kaydet.'),
    ('WATER_TARGET_HIT', 'Su Hedefine Ulaşıldı', 'Günlük su hedefine en az bir kez ulaş.')
) AS localized(code, title_tr, description_tr)
WHERE achievement_definitions.code = localized.code;
