package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AiCreditPricingPolicyEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCreditPricingPolicyRepository extends JpaRepository<AiCreditPricingPolicyEntity, Long> {
    Optional<AiCreditPricingPolicyEntity> findByFeature(SubscriptionFeature feature);
}
