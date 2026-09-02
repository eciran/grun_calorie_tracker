package com.grun.calorietracker.service.reminder;

import com.grun.calorietracker.dto.MealReminderInteractionRequestDto;
import com.grun.calorietracker.dto.MealReminderNotificationContextDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.MealReminderInteractionType;
import com.grun.calorietracker.event.FoodDiaryChangedEvent;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.*;

@Service
@RequiredArgsConstructor
public class MealReminderInteractionService {
    private static final Duration CONVERSION_WINDOW = Duration.ofHours(2);

    private final UserRepository users;
    private final MealReminderOccurrenceRepository occurrences;
    private final MealReminderInteractionRepository interactions;
    private final FoodLogsRepository foodLogs;
    private final RecipeLogRepository recipeLogs;
    private final UserTimeZoneSupport timeZones;
    private final Clock analyticsClock;

    @Transactional
    public MealReminderNotificationContextDto resolveAndRecordOpen(
            String email, Long notificationId, MealReminderInteractionRequestDto request) {
        UserEntity user = users.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        MealReminderOccurrenceEntity occurrence = occurrences
                .findByNotificationIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Meal reminder notification not found"));
        Instant now = analyticsClock.instant();
        interactions.insertIdempotent(user.getId(), occurrence.getId(), notificationId,
                MealReminderInteractionType.OPEN.name(), "OPEN:" + notificationId,
                request.eventId().trim(), request.source(), occurrence.getLocalDate(), now);

        NotificationEntity notification = occurrence.getNotification();
        if (notification != null && !Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
        }
        boolean stale = !occurrence.getLocalDate().equals(LocalDate.now(timeZones.zoneId(user)));
        String mealType = stale ? null : targetedMeal(occurrence.getCandidate());
        return new MealReminderNotificationContextDto(notificationId, occurrence.getId(), occurrence.getLocalDate(),
                mealType == null ? "DAILY_DIARY" : "MEAL_ADD", mealType, "diary", stale, 1);
    }

    @Transactional
    public void recordOptOut(UserEntity user) {
        Instant now = analyticsClock.instant();
        MealReminderOccurrenceEntity occurrence = occurrences
                .findRecentActive(user.getId(), null, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        Long notificationId = occurrence == null || occurrence.getNotification() == null
                ? null : occurrence.getNotification().getId();
        interactions.insertIdempotent(user.getId(), occurrence == null ? null : occurrence.getId(), notificationId,
                MealReminderInteractionType.OPT_OUT.name(), "OPT_OUT:" + user.getId() + ":" + now.toEpochMilli(),
                null, "PREFERENCE", occurrence == null ? null : occurrence.getLocalDate(), now);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMealLogConversions(FoodDiaryChangedEvent event) {
        UserEntity user = users.findByEmail(event.email()).orElse(null);
        if (user == null || event.date() == null) return;
        Instant now = analyticsClock.instant();
        var open = interactions.findFirstByUserIdAndRelatedDateAndEventTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
                user.getId(), event.date(), MealReminderInteractionType.OPEN, now.minus(CONVERSION_WINDOW), now)
                .orElse(null);
        if (open == null || open.getOccurrence() == null) return;

        // created_at columns are the backend's UTC LocalDateTime audit clock; diary date remains user-local.
        LocalDateTime openedAt = LocalDateTime.ofInstant(open.getRecordedAt(), ZoneOffset.UTC);
        LocalDateTime end = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime dayStart = event.date().atStartOfDay();
        LocalDateTime dayEnd = event.date().plusDays(1).atStartOfDay();
        foodLogs.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, dayStart, dayEnd)
                .stream().filter(log -> log.getCreatedAt() != null && !log.getCreatedAt().isBefore(openedAt)
                        && !log.getCreatedAt().isAfter(end))
                .forEach(log -> insertConversion(open, "FOOD", log.getId(), now));
        recipeLogs.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, dayStart, dayEnd)
                .stream().filter(log -> log.getCreatedAt() != null && !log.getCreatedAt().isBefore(openedAt)
                        && !log.getCreatedAt().isAfter(end))
                .forEach(log -> insertConversion(open, "RECIPE", log.getId(), now));
    }

    private void insertConversion(MealReminderInteractionEntity open, String kind, Long logId, Instant now) {
        interactions.insertIdempotent(open.getUser().getId(), open.getOccurrence().getId(),
                open.getNotification() == null ? null : open.getNotification().getId(),
                MealReminderInteractionType.MEAL_LOG_CONVERSION.name(), "CONVERSION:" + kind + ":" + logId,
                null, kind, open.getRelatedDate(), now);
    }

    private String targetedMeal(MealReminderDecision.Candidate candidate) {
        return switch (candidate) {
            case BREAKFAST -> "BREAKFAST";
            case LUNCH -> "LUNCH";
            case DINNER -> "DINNER";
            case DAILY_CATCHUP -> null;
        };
    }
}
