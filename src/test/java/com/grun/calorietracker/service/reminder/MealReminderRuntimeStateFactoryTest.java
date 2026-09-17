package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MealReminderRuntimeStateFactoryTest {
    @Test
    void onlyMealOccurrencesConsumeBudgetAndOnlyRoutineRemindersDelayMeals() {
        var tokens = mock(UserPushTokenRepository.class);
        var occurrences = mock(MealReminderOccurrenceRepository.class);
        var budgets = mock(MealReminderDailyBudgetRepository.class);
        var notifications = mock(NotificationRepository.class);
        var policies = mock(MealReminderPolicyFactory.class);
        var user = new UserEntity();
        user.setId(42L);
        user.setAccountEnabled(true);
        var current = new MealReminderOccurrenceEntity();
        current.setLocalDate(LocalDate.of(2026, 9, 14));
        var now = Instant.parse("2026-09-14T12:00:00Z");
        when(occurrences.findRecentActive(eq(42L), isNull(), any())).thenReturn(List.of());
        when(budgets.findByUserIdAndLocalDate(42L, current.getLocalDate())).thenReturn(Optional.empty());
        when(notifications.findTopByUserAndSourceInOrderByCreatedAtDesc(eq(user), anyList()))
                .thenAnswer(invocation -> {
                    List<String> sources = invocation.getArgument(1);
                    assertEquals(List.of("WATER_REMINDER", "FASTING_REMINDER", "ADVANCED_FASTING_REMINDER", "STEP_REMINDER"), sources);
                    assertTrue(sources.stream().noneMatch(source -> source.contains("AI") || source.contains("SUBSCRIPTION")));
                    return Optional.empty();
                });
        var factory = new MealReminderRuntimeStateFactory(new PushProperties(), tokens, occurrences,
                budgets, notifications, policies);
        var state = factory.current(user, current, now);
        assertEquals(0, state.dailyOccurrences());
        assertEquals(0, state.rollingOccurrences());
        assertNull(state.lastRoutineReminderAt());
        verify(occurrences).countRollingReservations(42L, now.minusSeconds(86400), null);
        verify(notifications).findTopByUserAndSourceInOrderByCreatedAtDesc(eq(user), anyList());
        verifyNoMoreInteractions(notifications);
    }
}
