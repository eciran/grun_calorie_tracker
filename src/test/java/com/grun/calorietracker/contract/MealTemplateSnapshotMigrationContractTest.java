package com.grun.calorietracker.contract;

import com.grun.calorietracker.entity.MealTemplateItemEntity;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MealTemplateSnapshotMigrationContractTest {
    @Test
    void migrationBackfillsAllMappedSnapshotsAndAllowsNonCatalogItems() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V258__snapshot_meal_template_items.sql"));
        assertTrue(sql.contains("ALTER COLUMN food_item_id DROP NOT NULL"));
        assertTrue(MealTemplateItemEntity.class.getDeclaredField("foodItem").getAnnotation(JoinColumn.class).nullable());
        int snapshots = 0;
        for (var field : MealTemplateItemEntity.class.getDeclaredFields()) {
            if (!field.getName().startsWith("snapshot")) {
                continue;
            }
            String column = field.getAnnotation(Column.class).name();
            assertTrue(sql.contains("ADD COLUMN " + column + " DOUBLE PRECISION"), column);
            assertTrue(sql.contains(column + " = ROUND("), column);
            snapshots++;
        }
        assertEquals(19, snapshots);
        assertTrue(sql.contains("f.nutrition_reference_unit = 'PER_100ML'"));
        assertTrue(sql.contains("t.normalized_portion_milliliters"));
        assertTrue(sql.contains("t.normalized_portion_grams"));
        assertTrue(sql.contains("WHERE f.id = t.food_item_id"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
