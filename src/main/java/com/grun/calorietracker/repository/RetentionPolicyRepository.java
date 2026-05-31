package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RetentionPolicyEntity;
import com.grun.calorietracker.enums.RetentionPolicyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicyEntity, Long> {
    Optional<RetentionPolicyEntity> findByPolicyKey(RetentionPolicyKey policyKey);
}
