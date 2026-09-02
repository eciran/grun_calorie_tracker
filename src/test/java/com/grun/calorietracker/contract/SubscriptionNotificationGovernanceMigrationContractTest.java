package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionNotificationGovernanceMigrationContractTest {
    @Test void governanceStartsStoppedAndEngagementsAreIdempotent() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V242__add_subscription_notification_governance.sql"));
        assertThat(sql).contains("requested_delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("emergency_stopped BOOLEAN NOT NULL DEFAULT TRUE")
                .contains("1, FALSE, TRUE")
                .contains("UNIQUE (notification_id, user_id, engagement_type)")
                .contains("IN ('IN_APP','PUSH')");
    }
}
