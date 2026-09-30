package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminRecipeReviewAnalysisDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeReviewAnalysisEntity;
import com.grun.calorietracker.enums.RecipeReviewAnalysisStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeReviewAnalysisRepository;
import com.grun.calorietracker.service.RecipeReviewAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecipeReviewAnalysisServiceImpl implements RecipeReviewAnalysisService {
    private final RecipeRepository recipeRepository;
    private final RecipeReviewAnalysisRepository analysisRepository;
    private final RecipeReviewAnalysisEngine engine;
    private final RecipeReviewAnalysisProcessor processor;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AdminRecipeReviewAnalysisDto start(Long recipeId, boolean force, String requestedBy) {
        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        RecipeReviewAnalysisEngine.AnalysisInput input = engine.analyze(recipe);
        if (!force) {
            var cached = analysisRepository.findFirstByRecipeIdAndContentHashAndStatusOrderByCreatedAtDesc(
                    recipeId, input.contentHash(), RecipeReviewAnalysisStatus.COMPLETED);
            if (cached.isPresent()) {
                return toDto(cached.get());
            }
            var running = analysisRepository.findFirstByRecipeIdOrderByCreatedAtDesc(recipeId);
            if (running.isPresent() && running.get().getStatus() == RecipeReviewAnalysisStatus.PROCESSING
                    && input.contentHash().equals(running.get().getContentHash())) {
                return toDto(running.get());
            }
        }
        RecipeReviewAnalysisEntity entity = new RecipeReviewAnalysisEntity();
        entity.setRecipe(recipe);
        entity.setContentHash(input.contentHash());
        entity.setStatus(RecipeReviewAnalysisStatus.PROCESSING);
        entity.setRequestedBy(requestedBy);
        entity.setCreatedAt(LocalDateTime.now());
        entity = analysisRepository.save(entity);
        Long analysisId = entity.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processor.process(analysisId);
            }
        });
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRecipeReviewAnalysisDto latest(Long recipeId) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new ResourceNotFoundException("Recipe not found");
        }
        return analysisRepository.findFirstByRecipeIdOrderByCreatedAtDesc(recipeId)
                .map(this::toDto)
                .orElse(null);
    }

    private AdminRecipeReviewAnalysisDto toDto(RecipeReviewAnalysisEntity entity) {
        AdminRecipeReviewAnalysisDto dto = new AdminRecipeReviewAnalysisDto();
        dto.setId(entity.getId());
        dto.setRecipeId(entity.getRecipe().getId());
        dto.setStatus(entity.getStatus());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setQualityScore(entity.getQualityScore());
        dto.setDeterministicScore(entity.getDeterministicScore());
        dto.setAiScore(entity.getAiScore());
        dto.setConfidence(entity.getConfidence());
        dto.setReviewRequired(entity.getReviewRequired());
        dto.setCriticalIssue(entity.getCriticalIssue());
        dto.setSummary(entity.getSummary());
        dto.setDeterministicResult(readJson(entity.getDeterministicResultJson()));
        dto.setAiResult(readJson(entity.getAiResultJson()));
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setProvider(entity.getProvider());
        dto.setRequestedBy(entity.getRequestedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        return dto;
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readTree(value); }
        catch (Exception ignored) { return null; }
    }
}
