package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeReportEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.RecipeReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeReportRepository extends JpaRepository<RecipeReportEntity, Long> {
    Optional<RecipeReportEntity> findByUserAndRecipeAndStatus(UserEntity user, RecipeEntity recipe, RecipeReportStatus status);
}
