package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI-generated workout plan draft. It is not active until the user confirms it.")
public class AiWorkoutPlanDraftResponseDto implements AiUsageMetadataCarrier {
    private Long requestId;
    private String schemaVersion = "ai_response_v3";
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private String name;
    private String summary;
    @Schema(description = "Stable result type used by the mobile UI to present this as an AI-generated workout plan draft.", example = "AI_WORKOUT_PLAN_DRAFT")
    private String resultType = "AI_WORKOUT_PLAN_DRAFT";
    @Schema(description = "Short premium-facing explanation shown near the top of the workout result.")
    private String userMessage;
    @Schema(description = "Polished professional summary explaining the plan strategy, progression, and safety caveats.")
    private String professionalSummary;
    @Schema(description = "Plain-language assumptions behind the plan, such as equipment, level, limitations, or missing signals.")
    private List<String> assumptions = new ArrayList<>();
    @Schema(description = "Concrete next actions before starting the plan.")
    private List<String> nextBestActions = new ArrayList<>();
    private Boolean reviewRequired = true;
    private Double confidence;
    private Integer qualityScore;
    private String estimatedUncertainty;
    private List<String> reviewReasons = new ArrayList<>();
    private Integer aiRemainingThisPeriod;
    private List<AiWorkoutPlanDayDto> days = new ArrayList<>();
    @Schema(description = "Training principles that explain how the user should progress safely.")
    private List<String> trainingPrinciples = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
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