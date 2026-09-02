package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.MealReminderDeliveryAttemptEntity;
import com.grun.calorietracker.entity.MealReminderOccurrenceEntity;
import com.grun.calorietracker.entity.MealReminderOutboxEntity;
import com.grun.calorietracker.enums.MealReminderAttemptStatus;
import com.grun.calorietracker.enums.MealReminderOccurrenceStatus;
import com.grun.calorietracker.enums.MealReminderOutboxStatus;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.push.PushProviderSendResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MealReminderDispatchStateServiceTest {
    @Test
    void uncertainProviderOutcomeIsNotRetriedOrReleased() {
        MealReminderOutboxRepository outboxes = mock(MealReminderOutboxRepository.class);
        MealReminderDeliveryAttemptRepository attempts = mock(MealReminderDeliveryAttemptRepository.class);
        MealReminderDailyBudgetRepository budgets = mock(MealReminderDailyBudgetRepository.class);
        UserPushTokenRepository tokens = mock(UserPushTokenRepository.class);
        MealReminderDeliveryProperties properties = new MealReminderDeliveryProperties();
        properties.setMaxAttempts(3);
        MealReminderDispatchStateService service = new MealReminderDispatchStateService(
                outboxes, attempts, budgets, tokens,
                mock(DailyMealReminderSnapshotService.class), mock(MealReminderDecisionEngine.class),
                mock(MealReminderRuntimeStateFactory.class), mock(MealReminderPolicyFactory.class),
                properties, new PushProperties());
        Instant now = Instant.parse("2026-08-29T12:00:00Z");
        MealReminderOccurrenceEntity occurrence = new MealReminderOccurrenceEntity();
        occurrence.setStatus(MealReminderOccurrenceStatus.QUEUED);
        occurrence.setReservationActive(true);
        MealReminderOutboxEntity outbox = new MealReminderOutboxEntity();
        outbox.setId(3L);
        outbox.setOccurrence(occurrence);
        outbox.setStatus(MealReminderOutboxStatus.PROCESSING);
        outbox.setExpiresAt(now.plusSeconds(60));
        outbox.setLeaseOwner("worker");
        MealReminderDeliveryAttemptEntity attempt = new MealReminderDeliveryAttemptEntity();
        attempt.setId(4L);
        attempt.setOutbox(outbox);
        attempt.setStatus(MealReminderAttemptStatus.PROCESSING);
        attempt.setAttemptCount(1);
        when(attempts.findById(4L)).thenReturn(Optional.of(attempt));
        when(attempts.findByOutboxIdOrderById(3L)).thenReturn(List.of(attempt));

        service.recordProviderResult(4L, PushProviderSendResult.uncertain("timeout"), now);

        assertEquals(MealReminderAttemptStatus.UNKNOWN, attempt.getStatus());
        assertEquals(MealReminderOutboxStatus.UNKNOWN, outbox.getStatus());
        assertEquals(MealReminderOccurrenceStatus.UNKNOWN, occurrence.getStatus());
        assertTrue(occurrence.isReservationActive());
        assertNull(attempt.getNextAttemptAt());
        verifyNoInteractions(budgets);
    }
}
