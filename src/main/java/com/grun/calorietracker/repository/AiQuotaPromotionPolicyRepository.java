package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiQuotaPromotionPolicyEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiQuotaPromotionPolicyRepository extends JpaRepository<AiQuotaPromotionPolicyEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from AiQuotaPromotionPolicyEntity policy where policy.id = :id")
    Optional<AiQuotaPromotionPolicyEntity> findByIdForUpdate(@Param("id") Long id);
}
