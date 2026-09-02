package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionCreditAllocationMigrationContractTest {
    @Test
    void migrationSeparatesPlanUsageAndMakesVerifiedAllocationsUnique() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V235__add_idempotent_subscription_credit_allocations.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        assertTrue(sql.contains("ai_plan_used_this_period"));
        assertTrue(sql.contains("coalesce(ai_used_this_period, 0) - coalesce(ai_addon_used, 0)"));
        assertTrue(sql.contains("unique (provider, user_id, allocation_key)"));
        assertTrue(sql.contains("timestamp with time zone"));
        assertTrue(sql.contains("new_product_id"));
        assertTrue(sql.contains("check (quota_amount between 0 and 10000)"));
    }
}
