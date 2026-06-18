package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductCorrectionSuggestionEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCorrectionSuggestionRepository extends JpaRepository<ProductCorrectionSuggestionEntity, Long> {
    long countByUser(UserEntity user);

    List<ProductCorrectionSuggestionEntity> findByUserOrderByCreatedAtDesc(UserEntity user);

    long deleteByUser(UserEntity user);
}
