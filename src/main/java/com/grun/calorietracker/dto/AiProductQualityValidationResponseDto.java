package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProductQualityValidationResponseDto {
    private String schemaVersion;
    private String summary;
    private Double confidence;
    private Integer qualityScore;
    private Boolean reviewRequired;
    private List<AiProductQualityIssueDto> issues;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiProductQualityIssueDto {
        private ProductQualitySuggestionType suggestionType;
        private String fieldName;
        private String currentValue;
        private String suggestedValue;
        private String reason;
        private Integer confidenceScore;
    }
}
