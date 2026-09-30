package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodCategoryTurkishLocalizationMigrationContractTest {

    @Test
    void migrationUsesNaturalTurkishLabelsAndCharacters() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V295__localize_food_category_turkish_labels.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(migration.contains("('meat-poultry', 'Et ve Tavuk')"));
        assertTrue(migration.contains("'Süt Ürünleri ve Yumurta'"));
        assertTrue(migration.contains("'Atıştırmalıklar ve Tatlılar'"));
        assertTrue(migration.contains("'Soslar, Çeşniler ve Sürülebilir Ürünler'"));
        assertTrue(migration.contains("'Çikolata ve Şekerlemeler'"));
        assertTrue(migration.contains("updated_by = 'migration-v295'"));
    }
}
