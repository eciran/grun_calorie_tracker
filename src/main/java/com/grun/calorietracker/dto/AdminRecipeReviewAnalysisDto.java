package com.grun.calorietracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.RecipeReviewAnalysisStatus;
import com.grun.calorietracker.enums.RecipeReviewRiskLevel;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminRecipeReviewAnalysisDto {
    private Long id;
    private Long recipeId;
    private RecipeReviewAnalysisStatus status;
    private RecipeReviewRiskLevel riskLevel;
    private Integer qualityScore;
    private Integer deterministicScore;
    private Integer aiScore;
    private Double confidence;
    private Boolean reviewRequired;
    private Boolean criticalIssue;
    private String summary;
    private JsonNode deterministicResult;
    private JsonNode aiResult;
    private String errorMessage;
    private AiProvider provider;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
