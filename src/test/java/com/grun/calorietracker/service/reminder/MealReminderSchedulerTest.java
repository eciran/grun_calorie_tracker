package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MealReminderSchedulerTest {
    @Test
    void oneBrokenUserDoesNotStopTheRemainingClaimedPage() {
        Instant now = Instant.parse("2026-08-29T12:00:00Z");
        MealReminderClaimService claims = mock(MealReminderClaimService.class);
        MealReminderCandidateWorker worker = mock(MealReminderCandidateWorker.class);
        MealReminderDeliveryProperties properties = new MealReminderDeliveryProperties();
        properties.setMode(MealReminderContract.Mode.PILOT);
        when(claims.claimSchedules(anyString(), eq(now))).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("broken fixture")).when(worker).evaluate(1L, now);

        MealReminderPolicyFactory policyFactory = mock(MealReminderPolicyFactory.class);
        when(policyFactory.current()).thenReturn(MealReminderPolicy.dryRunDefaults("test"));
        new MealReminderScheduler(claims, worker, policyFactory, Clock.fixed(now, ZoneOffset.UTC)).scan();

        verify(worker).evaluate(1L, now);
        verify(worker).evaluate(2L, now);
        verify(claims, times(2)).completeSchedule(anyLong(), anyString(), eq(now), eq(now), any());
    }

    @Test
    void fullDefaultCandidatePageCompletesBelowTheFiveSecondWorkerInterval() {
        Instant now = Instant.parse("2026-08-29T12:00:00Z");
        MealReminderClaimService claims = mock(MealReminderClaimService.class);
        MealReminderCandidateWorker worker = mock(MealReminderCandidateWorker.class);
        List<Long> page = LongStream.rangeClosed(1, 100).boxed().toList();
        when(claims.claimSchedules(anyString(), eq(now))).thenReturn(page);
        MealReminderPolicyFactory policyFactory = mock(MealReminderPolicyFactory.class);
        when(policyFactory.current()).thenReturn(MealReminderPolicy.dryRunDefaults("load-contract"));
        MealReminderScheduler scheduler = new MealReminderScheduler(
                claims, worker, policyFactory, Clock.fixed(now, ZoneOffset.UTC));

        long started = System.nanoTime();
        scheduler.scan();
        long durationMillis = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();

        verify(worker, times(100)).evaluate(anyLong(), eq(now));
        verify(claims, times(100)).completeSchedule(anyLong(), anyString(), eq(now), eq(now), isNull());
        assertTrue(durationMillis < 5_000, "mocked full page took " + durationMillis + "ms");
    }
}
