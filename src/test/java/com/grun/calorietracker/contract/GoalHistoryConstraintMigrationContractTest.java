package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalHistoryConstraintMigrationContractTest {

    @Test
    void migrationRemovesLegacyUserOnlyUniquenessAndPreservesSingleActiveGoal() throws Exception {
        String sql = resource("db/migration/V251__remove_legacy_goal_user_unique_constraint.sql");

        assertTrue(sql.contains("constraint_definition.contype = 'u'"));
        assertTrue(sql.contains("ARRAY['user_id']::text[]"));
        assertTrue(sql.contains("DROP CONSTRAINT"));
        assertTrue(sql.contains("index_definition.indpred IS NULL"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_goals_one_active_per_user"));
        assertTrue(sql.contains("WHERE effective_until IS NULL"));
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path + " missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
