package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionPlanFeatureEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanFeatureRepository extends JpaRepository<SubscriptionPlanFeatureEntity, Long> {
    List<SubscriptionPlanFeatureEntity> findByPlanTypeOrderByFeatureAsc(SubscriptionPlan planType);
    Optional<SubscriptionPlanFeatureEntity> findByPlanTypeAndFeature(SubscriptionPlan planType, SubscriptionFeature feature);
}
