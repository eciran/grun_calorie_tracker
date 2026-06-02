package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class GdprDataExportDto {
    private LocalDateTime exportedAt;
    private String email;
    private String name;
    private UserRole role;
    private MarketRegion marketRegion;
    private PreferredLanguage preferredLanguage;
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
    private Long consentCount;
    private Long aiRequestCount;
    private SubscriptionSnapshotDto subscription;
    private List<LinkedIdentityDto> linkedIdentities;
    private List<ConsentExportDto> consents;
    private List<FoodLogExportDto> foodLogs;
    private List<ExerciseLogExportDto> exerciseLogs;
    private List<ProgressLogExportDto> progressLogs;
    private List<FoodDiaryNoteExportDto> foodDiaryNotes;
    private List<MealTemplateExportDto> mealTemplates;
    private List<FavoriteFoodExportDto> favoriteFoods;
    private List<HealthConnectionExportDto> healthConnections;
    private List<HealthMetricExportDto> healthMetrics;
    private List<NotificationExportDto> notifications;
    private List<AiRequestExportDto> aiRequests;
    private List<SubscriptionEventExportDto> subscriptionEvents;

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
    public static class ExerciseLogExportDto {
        private Long id;
        private String exerciseName;
        private Integer durationMinutes;
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
