package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FreePromotionPolicyEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface FreePromotionPolicyRepository extends JpaRepository<FreePromotionPolicyEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from FreePromotionPolicyEntity policy where policy.id = :id")
    Optional<FreePromotionPolicyEntity> findByIdForUpdate(@Param("id") Long id);
}
