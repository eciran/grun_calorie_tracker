package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GdprDataExportDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.DeviceDataEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.FastingPlanEntity;
import com.grun.calorietracker.entity.FastingSessionEntity;
import com.grun.calorietracker.entity.FailedBarcodeScanEntity;
import com.grun.calorietracker.entity.FoodDiaryNoteEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.HealthConnectionEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealTemplateEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.ProductCorrectionSuggestionEntity;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.RecipeUserInteractionEntity;
import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.UserConsentEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFavoriteEntity;
import com.grun.calorietracker.entity.WaterLogEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AppliedPromoRepository;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.DeviceDataRepository;
import com.grun.calorietracker.repository.EmailVerificationTokenRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FastingPlanRepository;
import com.grun.calorietracker.repository.FastingSessionRepository;
import com.grun.calorietracker.repository.FailedBarcodeScanRepository;
import com.grun.calorietracker.repository.FederatedIdentityRepository;
import com.grun.calorietracker.repository.FoodDiaryNoteRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.HealthConnectionRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.MealTemplateRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.PasswordResetTokenRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.ProductCorrectionSuggestionRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.repository.RefreshTokenRepository;
import com.grun.calorietracker.repository.StepGoalRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.repository.UserAchievementRepository;
import com.grun.calorietracker.repository.UserConsentRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.UserSubscriptionEntitlementRepository;
import com.grun.calorietracker.repository.WaterLogRepository;
import com.grun.calorietracker.repository.WaterReminderSettingsRepository;
import com.grun.calorietracker.service.AccountGdprService;
import com.grun.calorietracker.service.AccountIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountGdprServiceImpl implements AccountGdprService {

    private static final String DELETE_CONFIRM_TEXT = "DELETE_MY_ACCOUNT";

    private final UserRepository userRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ExerciseLogRepository exerciseLogRepository;
    private final ProgressLogRepository progressLogRepository;
    private final NotificationRepository notificationRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final MealTemplateRepository mealTemplateRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final DeviceDataRepository deviceDataRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AccountIdentityService accountIdentityService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final GoalRepository goalRepository;
    private final FoodDiaryNoteRepository foodDiaryNoteRepository;
    private final HealthConnectionRepository healthConnectionRepository;
    private final AppliedPromoRepository appliedPromoRepository;
    private final SubscriptionProviderEventRepository subscriptionProviderEventRepository;
    private final UserSubscriptionEntitlementRepository userSubscriptionEntitlementRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserConsentRepository userConsentRepository;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final WaterLogRepository waterLogRepository;
    private final WaterReminderSettingsRepository waterReminderSettingsRepository;
    private final FastingPlanRepository fastingPlanRepository;
    private final FastingSessionRepository fastingSessionRepository;
    private final StepGoalRepository stepGoalRepository;
    private final UserPushTokenRepository userPushTokenRepository;
    private final MealPlanRepository mealPlanRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final RecipeUserInteractionRepository recipeUserInteractionRepository;
    private final FailedBarcodeScanRepository failedBarcodeScanRepository;
    private final ProductCorrectionSuggestionRepository productCorrectionSuggestionRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public GdprDataExportDto exportMyData(String userEmail) {
        UserEntity user = findUser(userEmail);
        SubscriptionEntity subscription = subscriptionRepository.findByUser(user).orElse(null);
        List<LinkedIdentityDto> identities = accountIdentityService.listLinkedIdentities(userEmail);

        LocalDateTime latestFoodLog = foodLogsRepository.findTopByUserOrderByLogDateDesc(user)
                .map(FoodLogsEntity::getLogDate)
                .orElse(null);
        LocalDateTime latestExerciseLog = exerciseLogRepository.findTopByUserOrderByLogDateDesc(user)
                .map(ExerciseLogsEntity::getLogDate)
                .orElse(null);
        LocalDateTime latestProgressLog = progressLogRepository.findTopByUserOrderByLogDateDesc(user)
                .map(p -> p.getLogDate())
                .orElse(null);

        GdprDataExportDto.SubscriptionSnapshotDto subscriptionDto = subscription == null ? null
                : new GdprDataExportDto.SubscriptionSnapshotDto(
                subscription.getPlanType() == null ? null : subscription.getPlanType().name(),
                subscription.getStatus() == null ? null : subscription.getStatus().name(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getAiMonthlyQuota(),
                subscription.getAiAddonQuota(),
                subscription.getAiUsedThisPeriod()
        );

        return new GdprDataExportDto(
                LocalDateTime.now(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getMarketRegion(),
                user.getPreferredLanguage(),
                user.getTimeZone(),
                user.getEmailVerified(),
                latestFoodLog,
                latestExerciseLog,
                latestProgressLog,
                foodLogsRepository.countByUser(user),
                exerciseLogRepository.countByUser(user),
                progressLogRepository.countByUser(user),
                notificationRepository.countByUser(user),
                federatedIdentityRepository.countByUser(user),
                mealTemplateRepository.countByUser(user),
                userFavoriteRepository.countByUser(user),
                deviceDataRepository.countByUser(user),
                userConsentRepository.countByUser(user),
                aiRequestHistoryRepository.countByUser(user),
                waterLogRepository.countByUser(user),
                fastingSessionRepository.countByUser(user),
                mealPlanRepository.countByUser(user),
                recipeLogRepository.countByUser(user),
                recipeUserInteractionRepository.countByUser(user),
                failedBarcodeScanRepository.countByUser(user),
                productCorrectionSuggestionRepository.countByUser(user),
                productAnalyticsEventRepository.countByUser(user),
                subscriptionDto,
                identities,
                userConsentRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toConsentExport).toList(),
                foodLogsRepository.findByUser(user).stream().map(this::toFoodLogExport).toList(),
                waterLogRepository.findByUserOrderByLoggedAtAsc(user).stream().map(this::toWaterLogExport).toList(),
                fastingPlanRepository.findByUser(user).map(this::toFastingPlanExport).orElse(null),
                fastingSessionRepository.findByUserOrderByStartedAtAsc(user).stream().map(this::toFastingSessionExport).toList(),
                exerciseLogRepository.findByUser(user).stream().map(this::toExerciseLogExport).toList(),
                progressLogRepository.findByUserOrderByLogDateAsc(user).stream().map(this::toProgressLogExport).toList(),
                foodDiaryNoteRepository.findByUserOrderByDiaryDateAsc(user).stream().map(this::toFoodDiaryNoteExport).toList(),
                mealTemplateRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toMealTemplateExport).toList(),
                mealPlanRepository.findByUserOrderByStartDateDesc(user).stream().map(this::toMealPlanExport).toList(),
                userFavoriteRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toFavoriteFoodExport).toList(),
                healthConnectionRepository.findByUserOrderByProviderAsc(user).stream().map(this::toHealthConnectionExport).toList(),
                deviceDataRepository.findByUserOrderByRecordedAtAsc(user).stream().map(this::toHealthMetricExport).toList(),
                notificationRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toNotificationExport).toList(),
                aiRequestHistoryRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toAiRequestExport).toList(),
                recipeLogRepository.findByUserOrderByLogDateAsc(user).stream().map(this::toRecipeLogExport).toList(),
                recipeUserInteractionRepository.findByUserOrderByUpdatedAtDesc(user).stream().map(this::toRecipeInteractionExport).toList(),
                failedBarcodeScanRepository.findByUserOrderByLastScannedAtDesc(user).stream().map(this::toFailedBarcodeScanExport).toList(),
                productCorrectionSuggestionRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toProductCorrectionSuggestionExport).toList(),
                productAnalyticsEventRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toProductAnalyticsEventExport).toList(),
                subscriptionProviderEventRepository.findByUserOrderByReceivedAtDesc(user).stream().map(this::toSubscriptionEventExport).toList()
        );
    }

    @Override
    @Transactional
    public void anonymizeAndDeleteAccount(String userEmail, String confirmText, String currentPassword) {
        UserEntity user = findUser(userEmail);
        if (!DELETE_CONFIRM_TEXT.equalsIgnoreCase(confirmText == null ? "" : confirmText.trim())) {
            throw new IllegalArgumentException("confirmText must be DELETE_MY_ACCOUNT");
        }
        if (!Boolean.TRUE.equals(user.getPasswordSet())
                || currentPassword == null
                || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Current password is required to delete account data");
        }

        String anonymizedEmail = "deleted-" + user.getId() + "-" + UUID.randomUUID() + "@deleted.grun.local";
        String anonymizedAppUserId = "deleted-user:" + user.getId();
        user.setEmail(anonymizedEmail.toLowerCase(Locale.ROOT));
        user.setName("Deleted User");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setAge(null);
        user.setGender(null);
        user.setHeight(null);
        user.setWeight(null);
        user.setBodyFatPercentage(null);
        user.setBmi(null);
        user.setAvatarUrl(null);
        user.setEmailVerified(false);
        user.setPasswordSet(false);

        refreshTokenRepository.deleteByUser(user);
        passwordResetTokenRepository.deleteByUser(user);
        emailVerificationTokenRepository.deleteByUser(user);
        federatedIdentityRepository.deleteByUser(user);

        userFavoriteRepository.deleteByUser(user);
        foodLogsRepository.deleteByUser(user);
        waterLogRepository.deleteByUser(user);
        waterReminderSettingsRepository.deleteByUser(user);
        fastingSessionRepository.deleteByUser(user);
        fastingPlanRepository.deleteByUser(user);
        exerciseLogRepository.deleteByUser(user);
        progressLogRepository.deleteByUser(user);
        recipeLogRepository.deleteByUser(user);
        mealTemplateRepository.deleteByUser(user);
        mealPlanRepository.deleteByUser(user);
        foodDiaryNoteRepository.deleteByUser(user);
        notificationRepository.deleteByUser(user);
        userAchievementRepository.deleteByUser(user);
        deviceDataRepository.deleteByUser(user);
        stepGoalRepository.deleteByUser(user);
        userPushTokenRepository.deleteByUser(user);
        recipeUserInteractionRepository.deleteByUser(user);
        failedBarcodeScanRepository.deleteByUser(user);
        productCorrectionSuggestionRepository.deleteByUser(user);
        productAnalyticsEventRepository.deleteByUser(user);
        healthConnectionRepository.deleteByUser(user);
        goalRepository.deleteByUser(user);
        appliedPromoRepository.deleteByUser(user);
        subscriptionProviderEventRepository.anonymizeUserReferences(user, anonymizedAppUserId, "{}");
        aiRequestHistoryRepository.deleteByUser(user);
        userSubscriptionEntitlementRepository.deleteByUser(user);
        subscriptionRepository.deleteByUser(user);
        foodItemRepository.deleteByCreatedByUserAndIsCustomTrue(user);

        userRepository.save(user);
    }

    private UserEntity findUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private GdprDataExportDto.FoodLogExportDto toFoodLogExport(FoodLogsEntity log) {
        return new GdprDataExportDto.FoodLogExportDto(
                log.getId(),
                log.getFoodItem() == null ? null : log.getFoodItem().getName(),
                log.getFoodItem() == null ? null : log.getFoodItem().getBarcode(),
                log.getMealType(),
                log.getPortionSize(),
                log.getPortionUnit() == null ? null : log.getPortionUnit().name(),
                log.getNormalizedPortionGrams(),
                log.getSnapshotCalories(),
                log.getSnapshotProtein(),
                log.getSnapshotCarbs(),
                log.getSnapshotFat(),
                log.getLogDate()
        );
    }

    private GdprDataExportDto.WaterLogExportDto toWaterLogExport(WaterLogEntity log) {
        return new GdprDataExportDto.WaterLogExportDto(
                log.getId(),
                log.getLogDate(),
                log.getAmountMl(),
                log.getSource(),
                log.getLoggedAt(),
                log.getCreatedAt()
        );
    }

    private GdprDataExportDto.FastingPlanExportDto toFastingPlanExport(FastingPlanEntity plan) {
        return new GdprDataExportDto.FastingPlanExportDto(
                plan.getId(),
                plan.getPlanType() == null ? null : plan.getPlanType().name(),
                plan.getFastingHours(),
                plan.getEatingWindowHours(),
                plan.getPreferredStartTime() == null ? null : plan.getPreferredStartTime().toString(),
                plan.getActive(),
                plan.getReminderEnabled(),
                plan.getNote(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private GdprDataExportDto.FastingSessionExportDto toFastingSessionExport(FastingSessionEntity session) {
        return new GdprDataExportDto.FastingSessionExportDto(
                session.getId(),
                session.getStatus() == null ? null : session.getStatus().name(),
                session.getFastingDate(),
                session.getStartedAt(),
                session.getTargetEndAt(),
                session.getEndedAt(),
                session.getTargetMinutes(),
                session.getActualMinutes(),
                session.getTargetReached(),
                session.getNote(),
                session.getCreatedAt()
        );
    }

    private GdprDataExportDto.ConsentExportDto toConsentExport(UserConsentEntity consent) {
        return new GdprDataExportDto.ConsentExportDto(
                consent.getId(),
                consent.getConsentType() == null ? null : consent.getConsentType().name(),
                consent.getVersion(),
                consent.getStatus() == null ? null : consent.getStatus().name(),
                consent.getSource(),
                consent.getIpAddress(),
                consent.getUserAgent(),
                consent.getCreatedAt()
        );
    }

    private GdprDataExportDto.ExerciseLogExportDto toExerciseLogExport(ExerciseLogsEntity log) {
        return new GdprDataExportDto.ExerciseLogExportDto(
                log.getId(),
                log.getExerciseItem() == null ? null : log.getExerciseItem().getName(),
                log.getDurationMinutes(),
                log.getMeasurementType() == null ? null : log.getMeasurementType().name(),
                log.getSetCount(),
                log.getReps(),
                log.getWeightKg(),
                log.getDistanceKm(),
                log.getCaloriesBurned(),
                log.getLogDate(),
                log.getSource(),
                log.getExternalId()
        );
    }

    private GdprDataExportDto.ProgressLogExportDto toProgressLogExport(ProgressLogEntity log) {
        return new GdprDataExportDto.ProgressLogExportDto(
                log.getId(),
                log.getLogDate(),
                log.getWeight(),
                log.getCalorieIntake(),
                log.getProteinIntake(),
                log.getFatIntake(),
                log.getCarbIntake(),
                log.getNote()
        );
    }

    private GdprDataExportDto.FoodDiaryNoteExportDto toFoodDiaryNoteExport(FoodDiaryNoteEntity note) {
        return new GdprDataExportDto.FoodDiaryNoteExportDto(
                note.getId(),
                note.getDiaryDate(),
                note.getNote(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private GdprDataExportDto.MealTemplateExportDto toMealTemplateExport(MealTemplateEntity template) {
        return new GdprDataExportDto.MealTemplateExportDto(
                template.getId(),
                template.getName(),
                template.getMealType(),
                template.getCreatedAt()
        );
    }

    private GdprDataExportDto.MealPlanExportDto toMealPlanExport(MealPlanEntity plan) {
        return new GdprDataExportDto.MealPlanExportDto(
                plan.getId(),
                plan.getName(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus() == null ? null : plan.getStatus().name(),
                plan.getItems() == null ? 0 : plan.getItems().size(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private GdprDataExportDto.FavoriteFoodExportDto toFavoriteFoodExport(UserFavoriteEntity favorite) {
        return new GdprDataExportDto.FavoriteFoodExportDto(
                favorite.getId(),
                favorite.getFoodItem() == null ? null : favorite.getFoodItem().getName(),
                favorite.getFoodItem() == null ? null : favorite.getFoodItem().getBarcode(),
                favorite.getCreatedAt()
        );
    }

    private GdprDataExportDto.HealthConnectionExportDto toHealthConnectionExport(HealthConnectionEntity connection) {
        return new GdprDataExportDto.HealthConnectionExportDto(
                connection.getId(),
                connection.getProvider() == null ? null : connection.getProvider().name(),
                connection.getStatus() == null ? null : connection.getStatus().name(),
                connection.getConnectedAt(),
                connection.getDisconnectedAt(),
                connection.getLastSyncAt(),
                connection.getDeviceModel(),
                connection.getAppVersion()
        );
    }

    private GdprDataExportDto.HealthMetricExportDto toHealthMetricExport(DeviceDataEntity metric) {
        return new GdprDataExportDto.HealthMetricExportDto(
                metric.getId(),
                metric.getProvider() == null ? null : metric.getProvider().name(),
                metric.getSteps(),
                metric.getHeartRate(),
                metric.getSleepHours(),
                metric.getCaloriesBurned(),
                metric.getDistanceMeters(),
                metric.getRecordedAt(),
                metric.getSource()
        );
    }

    private GdprDataExportDto.NotificationExportDto toNotificationExport(NotificationEntity notification) {
        return new GdprDataExportDto.NotificationExportDto(
                notification.getId(),
                notification.getMessage(),
                notification.getType(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }

    private GdprDataExportDto.SubscriptionEventExportDto toSubscriptionEventExport(SubscriptionProviderEventEntity event) {
        return new GdprDataExportDto.SubscriptionEventExportDto(
                event.getId(),
                event.getProvider() == null ? null : event.getProvider().name(),
                event.getEventType(),
                event.getProductId(),
                event.getEntitlementIds(),
                event.getTransactionId(),
                event.getOriginalTransactionId(),
                event.getStatus() == null ? null : event.getStatus().name(),
                event.getReceivedAt(),
                event.getProcessedAt()
        );
    }

    private GdprDataExportDto.AiRequestExportDto toAiRequestExport(AiRequestHistoryEntity request) {
        return new GdprDataExportDto.AiRequestExportDto(
                request.getId(),
                request.getRequestType() == null ? null : request.getRequestType().name(),
                request.getProvider() == null ? null : request.getProvider().name(),
                request.getModel(),
                request.getStatus() == null ? null : request.getStatus().name(),
                request.getQuotaConsumed(),
                request.getLatencyMs(),
                request.getTotalTokens(),
                request.getEstimatedCost(),
                request.getCostCurrency(),
                request.getCorrectionSummary(),
                request.getRejectionReason() == null ? null : request.getRejectionReason().name(),
                request.getRejectionFeedback(),
                request.getQuotaRefundedAmount(),
                request.getQuotaRefundReason(),
                request.getQuotaRefundedBy(),
                request.getCreatedAt(),
                request.getConfirmedAt(),
                request.getRejectedAt(),
                request.getQuotaRefundedAt()
        );
    }

    private GdprDataExportDto.RecipeLogExportDto toRecipeLogExport(RecipeLogEntity log) {
        return new GdprDataExportDto.RecipeLogExportDto(
                log.getId(),
                log.getRecipe() == null ? null : log.getRecipe().getName(),
                log.getMealType(),
                log.getServingGrams(),
                log.getServingCount(),
                log.getSnapshotCalories(),
                log.getSnapshotProtein(),
                log.getSnapshotCarbs(),
                log.getSnapshotFat(),
                log.getLogDate(),
                log.getCreatedAt()
        );
    }

    private GdprDataExportDto.RecipeInteractionExportDto toRecipeInteractionExport(RecipeUserInteractionEntity interaction) {
        return new GdprDataExportDto.RecipeInteractionExportDto(
                interaction.getId(),
                interaction.getRecipe() == null ? null : interaction.getRecipe().getName(),
                interaction.getSaved(),
                interaction.getFavorite(),
                interaction.getRating(),
                interaction.getCreatedAt(),
                interaction.getUpdatedAt()
        );
    }

    private GdprDataExportDto.FailedBarcodeScanExportDto toFailedBarcodeScanExport(FailedBarcodeScanEntity scan) {
        return new GdprDataExportDto.FailedBarcodeScanExportDto(
                scan.getId(),
                scan.getBarcode(),
                scan.getMarketRegion() == null ? null : scan.getMarketRegion().name(),
                scan.getScanCount(),
                scan.getFirstScannedAt(),
                scan.getLastScannedAt()
        );
    }

    private GdprDataExportDto.ProductCorrectionSuggestionExportDto toProductCorrectionSuggestionExport(ProductCorrectionSuggestionEntity suggestion) {
        return new GdprDataExportDto.ProductCorrectionSuggestionExportDto(
                suggestion.getId(),
                suggestion.getFoodItem() == null ? null : suggestion.getFoodItem().getName(),
                suggestion.getSuggestedCalories(),
                suggestion.getSuggestedProtein(),
                suggestion.getSuggestedCarbs(),
                suggestion.getSuggestedFat(),
                suggestion.getNote(),
                suggestion.getImageUrl(),
                suggestion.getStatus() == null ? null : suggestion.getStatus().name(),
                suggestion.getCreatedAt()
        );
    }

    private GdprDataExportDto.ProductAnalyticsEventExportDto toProductAnalyticsEventExport(ProductAnalyticsEventEntity event) {
        return new GdprDataExportDto.ProductAnalyticsEventExportDto(
                event.getId(),
                event.getEventType() == null ? null : event.getEventType().name(),
                event.getSurface(),
                event.getMarketRegion(),
                event.getLanguage(),
                event.getStartedAt(),
                event.getCompletedAt(),
                event.getDurationMs(),
                event.getTargetType(),
                event.getTargetId(),
                event.getMetadataJson(),
                event.getCreatedAt()
        );
    }
}
