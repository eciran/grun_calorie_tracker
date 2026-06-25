package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RecipeDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface RecipeImageService {
    RecipeDto uploadRecipeImage(String email, Long recipeId, MultipartFile file);

    Resource loadRecipeImage(String filename);
}