package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.RecipeEntity;
import org.springframework.core.io.Resource;

public interface RecipeMediaCacheService {
    boolean cacheApprovedImage(RecipeEntity recipe);
    Resource load(String publicToken);
}
