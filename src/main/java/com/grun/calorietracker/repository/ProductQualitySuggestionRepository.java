package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductQualitySuggestionRepository extends JpaRepository<ProductQualitySuggestionEntity, Long> {

    Page<ProductQualitySuggestionEntity> findByStatusOrderByCreatedAtDesc(ProductQualitySuggestionStatus status, Pageable pageable);

    boolean existsByFoodItemIdAndSuggestionTypeAndSuggestedValueAndStatus(
            Long foodItemId,
            ProductQualitySuggestionType suggestionType,
            String suggestedValue,
            ProductQualitySuggestionStatus status
    );
}
