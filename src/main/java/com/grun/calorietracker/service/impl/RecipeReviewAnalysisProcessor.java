package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiRecipeQualityReviewResponseDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeReviewAnalysisEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.RecipeReviewAnalysisStatus;
import com.grun.calorietracker.enums.RecipeReviewRiskLevel;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeReviewAnalysisRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class RecipeReviewAnalysisProcessor {
    private final RecipeReviewAnalysisRepository analysisRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeReviewAnalysisEngine engine;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Async("recipeReviewExecutor")
    @Transactional
    public void process(Long analysisId, PreferredLanguage responseLanguage) {
        RecipeReviewAnalysisEntity analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return;
        }
        long startedAt = System.nanoTime();
        try {
            RecipeEntity recipe = recipeRepository.findById(analysis.getRecipe().getId())
                    .orElseThrow(() -> new IllegalStateException("Recipe no longer exists."));
            RecipeReviewAnalysisEngine.AnalysisInput input = engine.analyze(recipe, responseLanguage);
            analysis.setContentHash(input.contentHash());
            analysis.setDeterministicScore(input.deterministicScore());
            analysis.setCriticalIssue(input.criticalIssue());
            analysis.setDeterministicResultJson(objectMapper.writeValueAsString(input.deterministicResult()));

            AiProvider provider = aiProperties.resolveProvider(AiRequestType.AI_RECIPE_QUALITY_REVIEW);
            AiRecipeQualityReviewResponseDto ai = provider(provider).reviewRecipeQuality(input.request());
            int aiScore = clamp(ai.getQualityScore() == null ? 0 : ai.getQualityScore());
            int combined = input.criticalIssue() ? Math.min(49, input.deterministicScore())
                    : clamp((int) Math.round(input.deterministicScore() * 0.60 + aiScore * 0.40));
            boolean critical = input.criticalIssue() || hasCriticalIssue(ai);
            if (critical) {
                combined = Math.min(combined, 49);
            }
            analysis.setProvider(provider);
            analysis.setModel(aiProperties.resolveModel(AiRequestType.AI_RECIPE_QUALITY_REVIEW));
            analysis.setPromptVersion(aiProperties.getPromptVersion());
            analysis.setAiScore(aiScore);
            analysis.setQualityScore(combined);
            analysis.setConfidence(normalizeConfidence(ai.getConfidence()));
            analysis.setCriticalIssue(critical);
            analysis.setReviewRequired(critical || Boolean.TRUE.equals(ai.getReviewRequired()) || combined < 85);
            analysis.setRiskLevel(risk(combined, critical));
            analysis.setSummary(ai.getSummary());
            analysis.setAiResultJson(objectMapper.writeValueAsString(ai));
            analysis.setTotalTokens(ai.getTotalTokens());
            analysis.setEstimatedCost(ai.getEstimatedCost());
            analysis.setCostCurrency(normalizeCurrency(ai.getCostCurrency()));
            analysis.setStatus(RecipeReviewAnalysisStatus.COMPLETED);
            analysis.setCompletedAt(LocalDateTime.now());
            analysis.setLatencyMs((System.nanoTime() - startedAt) / 1_000_000);
            analysisRepository.save(analysis);
        } catch (Exception exception) {
            log.warn("recipe_review_analysis_failed analysisId={} recipeId={} error={}", analysisId,
                    analysis.getRecipe() == null ? null : analysis.getRecipe().getId(), exception.getMessage());
            analysis.setStatus(RecipeReviewAnalysisStatus.FAILED);
            analysis.setReviewRequired(true);
            analysis.setErrorMessage(sanitize(exception.getMessage()));
            analysis.setCompletedAt(LocalDateTime.now());
            analysis.setLatencyMs((System.nanoTime() - startedAt) / 1_000_000);
            analysisRepository.save(analysis);
        }
    }

    private AiMealDraftProviderClient provider(AiProvider provider) {
        Map<AiProvider, AiMealDraftProviderClient> clients = new EnumMap<>(AiProvider.class);
        providerClients.forEach(client -> clients.put(client.provider(), client));
        AiMealDraftProviderClient client = clients.get(provider);
        if (client == null || provider == AiProvider.DISABLED) {
            throw new IllegalStateException("AI recipe review provider is not configured.");
        }
        return client;
    }

    private boolean hasCriticalIssue(AiRecipeQualityReviewResponseDto response) {
        return response.getIssues() != null && response.getIssues().stream()
                .anyMatch(issue -> "CRITICAL".equalsIgnoreCase(issue.getSeverity()));
    }

    private RecipeReviewRiskLevel risk(int score, boolean critical) {
        if (critical || score < 50) return RecipeReviewRiskLevel.BLOCKED;
        if (score < 70) return RecipeReviewRiskLevel.LOW_CONFIDENCE;
        if (score < 85) return RecipeReviewRiskLevel.REVIEW_RECOMMENDED;
        return RecipeReviewRiskLevel.TRUSTED;
    }

    private int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private double normalizeConfidence(Double value) { return value == null ? 0.0 : Math.max(0.0, Math.min(1.0, value)); }
    private String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED" : value.trim().toUpperCase(Locale.ROOT);
    }
    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "Recipe analysis failed.";
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
