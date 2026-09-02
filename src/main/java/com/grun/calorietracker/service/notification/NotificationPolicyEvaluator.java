package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationClassification;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

@Service
public class NotificationPolicyEvaluator {

    public NotificationPolicyDecision evaluate(
            UserEntity user,
            NotificationEventType eventType,
            Set<NotificationDeliveryChannel> requestedChannels,
            Instant now
    ) {
        if (user == null || eventType == null || now == null) {
            return suppressed("INVALID_CONTEXT");
        }
        if (!Boolean.TRUE.equals(user.getAccountEnabled())) {
            return suppressed("ACCOUNT_DISABLED");
        }

        EnumSet<NotificationDeliveryChannel> allowed = requestedChannels == null || requestedChannels.isEmpty()
                ? EnumSet.copyOf(eventType.defaultChannels())
                : EnumSet.copyOf(requestedChannels);
        NotificationClassification classification = eventType.classification();

        if (classification == NotificationClassification.MARKETING
                && !Boolean.TRUE.equals(user.getMarketingNotificationsEnabled())) {
            return suppressed("MARKETING_OPT_OUT");
        }
        if (classification == NotificationClassification.BEHAVIOR_REMINDER
                && !behaviorPreferenceEnabled(user, eventType)) {
            return suppressed("CATEGORY_OPT_OUT");
        }
        if (!Boolean.TRUE.equals(user.getPushNotificationsEnabled())) {
            allowed.remove(NotificationDeliveryChannel.PUSH);
        }

        Instant pushAvailableAt = now;
        if (allowed.contains(NotificationDeliveryChannel.PUSH)
                && (classification == NotificationClassification.BEHAVIOR_REMINDER
                || classification == NotificationClassification.TRANSACTIONAL_ACCOUNT)) {
            QuietHoursResult quietHours = quietHours(user, now);
            if (quietHours.invalid()) {
                allowed.remove(NotificationDeliveryChannel.PUSH);
                if (classification == NotificationClassification.BEHAVIOR_REMINDER) {
                    allowed.remove(NotificationDeliveryChannel.IN_APP);
                }
                return new NotificationPolicyDecision(allowed, now, "INVALID_QUIET_HOURS");
            }
            if (quietHours.resumeAt() != null) {
                pushAvailableAt = quietHours.resumeAt();
                if (classification == NotificationClassification.BEHAVIOR_REMINDER) {
                    // Reminder inbox rows must not appear silently during quiet hours.
                    allowed.remove(NotificationDeliveryChannel.IN_APP);
                }
            }
        }
        return new NotificationPolicyDecision(allowed, pushAvailableAt,
                pushAvailableAt.isAfter(now) ? "QUIET_HOURS_DEFERRED" : "ELIGIBLE");
    }

    private boolean behaviorPreferenceEnabled(UserEntity user, NotificationEventType eventType) {
        return switch (eventType) {
            case MEAL_REMINDER_BREAKFAST, MEAL_REMINDER_LUNCH, MEAL_REMINDER_DINNER,
                    MEAL_REMINDER_DINNER_KCAL, MEAL_REMINDER_DAILY_CATCHUP ->
                    Boolean.TRUE.equals(user.getMealRemindersEnabled());
            case WATER_REMINDER_DUE -> Boolean.TRUE.equals(user.getHydrationRemindersEnabled());
            case STEP_REMINDER_DUE -> Boolean.TRUE.equals(user.getStepRemindersEnabled());
            case FASTING_REMINDER_DUE -> Boolean.TRUE.equals(user.getFastingRemindersEnabled());
            default -> false;
        };
    }

    private QuietHoursResult quietHours(UserEntity user, Instant now) {
        LocalTime start = user.getNotificationQuietHoursStart();
        LocalTime end = user.getNotificationQuietHoursEnd();
        if (start == null && end == null) return QuietHoursResult.clear();
        if (start == null || end == null) return QuietHoursResult.invalidResult();
        if (start.equals(end)) return QuietHoursResult.clear();
        try {
            ZoneId zone = ZoneId.of(user.getTimeZone());
            ZonedDateTime localNow = now.atZone(zone);
            LocalTime time = localNow.toLocalTime();
            boolean overnight = start.isAfter(end);
            boolean inside = overnight
                    ? !time.isBefore(start) || time.isBefore(end)
                    : !time.isBefore(start) && time.isBefore(end);
            if (!inside) return QuietHoursResult.clear();
            LocalDate resumeDate = overnight && !time.isBefore(start)
                    ? localNow.toLocalDate().plusDays(1) : localNow.toLocalDate();
            LocalDateTime resumeLocal = LocalDateTime.of(resumeDate, end);
            return new QuietHoursResult(false, resumeLocal.atZone(zone).toInstant());
        } catch (DateTimeException exception) {
            return QuietHoursResult.invalidResult();
        }
    }

    private NotificationPolicyDecision suppressed(String reason) {
        return new NotificationPolicyDecision(Set.of(), null, reason);
    }

    private record QuietHoursResult(boolean invalid, Instant resumeAt) {
        static QuietHoursResult clear() { return new QuietHoursResult(false, null); }
        static QuietHoursResult invalidResult() { return new QuietHoursResult(true, null); }
    }
}
