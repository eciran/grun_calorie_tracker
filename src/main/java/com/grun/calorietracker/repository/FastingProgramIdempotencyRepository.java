package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FastingProgramIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FastingProgramIdempotencyRepository
        extends JpaRepository<FastingProgramIdempotencyEntity, Long> {

    Optional<FastingProgramIdempotencyEntity> findByUserIdAndOperationAndIdempotencyKey(
            Long userId,
            String operation,
            String idempotencyKey);
}