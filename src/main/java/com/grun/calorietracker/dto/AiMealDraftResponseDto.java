package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI meal logging draft response. The draft must be reviewed and confirmed by the user before diary writes.")
public class AiMealDraftResponseDto implements AiUsageMetadataCarrier {
    private Long requestId;
    private String schemaVersion = "ai_response_v3";
    private AiRequestType requestType;
    private AiProvider provider;
    private String model;
    private AiRequestStatus status;
    @Schema(description = "Normalized language used for every user-visible AI-generated string.", example = "en")
    private String outputLanguage;
    private String suggestedMealType;
    private LocalDateTime suggestedLogDate;
    private String summary;
    @Schema(description = "Stable result type used by the mobile UI to present this as an AI-estimated snapshot rather than a verified catalog result.", example = "AI_SNAPSHOT")
    private String resultType = "AI_SNAPSHOT";
    @Schema(description = "Short premium-facing explanation shown near the top of the AI result.")
    private String userMessage;
    @Schema(description = "Polished professional summary explaining what the AI found and how confident the draft is.")
    private String professionalSummary;
    @Schema(description = "Plain-language assumptions behind the estimate, such as portion or ingredient uncertainty.")
    private List<String> assumptions = new ArrayList<>();
    @Schema(description = "Concrete next actions the user can take before confirming the draft.")
    private List<String> nextBestActions = new ArrayList<>();
    private AiSafetyResultDto safety;
    private Double confidence;
    private Integer qualityScore;
    private String estimatedUncertainty;
    private AiUxContractDto ux;
    private List<String> reviewReasons = new ArrayList<>();
    private List<AiMealDraftItemDto> items = new ArrayList<>();
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
