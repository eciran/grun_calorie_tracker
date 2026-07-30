package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductUploadSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodProductUploadSessionRepository extends JpaRepository<FoodProductUploadSessionEntity, String> {
    Optional<FoodProductUploadSessionEntity> findByCreatedByIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );
}
