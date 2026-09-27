package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeTranslationEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeTranslationRepository extends JpaRepository<RecipeTranslationEntity, Long> {
    Optional<RecipeTranslationEntity> findByRecipeIdAndLanguage(Long recipeId, PreferredLanguage language);
}
