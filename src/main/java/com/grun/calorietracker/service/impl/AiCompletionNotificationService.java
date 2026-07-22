package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiCompletionNotificationService {

    private static final List<AiRequestStatus> COMPLETED_STATUSES =
            List.of(AiRequestStatus.DRAFT_CREATED, AiRequestStatus.FAILED);

    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryService pushDeliveryService;

    @Scheduled(fixedDelayString = "${grun.ai.completion-notifications.scan-interval-ms:15000}")
    @Transactional
    public int publishPendingNotifications() {
        List<AiRequestHistoryEntity> pending = aiRequestHistoryRepository.findPendingCompletionNotifications(
                COMPLETED_STATUSES,
                PageRequest.of(0, 50));
        LocalDateTime now = LocalDateTime.now();
        for (AiRequestHistoryEntity history : pending) {
            NotificationCopy copy = notificationCopy(history.getRequestType(), history.getStatus());
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

    private NotificationCopy notificationCopy(AiRequestType type, AiRequestStatus status) {
        String label = switch (type) {
            case VOICE_FOOD_LOG -> "voice meal";
            case PHOTO_MEAL_LOG -> "meal scan";
            case AI_RECIPE_GENERATION -> "recipe draft";
            case AI_MEAL_PREPARATION_GUIDE -> "preparation guide";
            case AI_NUTRITION_PLAN -> "nutrition plan";
            case AI_WORKOUT_PLAN -> "workout plan";
            case AI_DAILY_INSIGHT, AI_WEEKLY_INSIGHT -> "coaching update";
        };
        String route = switch (type) {
            case VOICE_FOOD_LOG, PHOTO_MEAL_LOG -> "ai-draft-review";
            case AI_RECIPE_GENERATION -> "ai-recipe-assistant";
            case AI_MEAL_PREPARATION_GUIDE -> "meal-plans";
            case AI_NUTRITION_PLAN -> "ai-nutrition-plan";
            case AI_WORKOUT_PLAN -> "ai-workout-planner";
            case AI_DAILY_INSIGHT, AI_WEEKLY_INSIGHT -> "ai-insights";
        };
        if (status == AiRequestStatus.FAILED) {
            return new NotificationCopy("That one didn’t land", "We couldn’t finish your " + label + ". Open it to review or try again.", route);
        }
        return new NotificationCopy("Your " + label + " is ready!", "Fresh from GRun AI — open your result whenever you’re ready.", route);
    }

    private record NotificationCopy(String title, String message, String route) {}
}