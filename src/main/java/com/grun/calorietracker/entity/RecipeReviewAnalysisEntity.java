package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.RecipeReviewAnalysisStatus;
import com.grun.calorietracker.enums.RecipeReviewRiskLevel;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "recipe_review_analyses", indexes = {
        @Index(name = "idx_recipe_review_analysis_recipe_created", columnList = "recipe_id,created_at"),
        @Index(name = "idx_recipe_review_analysis_status", columnList = "status")
})
@Data
public class RecipeReviewAnalysisEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeEntity recipe;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RecipeReviewAnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RecipeReviewRiskLevel riskLevel;

    private Integer qualityScore;
    private Integer deterministicScore;
    private Integer aiScore;
    private Double confidence;
    private Boolean reviewRequired;
    private Boolean criticalIssue;

    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String deterministicResultJson;
    @Column(columnDefinition = "TEXT")
    private String aiResultJson;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    private AiProvider provider;
    private String model;
    private String promptVersion;
    private Long latencyMs;
    private Integer totalTokens;
    private Double estimatedCost;
    @Column(length = 16)
    private String costCurrency;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
