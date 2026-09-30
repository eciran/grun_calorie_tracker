package com.grun.calorietracker.contract;

import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandedDuplicateDecisionMigrationContractTest {
    @Test
    void migrationContainsDecisionConstraintsAuditAndIdentityUniqueness() throws Exception {
        String resource = "db/migration/V292__add_branded_duplicate_review_decisions.sql";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " missing");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (BrandedDuplicateDecision value : BrandedDuplicateDecision.values()) {
                assertTrue(sql.contains("'" + value.name() + "'"), "Missing decision: " + value);
            }
            assertTrue(sql.contains("uq_food_branded_duplicate_identity"));
            assertTrue(sql.contains("food_branded_duplicate_decision_audits"));
            assertTrue(sql.contains("candidate_fingerprint VARCHAR(64) NOT NULL"));
            assertTrue(sql.contains("decision = 'SURVIVOR_SELECTED' AND survivor_food_item_id IS NOT NULL"));
        }
    }
}
