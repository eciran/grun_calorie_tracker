package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.WeeklyWorkoutFrequency;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GdprDataExportDto {
    private LocalDateTime exportedAt;
    private String email;
    private String name;
    private UserRole role;
    private MarketRegion marketRegion;
    private LocalDate birthDate;
    private CountryCode countryCode;
    private PreferredLanguage preferredLanguage;
    private String timeZone;
    private Boolean emailVerified;
    private LocalDateTime latestFoodLogAt;
    private LocalDateTime latestExerciseLogAt;
    private LocalDateTime latestProgressLogAt;
    private Long foodLogCount;
    private Long exerciseLogCount;
    private Long progressLogCount;
    private Long notificationCount;
    private Long linkedIdentityCount;
    private Long mealTemplateCount;
    private Long favoriteCount;
    private Long healthMetricCount;
    private Long sleepSessionCount;
    private Long consentCount;
    private Long aiRequestCount;
    private Long waterLogCount;
    private Long fastingSessionCount;
    private Long mealPlanCount;
    private Long recipeLogCount;
    private Long recipeInteractionCount;
    private Long failedBarcodeScanCount;
    private Long productCorrectionSuggestionCount;
    private Long productAnalyticsEventCount;
    private SubscriptionSnapshotDto subscription;
    private UserNutritionPreferenceDto nutritionPreferences;
    private FitnessPreferenceExportDto fitnessPreferences;
    private List<LinkedIdentityDto> linkedIdentities;
    private List<ConsentExportDto> consents;
    private List<FoodLogExportDto> foodLogs;
    private List<WaterLogExportDto> waterLogs;
    private FastingPlanExportDto fastingPlan;
    private List<FastingSessionExportDto> fastingSessions;
    private List<ExerciseLogExportDto> exerciseLogs;
    private List<ProgressLogExportDto> progressLogs;
    private List<FoodDiaryNoteExportDto> foodDiaryNotes;
    private List<MealTemplateExportDto> mealTemplates;
    private List<MealPlanExportDto> mealPlans;
    private List<FavoriteFoodExportDto> favoriteFoods;
    private List<HealthConnectionExportDto> healthConnections;
    private List<HealthMetricExportDto> healthMetrics;
    private SleepGoalExportDto sleepGoal;
    private List<SleepSessionExportDto> sleepSessions;
    private List<NotificationExportDto> notifications;
    private List<AiRequestExportDto> aiRequests;
    private List<RecipeLogExportDto> recipeLogs;
    private List<RecipeInteractionExportDto> recipeInteractions;
    private List<FailedBarcodeScanExportDto> failedBarcodeScans;
    private List<ProductCorrectionSuggestionExportDto> productCorrectionSuggestions;
    private List<ProductAnalyticsEventExportDto> productAnalyticsEvents;
    private List<SubscriptionEventExportDto> subscriptionEvents;
    private AdvancedFastingExportDto advancedFasting;
    private List<ProductReviewCaseExportDto> productReviewCases;

    public record ProductReviewCaseExportDto(
            Long id, String source, String status, String marketRegion, String barcode,
            String resolutionMode, String riskLevel, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<ProductReviewAssetExportDto> assets) {}

    public record ProductReviewAssetExportDto(
            Long id, String assetType, String contentType, Long sizeBytes, Integer width, Integer height,
            String uploadState, LocalDateTime expiresAt, String deletionState, LocalDateTime deletedAt) {}


    @Data
    @AllArgsConstructor
    public static class AdvancedFastingExportDto {
        private List<AdvancedFastingProgramExportDto> programs;
        private AdvancedFastingReminderSettingsExportDto reminderSettings;
        private List<FastingScheduleExceptionExportDto> scheduleExceptions;
        private List<FastingHistoryCorrectionExportDto> historyCorrections;
        private List<FastingScheduleExceptionAuditExportDto> exceptionAudits;
    }

    public record AdvancedFastingProgramExportDto(Long id, String name, String status, LocalDate effectiveFrom,
            LocalDate effectiveUntil, Integer currentVersionNumber, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record AdvancedFastingReminderSettingsExportDto(Boolean enabled, Boolean preStartEnabled,
            Boolean startEnabled, Boolean nearingCompletionEnabled, Boolean completionEnabled,
            Boolean missedPlanEnabled, Integer preStartMinutes, Integer nearingCompletionMinutes,
            LocalDateTime updatedAt) {}
    public record FastingScheduleExceptionExportDto(Long id, Long programId, LocalDate sourceDate,
            LocalDate targetDate, String exceptionType, String movedStartTime, LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
    public record FastingHistoryCorrectionExportDto(Long id, Long sessionId, String action,
            LocalDateTime oldStartedAt, LocalDateTime oldEndedAt, LocalDateTime newStartedAt,
            LocalDateTime newEndedAt, LocalDateTime createdAt) {}
    public record FastingScheduleExceptionAuditExportDto(Long id, Long exceptionId, LocalDate sourceDate,
            String action, LocalDateTime createdAt) {}

    @Data
    @AllArgsConstructor
    public static class FitnessPreferenceExportDto {
        private WeeklyWorkoutFrequency weeklyWorkoutFrequency;
        private LocalDateTime updatedAt;
    }
    @Data
    @AllArgsConstructor
    public static class SubscriptionSnapshotDto {
        private String plan;
        private String status;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Integer aiMonthlyQuota;
        private Integer aiAddonQuota;
        private Integer aiUsedThisPeriod;
    }

    @Data
    @AllArgsConstructor
    public static class ConsentExportDto {
        private Long id;
        private String consentType;
        private String version;
        private String status;
        private String source;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class FoodLogExportDto {
        private Long id;
        private String foodName;
        private String barcode;
        private String mealType;
        private Double portionSize;
        private String portionUnit;
        private Double normalizedPortionGrams;
        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private LocalDateTime logDate;
    }

    @Data
    @AllArgsConstructor
    public static class WaterLogExportDto {
        private Long id;
        private LocalDate logDate;
        private Integer amountMl;
        private String source;
        private LocalDateTime loggedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class FastingPlanExportDto {
        private Long id;
        private String planType;
        private Integer fastingHours;
        private Integer eatingWindowHours;
        private String preferredStartTime;
        private Boolean active;
        private Boolean reminderEnabled;
        private String note;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class FastingSessionExportDto {
        private Long id;
        private String status;
        private LocalDate fastingDate;
        private LocalDateTime startedAt;
        private LocalDateTime targetEndAt;
        private LocalDateTime endedAt;
        private Integer targetMinutes;
        private Integer actualMinutes;
        private Boolean targetReached;
        private String note;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class ExerciseLogExportDto {
        private Long id;
        private String exerciseName;
        private Integer durationMinutes;
        private String measurementType;
        private Integer setCount;
        private Integer reps;
        private Double weightKg;
        private Double distanceKm;
        private Double caloriesBurned;
        private LocalDateTime logDate;
        private String source;
        private String externalId;
    }

    @Data
    @AllArgsConstructor
    public static class ProgressLogExportDto {
        private Long id;
        private LocalDateTime logDate;
        private Double weight;
        private Integer calorieIntake;
        private Double proteinIntake;
        private Double fatIntake;
        private Double carbIntake;
        private String note;
    }

    @Data
    @AllArgsConstructor
    public static class FoodDiaryNoteExportDto {
        private Long id;
        private LocalDate diaryDate;
        private String note;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class MealTemplateExportDto {
        private Long id;
        private String name;
        private String mealType;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class MealPlanExportDto {
        private Long id;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;

        private String generationMode;
        private Long workoutPlanId;
        private Long sourceAiRequestId;
        private String schemaVersion;
        private String promptVersion;
        private Integer itemCount;
        private List<MealPlanItemExportDto> items;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class MealPlanItemExportDto {
        private Long id;
        private LocalDate planDate;
        private String mealType;
        private String itemType;
        private Long foodItemId;
        private Long recipeId;
        private Double portionSize;
        private String portionUnit;
        private String snapshotName;
        private String snapshotDescription;
        private String shortPreparationState;
        private MealPlanNutritionSnapshotDto snapshotNutrition;
        private String allergensPayload;
        private String warningsPayload;
        private String assumptionsPayload;
        private String workoutRelation;
        private Long sourceAiRequestId;
        private String schemaVersion;
        private String promptVersion;
    }

    @Data
    @AllArgsConstructor
    public static class FavoriteFoodExportDto {
        private Long id;
        private String foodName;
        private String barcode;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class HealthConnectionExportDto {
        private Long id;
        private String provider;
        private String status;
        private LocalDateTime connectedAt;
        private LocalDateTime disconnectedAt;
        private LocalDateTime lastSyncAt;
        private String deviceModel;
        private String appVersion;
    }

    @Data
    @AllArgsConstructor
    public static class HealthMetricExportDto {
        private Long id;
        private String provider;
        private Integer steps;
        private Integer heartRate;
        private Double sleepHours;
        private Double caloriesBurned;
        private Double distanceMeters;
        private LocalDateTime recordedAt;
        private String source;
    }

    @Data
    @AllArgsConstructor
    public static class SleepGoalExportDto {
        private Integer targetMinutes;
        private String preferredBedtime;
        private String preferredWakeTime;
    }

    @Data
    @AllArgsConstructor
    public static class SleepSessionExportDto {
        private Long id;
        private Instant startedAt;
        private Instant endedAt;
        private LocalDate sleepDate;
        private Integer durationMinutes;
        private String timeZone;
        private String provider;
        private String externalId;
        private Integer qualityScore;
        private String qualityConfidence;
        private String note;
    }

    @Data
    @AllArgsConstructor
    public static class NotificationExportDto {
        private Long id;
        private String message;
        private String type;
        private Boolean read;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class AiRequestExportDto {
        private Long id;
        private String requestType;
        private String provider;
        private String model;
        private String status;
        private Boolean quotaConsumed;
        private Long latencyMs;
        private Integer totalTokens;
        private Double estimatedCost;
        private String costCurrency;
        private String correctionSummary;
        private String rejectionReason;
        private String rejectionFeedback;
        private Integer quotaRefundedAmount;
        private String quotaRefundReason;
        private String quotaRefundedBy;
        private LocalDateTime createdAt;
        private LocalDateTime confirmedAt;
        private LocalDateTime rejectedAt;
        private LocalDateTime quotaRefundedAt;
    }

    @Data
    @AllArgsConstructor
    public static class RecipeLogExportDto {
        private Long id;
        private String recipeName;
        private String mealType;
        private Double servingGrams;
        private Double servingCount;
        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private LocalDateTime logDate;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class RecipeInteractionExportDto {
        private Long id;
        private String recipeName;
        private Boolean saved;
        private Boolean favorite;
        private Integer rating;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class FailedBarcodeScanExportDto {
        private Long id;
        private String barcode;
        private String marketRegion;
        private Long scanCount;
        private LocalDateTime firstScannedAt;
        private LocalDateTime lastScannedAt;
    }

    @Data
    @AllArgsConstructor
    public static class ProductCorrectionSuggestionExportDto {
        private Long id;
        private String foodName;
        private Double suggestedCalories;
        private Double suggestedProtein;
        private Double suggestedCarbs;
        private Double suggestedFat;
        private String note;
        private String imageUrl;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class ProductAnalyticsEventExportDto {
        private Long id;
        private String eventType;
        private String surface;
        private String marketRegion;
        private String language;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Long durationMs;
        private String targetType;
        private Long targetId;
        private String metadataJson;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class SubscriptionEventExportDto {
        private Long id;
        private String provider;
        private String eventType;
        private String productId;
        private String entitlementIds;
        private String transactionId;
        private String originalTransactionId;
        private String status;
        private LocalDateTime receivedAt;
        private LocalDateTime processedAt;
    }
}
