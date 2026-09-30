package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeReviewAnalysisEntity;
import com.grun.calorietracker.enums.RecipeReviewAnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecipeReviewAnalysisRepository extends JpaRepository<RecipeReviewAnalysisEntity, Long> {
    Optional<RecipeReviewAnalysisEntity> findFirstByRecipeIdOrderByCreatedAtDesc(Long recipeId);
    Optional<RecipeReviewAnalysisEntity> findFirstByRecipeIdAndContentHashAndStatusOrderByCreatedAtDesc(
            Long recipeId, String contentHash, RecipeReviewAnalysisStatus status);

    @Query("""
            select analysis.status, analysis.costCurrency, count(analysis),
                   coalesce(sum(analysis.totalTokens), 0), coalesce(sum(analysis.estimatedCost), 0)
            from RecipeReviewAnalysisEntity analysis
            where analysis.createdAt >= :createdAfter
            group by analysis.status, analysis.costCurrency
            """)
    List<Object[]> summarizeUsageAfter(@Param("createdAfter") LocalDateTime createdAfter);
}
