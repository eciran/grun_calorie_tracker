package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.entity.MealReminderDryRunDecisionEntity;
import com.grun.calorietracker.repository.MealReminderDryRunDecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealReminderDryRunServiceTest {

    @Mock private MealReminderDecisionEngine decisionEngine;
    @Mock private MealReminderDryRunDecisionRepository dryRunRepository;
    @Mock private DailyMealReminderSnapshot snapshot;
    @Mock private MealReminderRuntimeState runtime;

    private MealReminderDryRunService service;

    @BeforeEach
    void setUp() {
        service = new MealReminderDryRunService(decisionEngine, dryRunRepository);
    }

    @Test
    void previewUsesSameEngineWithoutWritingAnyRow() {
        MealReminderPolicy policy = MealReminderPolicy.dryRunDefaults("policy-v3");
        MealReminderDecision expected = decision();
        when(decisionEngine.evaluate(snapshot, policy, runtime)).thenReturn(expected);

        MealReminderDecision actual = service.preview(snapshot, policy, runtime);

        assertEquals(expected, actual);
        verifyNoInteractions(dryRunRepository);
    }

    @Test
    void evaluateAndRecordPurgesExpiredRowsAndStoresOnlyRedactedBoundedDecision() {
        MealReminderPolicy policy = MealReminderPolicy.dryRunDefaults("policy-v3");
        MealReminderDecision expected = decision();
        when(decisionEngine.evaluate(snapshot, policy, runtime)).thenReturn(expected);

        MealReminderDecision actual = service.evaluateAndRecord("synthetic:scenario-17", snapshot, policy, runtime);

        assertEquals(expected, actual);
        verify(dryRunRepository).deleteByExpiresAtLessThanEqual(expected.evaluatedAt());
        ArgumentCaptor<MealReminderDryRunDecisionEntity> captor =
                ArgumentCaptor.forClass(MealReminderDryRunDecisionEntity.class);
        verify(dryRunRepository).save(captor.capture());
        MealReminderDryRunDecisionEntity stored = captor.getValue();
        assertEquals("synthetic:scenario-17", stored.getSubjectRef());
        assertEquals(expected.evaluatedAt().plus(MealReminderDryRunService.RETENTION), stored.getExpiresAt());
        assertEquals(expected.reason(), stored.getReason());
        assertEquals(expected.message(), stored.getMessageVariant());
        assertEquals(expected.shouldSend(), stored.isShouldSend());
    }

    @Test
    void recordingRejectsLiveModesAndSensitiveLookingSubjectReferences() {
        MealReminderPolicy live = new MealReminderPolicy(
                "policy-live", MealReminderContract.Mode.LIVE, true, true,
                MealReminderContract.DEFAULT_TIMES, MealReminderContract.MAX_SLOT_AGE,
                3, 3, 1, MealReminderContract.MIN_MEAL_REMINDER_GAP,
                MealReminderContract.ROUTINE_REMINDER_GAP,
                MealReminderContract.DEFAULT_QUIET_START, MealReminderContract.DEFAULT_QUIET_END);

        assertThrows(IllegalArgumentException.class,
                () -> service.evaluateAndRecord("synthetic:one", snapshot, live, runtime));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluateAndRecord("email@example.com", snapshot,
                        MealReminderPolicy.dryRunDefaults("policy-v3"), runtime));
        verify(decisionEngine, never()).evaluate(snapshot, live, runtime);
        verifyNoInteractions(dryRunRepository);
    }

    private MealReminderDecision decision() {
        Instant evaluatedAt = Instant.parse("2026-08-29T19:45:00Z");
        return new MealReminderDecision(
                MealReminderDecision.Candidate.DAILY_CATCHUP,
                MealReminderContract.Slot.EVENING,
                true,
                MealReminderContract.Reason.PREVIOUS_MEAL_MISSING,
                MealReminderContract.KcalReason.PREVIOUS_MEAL_MISSING,
                MealReminderContract.Message.DAILY_CATCHUP,
                Map.of(),
                MealReminderContract.initialCopy(MealReminderContract.Message.DAILY_CATCHUP, "tr"),
                "policy-v3",
                evaluatedAt,
                LocalDate.of(2026, 8, 29),
                Instant.parse("2026-08-29T19:30:00Z"),
                Instant.parse("2026-08-29T20:30:00Z"));
    }
}
