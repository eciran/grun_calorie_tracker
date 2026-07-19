package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiNutritionPlanDraftResponseDto implements AiUsageMetadataCarrier {
    private Long requestId;
    private String schemaVersion = "ai_nutrition_plan_v1";
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private NutritionPlanGenerationMode generationMode;
    private Long workoutPlanId;
    private String workoutScheduleVersion;
    private LocalDateTime workoutScheduleUpdatedAt;
    private String name;
    private String summary;
    private String professionalSummary;
    private LocalDate startDate;
    private LocalDate endDate;
    @Valid
    private MealPlanNutritionSnapshotDto dailyTarget;
    @Valid
    private List<AiNutritionPlanDayDto> days = new ArrayList<>();
    private List<String> assumptions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> nextBestActions = new ArrayList<>();
    private Boolean reviewRequired = true;
    private Double confidence;
    private Integer qualityScore;
    private String estimatedUncertainty;
    @Schema(description = "Exact number of AI credits charged by the backend for this request.", example = "8")
    private Integer quotaConsumedAmount;
    @Schema(description = "Plan credits remaining after this request.", example = "10")
    private Integer aiBaseRemainingThisPeriod;
    @Schema(description = "One-off add-on credits remaining after this request.", example = "0")
    private Integer aiAddonRemainingThisPeriod;
    @Schema(description = "Total plan and add-on credits remaining after this request.", example = "10")
    private Integer aiRemainingThisPeriod;
    @JsonIgnore
    private Integer promptTokens;
    @JsonIgnore
    private Integer completionTokens;
    @JsonIgnore
    private Integer totalTokens;
    @JsonIgnore
    private Double estimatedCost;
    @JsonIgnore
    private String costCurrency;
}