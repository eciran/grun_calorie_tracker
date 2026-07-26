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
@Schema(description = "Controlled AI coaching insight. It contains app-scoped observations and actions only.")
public class AiInsightResponseDto implements AiUsageMetadataCarrier {
    private Long requestId;
    private String schemaVersion = "ai_response_v3";
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private String title;
    private String summary;

    @Schema(description = "Backward-compatible short highlight strings for older clients.")
    private List<String> highlights = new ArrayList<>();

    @Schema(description = "Backward-compatible warning strings for older clients.")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Backward-compatible action strings for older clients.")
    private List<String> recommendedActions = new ArrayList<>();

    private Double confidence;
    private Integer qualityScore;
    private String priority;
    private String category;
    private String actionType;
    private String linkedMetric;
    private LocalDateTime expiresAt;
    private String ctaLabel;
    private String ctaTarget;
    private List<String> reviewReasons = new ArrayList<>();
    private Integer aiRemainingThisPeriod;

    @Schema(description = "Signals and range used to generate the insight so the user can see what was actually analyzed.")
    private DataCoverage dataCoverage = new DataCoverage();

    @Schema(description = "Structured findings with interpretation, evidence, and user-facing impact.")
    private List<KeyFinding> keyFindings = new ArrayList<>();

    @Schema(description = "Personalized next actions with reason and priority, not generic advice.")
    private List<PersonalizedAction> personalizedActions = new ArrayList<>();

    @Schema(description = "Single focus for the next day or next period.")
    private String tomorrowFocus;

    @Schema(description = "Specific risk or watch-out derived from the user's own data.")
    private String watchOut;

    @Schema(description = "Plain explanation of data limitations such as missing water, sleep, or incomplete logging.")
    private String dataQualityNote;
    private AiUxContractDto ux;
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

    @Data
    public static class DataCoverage {
        private Integer daysAnalyzed;
        private Integer mealsLogged;
        private Boolean exerciseLogged;
        private Integer exerciseMinutes;
        private Integer diaryDays;
        private List<String> signalsUsed = new ArrayList<>();
        private List<String> missingSignals = new ArrayList<>();
        private String confidenceLabel;
    }

    @Data
    public static class KeyFinding {
        private String type;
        private String label;
        private String message;
        private String evidence;
        private String impact;
        private String severity;
    }

    @Data
    public static class PersonalizedAction {
        private Integer priority;
        private String action;
        private String reason;
        private String expectedImpact;
        private String effort;
        private String linkedMetric;
    }
}
