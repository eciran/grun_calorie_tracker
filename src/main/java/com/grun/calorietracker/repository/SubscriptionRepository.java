package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {
    Optional<SubscriptionEntity> findByUser(UserEntity user);
    Optional<SubscriptionEntity> findByUserId(Long userId);
    long deleteByUser(UserEntity user);
    long countByPlanTypeAndStatus(SubscriptionPlan planType, SubscriptionStatus status);
    long countByStatus(SubscriptionStatus status);

    @Query("""
            select count(s)
            from SubscriptionEntity s
            where s.status in (com.grun.calorietracker.enums.SubscriptionStatus.ACTIVE, com.grun.calorietracker.enums.SubscriptionStatus.TRIALING)
              and coalesce(s.aiUsedThisPeriod, 0) >= (coalesce(s.aiMonthlyQuota, 0) + coalesce(s.aiAddonQuota, 0))
            """)
    long countActiveSubscriptionsWithExhaustedAiQuota();
}
