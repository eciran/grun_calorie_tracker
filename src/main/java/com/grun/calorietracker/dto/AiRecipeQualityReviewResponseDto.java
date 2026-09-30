package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecipeQualityReviewResponseDto implements AiUsageMetadataCarrier {
    private String schemaVersion;
    private String summary;
    private Integer qualityScore;
    private Double confidence;
    private Boolean reviewRequired;
    private String publicationRecommendation;
    private List<Issue> issues = new ArrayList<>();
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Double estimatedCost;
    private String costCurrency;

    @Data
    public static class Issue {
        private String type;
        private String severity;
        private Long foodItemId;
        private String message;
        private String suggestedAction;
    }
}
