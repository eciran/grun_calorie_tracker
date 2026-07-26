package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.LocaleConfig;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "grun.ai.completion-notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiCompletionNotificationService {

    private static final List<AiRequestStatus> COMPLETED_STATUSES =
            List.of(AiRequestStatus.DRAFT_CREATED, AiRequestStatus.FAILED);

    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryService pushDeliveryService;
    private final MessageSource messageSource;

    @Scheduled(fixedDelayString = "${grun.ai.completion-notifications.scan-interval-ms:15000}")
    @Transactional
    public int publishPendingNotifications() {
        List<AiRequestHistoryEntity> pending = aiRequestHistoryRepository.findPendingCompletionNotifications(
                COMPLETED_STATUSES,
                PageRequest.of(0, 50));
        LocalDateTime now = LocalDateTime.now();
        for (AiRequestHistoryEntity history : pending) {
            PreferredLanguage language = history.getUser() == null
                    ? PreferredLanguage.EN
                    : history.getUser().getPreferredLanguage();
            NotificationCopy copy = notificationCopy(history.getRequestType(), history.getStatus(), language);
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(history.getUser());
            notification.setType(history.getStatus() == AiRequestStatus.FAILED ? "ai_request_failed" : "ai_request_ready");
            notification.setTitle(copy.title());
            notification.setMessage(copy.message());
            notification.setSeverity(history.getStatus() == AiRequestStatus.FAILED ? "WARNING" : "INFO");
            notification.setSource("AI_PROCESSING");
            notification.setTargetType("AI_REQUEST");
            notification.setTargetId(String.valueOf(history.getId()));
            notification.setTargetRoute(copy.route());
            notification.setPrimaryAction("VIEW_AI_RESULT");
            notification.setIsRead(false);
            notification.setCreatedAt(now);
            NotificationEntity saved = notificationRepository.save(notification);
            history.setCompletionNotifiedAt(now);
            pushDeliveryService.deliver(saved);
        }
        aiRequestHistoryRepository.saveAll(pending);
        return pending.size();
    }

    private NotificationCopy notificationCopy(AiRequestType type, AiRequestStatus status, PreferredLanguage language) {
        Locale locale = language == PreferredLanguage.TR ? LocaleConfig.TURKISH : LocaleConfig.ENGLISH;
        String labelKey = switch (type) {
            case VOICE_FOOD_LOG -> "notification.ai.label.voice-food-log";
            case PHOTO_MEAL_LOG -> "notification.ai.label.photo-meal-log";
            case AI_RECIPE_GENERATION -> "notification.ai.label.recipe-generation";
            case AI_MEAL_PREPARATION_GUIDE -> "notification.ai.label.preparation-guide";
            case AI_NUTRITION_PLAN -> "notification.ai.label.nutrition-plan";
            case AI_WORKOUT_PLAN -> "notification.ai.label.workout-plan";
            case AI_DAILY_INSIGHT, AI_WEEKLY_INSIGHT -> "notification.ai.label.insight";
        };
        String label = messageSource.getMessage(labelKey, null, labelKey, locale);
        String route = switch (type) {
            case VOICE_FOOD_LOG, PHOTO_MEAL_LOG -> "ai-draft-review";
            case AI_RECIPE_GENERATION -> "ai-recipe-assistant";
            case AI_MEAL_PREPARATION_GUIDE -> "meal-plans";
            case AI_NUTRITION_PLAN -> "ai-nutrition-plan";
            case AI_WORKOUT_PLAN -> "ai-workout-planner";
            case AI_DAILY_INSIGHT, AI_WEEKLY_INSIGHT -> "ai-insights";
        };
        if (status == AiRequestStatus.FAILED) {
            return new NotificationCopy(
                    messageSource.getMessage("notification.ai.failed.title", null, "Request could not be completed", locale),
                    messageSource.getMessage("notification.ai.failed.message", new Object[]{label}, "Request could not be completed", locale),
                    route);
        }
        return new NotificationCopy(
                messageSource.getMessage("notification.ai.ready.title", new Object[]{label}, "AI result is ready", locale),
                messageSource.getMessage("notification.ai.ready.message", null, "AI result is ready to review", locale),
                route);
    }

    private record NotificationCopy(String title, String message, String route) {}
}