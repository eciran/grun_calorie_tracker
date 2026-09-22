package com.grun.calorietracker.service.notification;

import com.grun.calorietracker.config.NotificationProducerMigrationProperties;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.StepGoalEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WaterReminderSettingsEntity;
import com.grun.calorietracker.enums.NotificationDeliveryChannel;
import com.grun.calorietracker.enums.NotificationEventType;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BehaviorReminderNotificationService {

    private static final Set<NotificationDeliveryChannel> IN_APP_AND_PUSH = Set.of(
            NotificationDeliveryChannel.IN_APP,
            NotificationDeliveryChannel.PUSH
    );

    private final NotificationOrchestrationService orchestrationService;
    private final UserTimeZoneSupport userTimeZoneSupport;
    private final NotificationProducerMigrationProperties migrationProperties;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryService pushDeliveryService;

    /**
     * Keeps the pre-migration step eligibility contract intact while allowing the
     * shared policy engine to record preference suppressions after cutover.
     */
    public boolean shouldEvaluateStep(UserEntity user) {
        if (user == null) {
            return false;
        }
        return migrationProperties.isStepEnabled()
                || (Boolean.TRUE.equals(user.getPushNotificationsEnabled())
                && Boolean.TRUE.equals(user.getStepRemindersEnabled()));
    }

    public void enqueueWater(
            WaterReminderSettingsEntity settings,
            LocalDateTime userNow,
            String title,
            String message
    ) {
        requireId(settings == null ? null : settings.getId(), "Water reminder settings");
        UserEntity user = requireUser(settings.getUser());
        if (!migrationProperties.isWaterEnabled()) {
            deliverLegacy(user, NotificationEventType.WATER_REMINDER_DUE, "WATER_REMINDER", "WATER_TRACKING",
                    null, "water", "QUICK_ADD_WATER", 250, userNow, title, message);
            return;
        }
        int intervalMinutes = settings.getIntervalMinutes() == null ? 120 : settings.getIntervalMinutes();
        LocalTime endTime = settings.getEndTime() == null ? LocalTime.of(21, 0) : settings.getEndTime();
        orchestrationService.enqueue(new NotificationOrchestrationRequest(
                user,
                NotificationEventType.WATER_REMINDER_DUE,
                "WATER_REMINDER",
                intervalOccurrenceKey("water", settings.getId(), settings.getLastReminderAt(), intervalMinutes, userNow),
                title,
                message,
                "INFO",
                "WATER_TRACKING",
                String.valueOf(settings.getId()),
                "water",
                "QUICK_ADD_WATER",
                Map.of(),
                IN_APP_AND_PUSH,
                toInstant(user, userNow),
                toInstant(user, userNow.toLocalDate().atTime(endTime)),
                250
        ));
    }

    public void enqueueStep(
            StepGoalEntity goal,
            LocalDateTime userNow,
            String title,
            String message
    ) {
        requireId(goal == null ? null : goal.getId(), "Step goal");
        UserEntity user = requireUser(goal.getUser());
        if (!migrationProperties.isStepEnabled()) {
            deliverLegacy(user, NotificationEventType.STEP_REMINDER_DUE, "STEP_REMINDER", "STEP_TRACKING",
                    null, "steps", "VIEW_STEPS", null, userNow, title, message);
            return;
        }
        int intervalMinutes = goal.getReminderIntervalMinutes() == null ? 120 : goal.getReminderIntervalMinutes();
        LocalTime endTime = goal.getReminderEndTime() == null ? LocalTime.of(21, 0) : goal.getReminderEndTime();
        orchestrationService.enqueue(new NotificationOrchestrationRequest(
                user,
                NotificationEventType.STEP_REMINDER_DUE,
                "STEP_REMINDER",
                intervalOccurrenceKey("step", goal.getId(), goal.getLastReminderAt(), intervalMinutes, userNow),
                title,
                message,
                "INFO",
                "STEP_TRACKING",
                String.valueOf(goal.getId()),
                "steps",
                "VIEW_STEPS",
                Map.of(),
                IN_APP_AND_PUSH,
                toInstant(user, userNow),
                toInstant(user, userNow.toLocalDate().atTime(endTime))
        ));
    }

    public void enqueueFasting(
            FastingSessionEntity session,
            LocalDateTime userNow,
            String title,
            String message
    ) {
        requireId(session == null ? null : session.getId(), "Fasting session");
        UserEntity user = requireUser(session.getUser());
        if (!migrationProperties.isBasicFastingEnabled()) {
            deliverLegacy(user, NotificationEventType.FASTING_REMINDER_DUE, "FASTING_REMINDER", "FASTING_SESSION",
                    String.valueOf(session.getId()), "fasting", "VIEW_FASTING", null, userNow, title, message);
            return;
        }
        LocalDateTime targetEndAt = session.getTargetEndAt();
        if (targetEndAt == null || targetEndAt.isBefore(userNow)) {
            throw new IllegalArgumentException("Fasting reminder target end is invalid.");
        }
        Instant eligibleAt = toInstant(user, userNow);
        Instant expiresAt = toInstant(user, targetEndAt);
        if (!expiresAt.isAfter(eligibleAt)) {
            expiresAt = eligibleAt.plusSeconds(60);
        }
        orchestrationService.enqueue(new NotificationOrchestrationRequest(
                user,
                NotificationEventType.FASTING_REMINDER_DUE,
                "FASTING_REMINDER",
                "fasting-session:" + session.getId() + ":nearing-completion",
                title,
                message,
                "INFO",
                "FASTING_SESSION",
                String.valueOf(session.getId()),
                "fasting",
                "VIEW_FASTING",
                Map.of(),
                IN_APP_AND_PUSH,
                eligibleAt,
                expiresAt
        ));
    }

    private void deliverLegacy(
            UserEntity user,
            NotificationEventType eventType,
            String source,
            String targetType,
            String targetId,
            String targetRoute,
            String primaryAction,
            Integer actionAmountMl,
            LocalDateTime userNow,
            String title,
            String message
    ) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setType(eventType.definitionKey());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setSeverity("INFO");
        notification.setSource(source);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setTargetRoute(targetRoute);
        notification.setPrimaryAction(primaryAction);
        notification.setActionAmountMl(actionAmountMl);
        notification.setVisibleInApp(true);
        notification.setIsRead(false);
        // Notification API treats persisted timestamps as UTC, not user-local time.
        notification.setCreatedAt(LocalDateTime.ofInstant(toInstant(user, userNow), ZoneOffset.UTC));
        pushDeliveryService.deliver(notificationRepository.save(notification));
    }

    private String intervalOccurrenceKey(
            String prefix,
            Long ownerId,
            LocalDateTime lastReminderAt,
            int intervalMinutes,
            LocalDateTime userNow
    ) {
        if (lastReminderAt == null) {
            return prefix + ":" + ownerId + ":initial:" + userNow.toLocalDate();
        }
        return prefix + ":" + ownerId + ":due:" + lastReminderAt.plusMinutes(intervalMinutes);
    }

    private Instant toInstant(UserEntity user, LocalDateTime value) {
        return value.atZone(userTimeZoneSupport.zoneId(user)).toInstant();
    }

    private UserEntity requireUser(UserEntity user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Behavior reminder user is required.");
        }
        return user;
    }

    private void requireId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(label + " id is required.");
        }
    }
}
