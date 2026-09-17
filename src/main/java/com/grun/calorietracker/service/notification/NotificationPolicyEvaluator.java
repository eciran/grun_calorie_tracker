package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationClassification;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

        // Reminder schedules are enforced by their producers, not a global quiet window.
        return new NotificationPolicyDecision(allowed, now, "ELIGIBLE");
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


    private NotificationPolicyDecision suppressed(String reason) {
        return new NotificationPolicyDecision(Set.of(), null, reason);
    }

}
