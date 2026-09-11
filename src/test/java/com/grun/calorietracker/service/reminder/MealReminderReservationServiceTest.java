package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.MealReminderDeliveryProperties;
import com.grun.calorietracker.entity.MealReminderDailyBudgetEntity;
import com.grun.calorietracker.entity.MealReminderOccurrenceEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.NotificationDefinitionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MealReminderReservationServiceTest {
    @Test
    void offModeCannotCreateNotificationOccurrenceOutboxOrCounter() {
        Fixture fixture = new Fixture();
        var result = fixture.service.reserve(fixture.user, fixture.decision(), fixture.now);

        assertFalse(result.reserved());
        assertEquals(MealReminderContract.Reason.SYSTEM_DISABLED, result.reason());
        verifyNoInteractions(fixture.budgets, fixture.occurrences,
                fixture.notifications, fixture.outboxes);
    }

    @Test
    void eligibleDecisionReservesCounterAndAllTransactionalRecordsTogether() {
        Fixture fixture = new Fixture();
        fixture.properties.setDeliveryEnabled(true);
        fixture.properties.setMode(MealReminderContract.Mode.PILOT);
        when(fixture.occurrences.findByUserIdAndLocalDateAndSlot(
                11L, LocalDate.of(2026, 8, 29), MealReminderContract.Slot.LUNCH))
                .thenReturn(Optional.empty());
        when(fixture.budgets.findForUpdate(11L, LocalDate.of(2026, 8, 29)))
                .thenReturn(Optional.empty());
        when(fixture.budgets.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.occurrences.countRollingReservations(eq(11L), any(), isNull())).thenReturn(0L);

        var result = fixture.service.reserve(fixture.user, fixture.decision(), fixture.now);

        assertTrue(result.reserved());
        ArgumentCaptor<MealReminderDailyBudgetEntity> budget =
                ArgumentCaptor.forClass(MealReminderDailyBudgetEntity.class);
        verify(fixture.budgets, atLeastOnce()).save(budget.capture());
        assertEquals(1, budget.getValue().getReservedCount());
        verify(fixture.notifications).save(any());
        ArgumentCaptor<MealReminderOccurrenceEntity> occurrence =
                ArgumentCaptor.forClass(MealReminderOccurrenceEntity.class);
        verify(fixture.occurrences).save(occurrence.capture());
        assertTrue(occurrence.getValue().isReservationActive());
        verify(fixture.outboxes).save(any());
    }

    private static final class Fixture {
        final Instant now = Instant.parse("2026-08-29T12:00:00Z");
        final MealReminderDeliveryProperties properties = new MealReminderDeliveryProperties();
        final MealReminderDailyBudgetRepository budgets = mock(MealReminderDailyBudgetRepository.class);
        final MealReminderOccurrenceRepository occurrences = mock(MealReminderOccurrenceRepository.class);
        final NotificationRepository notifications = mock(NotificationRepository.class);
        final MealReminderOutboxRepository outboxes = mock(MealReminderOutboxRepository.class);
        final MealReminderPolicyFactory policyFactory = mock(MealReminderPolicyFactory.class);
        final NotificationDefinitionPolicy definitionPolicy = mock(NotificationDefinitionPolicy.class);
        final UserEntity user = new UserEntity();
        final MealReminderReservationService service = new MealReminderReservationService(
                properties, budgets, occurrences, notifications, outboxes, policyFactory, definitionPolicy);

        Fixture() { user.setId(11L); when(policyFactory.current()).thenReturn(new MealReminderPolicy(
                "test", MealReminderContract.Mode.PILOT, true, true, MealReminderContract.DEFAULT_TIMES,
                MealReminderContract.MAX_SLOT_AGE, 3, 3, 1, MealReminderContract.MIN_MEAL_REMINDER_GAP,
                MealReminderContract.ROUTINE_REMINDER_GAP, MealReminderContract.DEFAULT_QUIET_START,
                MealReminderContract.DEFAULT_QUIET_END)); when(definitionPolicy.presentation(any(),isNull(),any())).thenAnswer(invocation->{var notification=(com.grun.calorietracker.entity.NotificationEntity)invocation.getArgument(0);return new NotificationDefinitionPolicy.NotificationPresentation(notification.getTitle(),notification.getMessage(),notification.getSeverity(),notification.getTargetRoute());}); }

        MealReminderDecision decision() {
            return new MealReminderDecision(
                    MealReminderDecision.Candidate.LUNCH, MealReminderContract.Slot.LUNCH,
                    true, MealReminderContract.Reason.ELIGIBLE, MealReminderContract.KcalReason.NOT_DINNER,
                    MealReminderContract.Message.LUNCH, Map.of(),
                    MealReminderContract.initialCopy(MealReminderContract.Message.LUNCH, "tr"),
                    "policy-v1", now, LocalDate.of(2026, 8, 29), now.minusSeconds(60),
                    now.plusSeconds(600));
        }
    }
}
