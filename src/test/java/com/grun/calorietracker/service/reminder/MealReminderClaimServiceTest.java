package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.entity.MealReminderScheduleEntity;
import com.grun.calorietracker.repository.MealReminderOutboxRepository;
import com.grun.calorietracker.repository.MealReminderScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealReminderClaimServiceTest {
    @Mock MealReminderScheduleRepository scheduleRepository;
    @Mock MealReminderOutboxRepository outboxRepository;

    @Test
    void claimsBoundedSkipLockedPageAndRecordsStableNextEvaluationAndDuration() {
        MealReminderDeliveryProperties properties = new MealReminderDeliveryProperties();
        properties.setCandidateBatchSize(7);
        properties.setScheduleLease(Duration.ofMinutes(2));
        properties.setDeterministicJitter(Duration.ofSeconds(45));
        MealReminderClaimService service = new MealReminderClaimService(
                scheduleRepository, outboxRepository, properties);
        Instant now = Instant.parse("2026-08-29T12:00:00Z");
        MealReminderScheduleEntity row = new MealReminderScheduleEntity();
        row.setUserId(47L);
        row.setNextEvaluationAt(now);
        row.setUpdatedAt(now);
        when(scheduleRepository.lockDue(now, 7)).thenReturn(List.of(row));

        assertEquals(List.of(47L), service.claimSchedules("worker-a", now));
        assertEquals("worker-a", row.getLeaseOwner());
        assertEquals(now.plus(Duration.ofMinutes(2)), row.getLeaseUntil());

        when(scheduleRepository.findById(47L)).thenReturn(Optional.of(row));
        service.completeSchedule(47L, "worker-a", now, now.plusMillis(125), null);

        assertNull(row.getLeaseOwner());
        assertEquals(125L, row.getLastDurationMs());
        assertEquals(now.plusMillis(125).plus(MealReminderContract.SCAN_INTERVAL).plusSeconds(1),
                row.getNextEvaluationAt());
        verify(scheduleRepository).save(row);
    }
}
