package com.grun.calorietracker.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedbackMigrationContractTest {
    @Test
    void migrationProtectsFeedbackScopeAndIdempotency() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V209__add_test_feedback_foundation.sql"));

        assertThat(sql).contains(
                "uk_test_feedback_user_idempotency",
                "chk_test_feedback_type",
                "ANDROID",
                "IOS"
        );
        assertThat(sql).doesNotContain("request_body", "access_token", "password");
    }
}
