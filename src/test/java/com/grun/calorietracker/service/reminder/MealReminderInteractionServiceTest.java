package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.dto.MealReminderInteractionRequestDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.MealReminderInteractionType;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MealReminderInteractionServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final MealReminderOccurrenceRepository occurrences = mock(MealReminderOccurrenceRepository.class);
    private final MealReminderInteractionRepository interactions = mock(MealReminderInteractionRepository.class);
    private final FoodLogsRepository foodLogs = mock(FoodLogsRepository.class);
    private final RecipeLogRepository recipeLogs = mock(RecipeLogRepository.class);
    private final Instant now = Instant.parse("2026-08-29T12:00:00Z");
    private MealReminderInteractionService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new MealReminderInteractionService(users, occurrences, interactions, foodLogs, recipeLogs,
                new UserTimeZoneSupport(), Clock.fixed(now, ZoneOffset.UTC));
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("member@example.com");
        user.setTimeZone("UTC");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void resolvesTargetedCurrentDayAndUsesNotificationScopedIdempotencyKey() {
        MealReminderOccurrenceEntity occurrence = occurrence(31L, 81L, LocalDate.of(2026, 8, 29),
                MealReminderDecision.Candidate.LUNCH);
        when(occurrences.findByNotificationIdAndUserId(81L, 7L)).thenReturn(Optional.of(occurrence));

        var result = service.resolveAndRecordOpen(user.getEmail(), 81L,
                new MealReminderInteractionRequestDto("tap-1", "BACKGROUND"));

        assertEquals("MEAL_ADD", result.destination());
        assertEquals("LUNCH", result.mealType());
        assertFalse(result.staleFallback());
        verify(interactions).insertIdempotent(eq(7L), eq(31L), eq(81L), eq("OPEN"), eq("OPEN:81"),
                eq("tap-1"), eq("BACKGROUND"), eq(LocalDate.of(2026, 8, 29)), eq(now));
    }

    @Test
    void staleOrCatchupNotificationFallsBackToDiaryWithoutKcalTruth() {
        MealReminderOccurrenceEntity occurrence = occurrence(32L, 82L, LocalDate.of(2026, 8, 28),
                MealReminderDecision.Candidate.DINNER);
        when(occurrences.findByNotificationIdAndUserId(82L, 7L)).thenReturn(Optional.of(occurrence));

        var result = service.resolveAndRecordOpen(user.getEmail(), 82L,
                new MealReminderInteractionRequestDto("tap-2", "COLD_START"));

        assertEquals("DAILY_DIARY", result.destination());
        assertNull(result.mealType());
        assertTrue(result.staleFallback());
        assertEquals("diary", result.fallbackRoute());
    }

    @Test
    void anotherAccountsNotificationCannotBeResolvedOrCounted() {
        when(occurrences.findByNotificationIdAndUserId(99L, 7L)).thenReturn(Optional.empty());
        assertThrows(com.grun.calorietracker.exception.ResourceNotFoundException.class,
                () -> service.resolveAndRecordOpen(user.getEmail(), 99L,
                        new MealReminderInteractionRequestDto("tap-3", "FOREGROUND")));
        verifyNoInteractions(interactions);
    }

    @Test
    void optOutIsRecordedOnlyByExplicitPreferenceTransitionCaller() {
        when(occurrences.findRecentActive(eq(7L), isNull(), any())).thenReturn(java.util.List.of());
        service.recordOptOut(user);
        verify(interactions).insertIdempotent(eq(7L), isNull(), isNull(),
                eq(MealReminderInteractionType.OPT_OUT.name()), startsWith("OPT_OUT:7:"),
                isNull(), eq("PREFERENCE"), isNull(), eq(now));
    }

    @Test
    void foodAndRecipeLogsWithinTwoHoursUseStablePerLogConversionKeys() {
        MealReminderOccurrenceEntity occurrence = occurrence(40L, 90L, LocalDate.of(2026, 8, 29),
                MealReminderDecision.Candidate.LUNCH);
        MealReminderInteractionEntity open = new MealReminderInteractionEntity();
        open.setUser(user);
        open.setOccurrence(occurrence);
        open.setNotification(occurrence.getNotification());
        open.setRelatedDate(LocalDate.of(2026, 8, 29));
        open.setRecordedAt(now.minus(Duration.ofMinutes(30)));
        when(interactions.findFirstByUserIdAndRelatedDateAndEventTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
                eq(7L), eq(LocalDate.of(2026, 8, 29)), eq(MealReminderInteractionType.OPEN), any(), eq(now)))
                .thenReturn(Optional.of(open));
        FoodLogsEntity food = new FoodLogsEntity(); food.setId(501L); food.setCreatedAt(LocalDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC));
        RecipeLogEntity recipe = new RecipeLogEntity(); recipe.setId(601L); recipe.setCreatedAt(LocalDateTime.ofInstant(now.minusSeconds(30), ZoneOffset.UTC));
        when(foodLogs.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(any(), any(), any())).thenReturn(List.of(food));
        when(recipeLogs.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(any(), any(), any())).thenReturn(List.of(recipe));

        service.recordMealLogConversions(new com.grun.calorietracker.event.FoodDiaryChangedEvent(
                user.getEmail(), LocalDate.of(2026, 8, 29)));

        verify(interactions).insertIdempotent(eq(7L), eq(40L), eq(90L), eq("MEAL_LOG_CONVERSION"),
                eq("CONVERSION:FOOD:501"), isNull(), eq("FOOD"), eq(LocalDate.of(2026, 8, 29)), eq(now));
        verify(interactions).insertIdempotent(eq(7L), eq(40L), eq(90L), eq("MEAL_LOG_CONVERSION"),
                eq("CONVERSION:RECIPE:601"), isNull(), eq("RECIPE"), eq(LocalDate.of(2026, 8, 29)), eq(now));
    }

    private MealReminderOccurrenceEntity occurrence(Long id, Long notificationId, LocalDate date,
                                                      MealReminderDecision.Candidate candidate) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(notificationId);
        notification.setUser(user);
        notification.setIsRead(false);
        MealReminderOccurrenceEntity occurrence = new MealReminderOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setUser(user);
        occurrence.setNotification(notification);
        occurrence.setLocalDate(date);
        occurrence.setCandidate(candidate);
        return occurrence;
    }
}
