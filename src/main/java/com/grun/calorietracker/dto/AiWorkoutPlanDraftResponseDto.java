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
    private String schemaVersion = "ai_response_v2";
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private String name;
    private String summary;
    private Boolean reviewRequired = true;
    private Double confidence;
    private Integer qualityScore;
    private String estimatedUncertainty;
    private List<String> reviewReasons = new ArrayList<>();
    private Integer aiRemainingThisPeriod;
    private List<AiWorkoutPlanDayDto> days = new ArrayList<>();
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
