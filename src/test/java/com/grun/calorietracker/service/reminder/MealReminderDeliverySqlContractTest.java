package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.repository.MealReminderOutboxRepository;
import com.grun.calorietracker.repository.MealReminderScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MealReminderDeliverySqlContractTest {
    @Test
    void migrationHasIdempotencyAndPerTokenAttemptConstraints() throws IOException {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V232__add_reliable_meal_reminder_delivery.sql").readAllBytes(),
                StandardCharsets.UTF_8).toLowerCase();
        assertTrue(sql.contains("unique (user_id, local_date, slot)"));
        assertTrue(sql.contains("unique (outbox_id, push_token_id)"));
        assertTrue(sql.contains("unique (user_id, local_date)"));
        assertTrue(sql.contains("on conflict (user_id) do nothing"));
    }

    @Test
    void bothCandidateAndOutboxClaimsUseSkipLockedAndBounds() throws Exception {
        assertClaimQuery(MealReminderScheduleRepository.class, "lockDue");
        assertClaimQuery(MealReminderOutboxRepository.class, "lockDue");
    }

    private void assertClaimQuery(Class<?> repository, String methodName) throws Exception {
        var method = java.util.Arrays.stream(repository.getMethods())
                .filter(value -> value.getName().equals(methodName)).findFirst().orElseThrow();
        String sql = method.getAnnotation(Query.class).value().toLowerCase();
        assertTrue(sql.contains("for update skip locked"));
        assertTrue(sql.contains("limit :limit"));
    }
}
