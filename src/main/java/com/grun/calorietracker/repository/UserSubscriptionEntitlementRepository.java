package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserSubscriptionEntitlementEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserSubscriptionEntitlementRepository extends JpaRepository<UserSubscriptionEntitlementEntity, Long> {
    long countBySubscription(SubscriptionEntity subscription);
    List<UserSubscriptionEntitlementEntity> findBySubscription(SubscriptionEntity subscription);

    @Query("""
            select count(e) > 0
            from UserSubscriptionEntitlementEntity e
            where e.subscription.id = :subscriptionId
              and e.feature = :feature
              and e.enabled = true
              and e.validFrom <= :date
              and (e.validUntil is null or e.validUntil >= :date)
            """)
    boolean existsActiveFeature(@Param("subscriptionId") Long subscriptionId,
                                @Param("feature") SubscriptionFeature feature,
                                @Param("date") LocalDate date);

    long deleteByUser(UserEntity user);

    @Query("""
            select e
            from UserSubscriptionEntitlementEntity e
            where e.sourcePlan = :planType
              and e.feature = :feature
              and e.enabled = true
              and e.validFrom <= :date
              and (e.validUntil is null or e.validUntil >= :date)
            """)
    List<UserSubscriptionEntitlementEntity> findActiveEntitlementsForPlanFeature(@Param("planType") SubscriptionPlan planType,
                                                                                 @Param("feature") SubscriptionFeature feature,
                                                                                 @Param("date") LocalDate date);
}
