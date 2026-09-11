package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.service.RecipeMediaCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeMediaCacheBackfillJob {
    private final RecipeRepository recipeRepository;
    private final RecipeMediaCacheService recipeMediaCacheService;

    @Scheduled(initialDelayString = "${grun.recipe-media.cache-backfill-initial-delay-ms:30000}",
            fixedDelayString = "${grun.recipe-media.cache-backfill-delay-ms:600000}")
    @Transactional
    public void cachePublishedRecipeImages() {
        int cached = 0;
        for (RecipeEntity recipe : recipeRepository.findByVisibilityAndArchivedFalseOrderByUpdatedAtDesc(RecipeVisibility.PUBLIC_ADMIN)) {
            try {
                if (recipeMediaCacheService.cacheApprovedImage(recipe)) {
                    recipeRepository.save(recipe);
                    cached++;
                }
            } catch (RuntimeException exception) {
                log.warn("recipe_image_cache_backfill_failed recipeId={} reason={}", recipe.getId(), exception.getMessage());
            }
        }
        if (cached > 0) log.info("recipe_image_cache_backfill_completed cached={}", cached);
    }
}
