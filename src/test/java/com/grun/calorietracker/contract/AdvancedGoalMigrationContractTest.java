package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedGoalMigrationContractTest {
    @Test
    void goalVersionMigrationPreservesHistoryAndAcknowledgements() throws Exception {
        String sql = resource("db/migration/V223__version_user_nutrition_goals.sql");
        assertTrue(sql.contains("calculation_mode"));
        assertTrue(sql.contains("effective_until"));
        assertTrue(sql.contains("uk_goals_one_active_per_user"));
        assertTrue(sql.contains("CREATE TABLE goal_target_acknowledgements"));
        assertTrue(sql.contains("policy_version VARCHAR(64)"));
    }

    @Test
    void previewSecurityMigrationCreatesTokenAndIdempotencyStores() throws Exception {
        String sql = resource("db/migration/V224__secure_advanced_goal_preview_and_save.sql");
        assertTrue(sql.contains("CREATE TABLE advanced_goal_previews"));
        assertTrue(sql.contains("request_hash VARCHAR(64)"));
        assertTrue(sql.contains("profile_version VARCHAR(64)"));
        assertTrue(sql.contains("CREATE TABLE advanced_goal_save_requests"));
        assertTrue(sql.contains("uk_advanced_goal_save_user_key UNIQUE"));
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path + " missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
