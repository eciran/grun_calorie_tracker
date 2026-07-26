package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.SubscriptionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {
    Optional<SubscriptionEntity> findByUser(UserEntity user);
    Optional<SubscriptionEntity> findByUserId(Long userId);
    long deleteByUser(UserEntity user);
    long countByPlanTypeAndStatus(SubscriptionPlan planType, SubscriptionStatus status);
    long countByStatus(SubscriptionStatus status);

    @Query("""
            select subscription.planType as planType, count(subscription.id) as subscriptionCount
            from SubscriptionEntity subscription
            where subscription.status in (
                com.grun.calorietracker.enums.SubscriptionStatus.ACTIVE,
                com.grun.calorietracker.enums.SubscriptionStatus.TRIALING
            )
            group by subscription.planType
            """)
    List<SubscriptionPlanCountProjection> countCurrentUsersByPlan();

    @Query("""
            select count(distinct subscription.user.id)
            from SubscriptionEntity subscription
            where subscription.user.createdAt >= :registeredFrom
              and subscription.user.createdAt < :registeredTo
              and subscription.planType <> com.grun.calorietracker.enums.SubscriptionPlan.FREE
              and subscription.startDate is not null
              and subscription.startDate < :startedBefore
              and subscription.status in (
                  com.grun.calorietracker.enums.SubscriptionStatus.ACTIVE,
                  com.grun.calorietracker.enums.SubscriptionStatus.TRIALING
              )
            """)
    long countPaidUsersForRegistrationCohort(
            @org.springframework.data.repository.query.Param("registeredFrom") Instant registeredFrom,
            @org.springframework.data.repository.query.Param("registeredTo") Instant registeredTo,
            @org.springframework.data.repository.query.Param("startedBefore") LocalDate startedBefore
    );

    @Query("""
            select subscription.planType as planType, count(subscription.id) as subscriptionCount
            from SubscriptionEntity subscription
            where subscription.user.createdAt >= :registeredFrom
              and subscription.user.createdAt < :registeredTo
              and subscription.status in (
                  com.grun.calorietracker.enums.SubscriptionStatus.ACTIVE,
                  com.grun.calorietracker.enums.SubscriptionStatus.TRIALING
              )
            group by subscription.planType
            """)
    List<SubscriptionPlanCountProjection> countCurrentUsersByPlanForRegistrationCohort(
            @org.springframework.data.repository.query.Param("registeredFrom") Instant registeredFrom,
            @org.springframework.data.repository.query.Param("registeredTo") Instant registeredTo
    );

    @Query("""
            select count(s)
            from SubscriptionEntity s
            where s.status in (com.grun.calorietracker.enums.SubscriptionStatus.ACTIVE, com.grun.calorietracker.enums.SubscriptionStatus.TRIALING)
              and coalesce(s.aiUsedThisPeriod, 0) >= (coalesce(s.aiMonthlyQuota, 0) + coalesce(s.aiAddonQuota, 0))
            """)
    long countActiveSubscriptionsWithExhaustedAiQuota();
}
