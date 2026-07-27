package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductQualitySuggestionRepository extends JpaRepository<ProductQualitySuggestionEntity, Long> {

    long countByStatus(ProductQualitySuggestionStatus status);

    @Query("""
            SELECT s.suggestionType, COUNT(s)
            FROM ProductQualitySuggestionEntity s
            WHERE s.status = :status
            GROUP BY s.suggestionType
            ORDER BY COUNT(s) DESC
            """)
    List<Object[]> summarizeTypesByStatus(@Param("status") ProductQualitySuggestionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductQualitySuggestionEntity s WHERE s.id = :id")
    Optional<ProductQualitySuggestionEntity> findForUpdateById(@Param("id") Long id);

    Page<ProductQualitySuggestionEntity> findByStatusOrderByCreatedAtDesc(ProductQualitySuggestionStatus status, Pageable pageable);

    List<ProductQualitySuggestionEntity> findByFoodItemIdOrderByCreatedAtDesc(Long foodItemId);

    @Query("""
            SELECT COUNT(s) > 0
            FROM ProductQualitySuggestionEntity s
            WHERE s.foodItem.id = :foodItemId
              AND s.suggestionType = :suggestionType
              AND COALESCE(s.fieldName, '') = COALESCE(:fieldName, '')
              AND COALESCE(s.suggestedValue, '') = COALESCE(:suggestedValue, '')
              AND s.status = :status
            """)
    boolean existsOpenDedupe(@Param("foodItemId") Long foodItemId,
                             @Param("suggestionType") ProductQualitySuggestionType suggestionType,
                             @Param("fieldName") String fieldName,
                             @Param("suggestedValue") String suggestedValue,
                             @Param("status") ProductQualitySuggestionStatus status);

    default boolean existsByFoodItemIdAndSuggestionTypeAndSuggestedValueAndStatus(
            Long foodItemId,
            ProductQualitySuggestionType suggestionType,
            String suggestedValue,
            ProductQualitySuggestionStatus status
    ) {
        return existsOpenDedupe(foodItemId, suggestionType, null, suggestedValue, status);
    }
}
