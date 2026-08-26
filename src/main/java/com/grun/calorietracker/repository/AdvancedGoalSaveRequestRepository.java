package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AdvancedGoalSaveRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdvancedGoalSaveRequestRepository extends JpaRepository<AdvancedGoalSaveRequestEntity, Long> {
    Optional<AdvancedGoalSaveRequestEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
