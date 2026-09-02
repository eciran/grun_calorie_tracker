package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlanningProOnlyMigrationContractTest {

    @Test
    void migrationMakesPlanningFeaturesProOnlyAndRepairsSnapshots() throws Exception {
        String sql = Files.readString(Path.of(
                        "src/main/resources/db/migration/V241__make_ai_planning_features_pro_only.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("'ai_nutrition_plan'"));
        assertTrue(sql.contains("'ai_workout_planner'"));
        assertTrue(sql.contains("plan_type = 'pro'"));
        assertTrue(sql.contains("subscription.plan_type <> 'pro'"));
        assertTrue(sql.contains("set enabled = false"));
        assertTrue(sql.contains("insert into user_subscription_entitlements"));
        assertTrue(sql.contains("where subscription.plan_type = 'pro'"));
    }
}
