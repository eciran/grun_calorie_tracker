package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeUserInteractionEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeUserInteractionRepository extends JpaRepository<RecipeUserInteractionEntity, Long> {
    Optional<RecipeUserInteractionEntity> findByUserAndRecipe(UserEntity user, RecipeEntity recipe);

    List<RecipeUserInteractionEntity> findByUserAndSavedTrueOrderByUpdatedAtDesc(UserEntity user);

    List<RecipeUserInteractionEntity> findByUserAndFavoriteTrueOrderByUpdatedAtDesc(UserEntity user);

    long countByRecipeAndSavedTrue(RecipeEntity recipe);

    long countByRecipeAndFavoriteTrue(RecipeEntity recipe);

    long countByRecipeAndRatingIsNotNull(RecipeEntity recipe);

    @Query("SELECT AVG(interaction.rating) FROM RecipeUserInteractionEntity interaction WHERE interaction.recipe = :recipe AND interaction.rating IS NOT NULL")
    Double averageRating(@Param("recipe") RecipeEntity recipe);
}
