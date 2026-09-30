package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BrandedDuplicateSearchCollapseMigrationContractTest {
    @Test
    void migrationDefinesReversibleSearchCollapseAndImmutableAudit() throws Exception {
        String resource = "db/migration/V294__add_reversible_branded_duplicate_search_collapse.sql";
        String sql;
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("food_branded_duplicate_search_collapses")
                .contains("food_branded_duplicate_search_collapse_members")
                .contains("food_branded_duplicate_search_collapse_audits")
                .contains("CHECK (action IN ('APPLY', 'REVERT'))")
                .contains("active BOOLEAN NOT NULL DEFAULT TRUE")
                .contains("suppressed_food_item_id");
    }
}
