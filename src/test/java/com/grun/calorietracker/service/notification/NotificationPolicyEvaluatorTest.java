package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPolicyEvaluatorTest {
    private final NotificationPolicyEvaluator evaluator = new NotificationPolicyEvaluator();
    private final Instant now = Instant.parse("2026-08-31T21:30:00Z");

    @Test
    void transactionalNotificationRemainsInAppWhenGlobalPushIsOff() {
        UserEntity user = user();
        user.setPushNotificationsEnabled(false);

        NotificationPolicyDecision decision = evaluator.evaluate(user, NotificationEventType.SUBSCRIPTION_STARTED,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now);

        assertEquals(Set.of(NotificationDeliveryChannel.IN_APP), decision.allowedChannels());
        assertEquals("ELIGIBLE", decision.reasonCode());
    }

    @Test
    void marketingOptOutSuppressesEveryRequestedChannel() {
        UserEntity user = user();
        user.setMarketingNotificationsEnabled(false);

        NotificationPolicyDecision decision = evaluator.evaluate(user, NotificationEventType.MARKETING_CAMPAIGN,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now);

        assertTrue(decision.suppressed());
        assertEquals("MARKETING_OPT_OUT", decision.reasonCode());
    }

    @Test
    void behaviorPreferenceIsFailClosed() {
        UserEntity user = user();
        user.setHydrationRemindersEnabled(false);

        NotificationPolicyDecision decision = evaluator.evaluate(user, NotificationEventType.WATER_REMINDER_DUE,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now);

        assertTrue(decision.suppressed());
        assertEquals("CATEGORY_OPT_OUT", decision.reasonCode());
    }

    @Test
    void legacyQuietHoursDoNotDelayBehaviorNotifications() {
        UserEntity user = user();
        user.setNotificationQuietHoursStart(LocalTime.of(22, 0));
        user.setNotificationQuietHoursEnd(LocalTime.of(8, 0));

        NotificationPolicyDecision decision = evaluator.evaluate(user, NotificationEventType.MEAL_REMINDER_DAILY_CATCHUP,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now);

        assertEquals(Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), decision.allowedChannels());
        assertEquals(now, decision.pushAvailableAt());
        assertEquals("ELIGIBLE", decision.reasonCode());
    }

    @Test
    void transactionalNotificationsAreImmediateAtNight() {
        UserEntity user = user();
        user.setNotificationQuietHoursStart(LocalTime.of(22, 0));
        user.setNotificationQuietHoursEnd(LocalTime.of(8, 0));

        NotificationPolicyDecision decision = evaluator.evaluate(user, NotificationEventType.SUBSCRIPTION_CANCELLED,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now);

        assertTrue(decision.allows(NotificationDeliveryChannel.IN_APP));
        assertTrue(decision.allows(NotificationDeliveryChannel.PUSH));
        assertEquals(now, decision.pushAvailableAt());
    }

    @Test
    void legacyQuietHoursTimezoneDoesNotSuppressDelivery() {
        UserEntity user = user();
        user.setTimeZone("Not/A_Zone");
        user.setNotificationQuietHoursStart(LocalTime.of(22, 0));
        user.setNotificationQuietHoursEnd(LocalTime.of(8, 0));

        NotificationPolicyDecision decision = evaluator.evaluate(user, NotificationEventType.STEP_REMINDER_DUE,
                Set.of(NotificationDeliveryChannel.IN_APP, NotificationDeliveryChannel.PUSH), now);

        assertTrue(decision.allows(NotificationDeliveryChannel.PUSH));
        assertEquals("ELIGIBLE", decision.reasonCode());
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setAccountEnabled(true);
        user.setPushNotificationsEnabled(true);
        user.setMealRemindersEnabled(true);
        user.setHydrationRemindersEnabled(true);
        user.setStepRemindersEnabled(true);
        user.setFastingRemindersEnabled(true);
        user.setMarketingNotificationsEnabled(false);
        user.setTimeZone("Europe/Dublin");
        return user;
    }

    @Test
    void allEventTypesRemainEligibleThroughoutTheDayWithLegacyQuietHours() {
        UserEntity user = user();
        user.setMarketingNotificationsEnabled(true);
        user.setNotificationQuietHoursStart(LocalTime.of(22, 0));
        user.setNotificationQuietHoursEnd(LocalTime.of(8, 0));
        for (int hour = 0; hour < 24; hour++) {
            Instant at = Instant.parse("2026-09-14T00:00:00Z").plusSeconds(hour * 3600L);
            for (NotificationEventType event : NotificationEventType.values()) {
                var decision = evaluator.evaluate(user, event, Set.of(NotificationDeliveryChannel.PUSH), at);
                assertTrue(decision.allows(NotificationDeliveryChannel.PUSH), event + " at " + at);
                assertEquals(at, decision.pushAvailableAt());
            }
        }
    }
}
