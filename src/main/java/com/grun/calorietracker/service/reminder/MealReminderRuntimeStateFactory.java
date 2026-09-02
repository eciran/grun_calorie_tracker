package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.MealReminderOccurrenceEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.MealReminderDailyBudgetRepository;
import com.grun.calorietracker.repository.MealReminderOccurrenceRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MealReminderRuntimeStateFactory {
    private static final List<String> ROUTINE_SOURCES = List.of(
            "WATER_REMINDER", "FASTING_REMINDER", "ADVANCED_FASTING_REMINDER", "STEP_REMINDER");

    private final PushProperties pushProperties;
    private final UserPushTokenRepository tokenRepository;
    private final MealReminderOccurrenceRepository occurrenceRepository;
    private final MealReminderDailyBudgetRepository budgetRepository;
    private final NotificationRepository notificationRepository;
    private final MealReminderPolicyFactory policyFactory;

    public MealReminderRuntimeState current(
            UserEntity user,
            MealReminderOccurrenceEntity currentOccurrence,
            Instant now
    ) {
        Long excludedId = currentOccurrence == null ? null : currentOccurrence.getId();
        List<MealReminderOccurrenceEntity> recent = occurrenceRepository.findRecentActive(
                user.getId(), excludedId, PageRequest.of(0, 10));
        Set<MealReminderContract.Slot> handled = recent.stream()
                .filter(value -> currentOccurrence != null
                        && value.getLocalDate().equals(currentOccurrence.getLocalDate()))
                .map(MealReminderOccurrenceEntity::getSlot)
                .collect(Collectors.toSet());
        int daily = 0;
        int catchups = 0;
        if (currentOccurrence != null) {
            var budget = budgetRepository.findByUserIdAndLocalDate(user.getId(), currentOccurrence.getLocalDate());
            boolean persistedCurrent = currentOccurrence.getId() != null;
            daily = budget.map(value -> Math.max(0, value.getReservedCount() - (persistedCurrent ? 1 : 0))).orElse(0);
            catchups = budget.map(value -> Math.max(0, value.getCatchupCount()
                    - (persistedCurrent && currentOccurrence.getCandidate() == MealReminderDecision.Candidate.DAILY_CATCHUP ? 1 : 0))).orElse(0);
        }
        Instant lastMeal = recent.isEmpty() ? null : recent.get(0).getCreatedAt();
        Instant lastRoutine = notificationRepository
                .findTopByUserAndSourceInOrderByCreatedAtDesc(user, ROUTINE_SOURCES)
                .map(NotificationEntity::getCreatedAt)
                .map(value -> value.toInstant(ZoneOffset.UTC))
                .orElse(null);
        boolean hasToken = tokenRepository.findByUserAndEnabledTrue(user).stream()
                .anyMatch(token -> token.getProvider() == pushProperties.getProvider());
        return new MealReminderRuntimeState(
                Boolean.TRUE.equals(user.getAccountEnabled()) && !Boolean.TRUE.equals(user.getAccountLocked()),
                policyFactory.isPilotUser(user.getId()),
                hasToken,
                handled,
                daily,
                (int) occurrenceRepository.countRollingReservations(
                        user.getId(), now.minusSeconds(86_400), excludedId),
                catchups,
                lastMeal,
                lastRoutine,
                user.getPreferredLanguage() == null ? "en" : user.getPreferredLanguage().name(),
                currentOccurrence == null || currentOccurrence.getId() == null ? null : currentOccurrence.getSlot());
    }
}
