package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.SubscriptionPlan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface ProductAnalyticsEventRepository extends JpaRepository<ProductAnalyticsEventEntity, Long> {

    long countByUser(UserEntity user);

    List<ProductAnalyticsEventEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    List<ProductAnalyticsEventEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);

    long deleteByUser(UserEntity user);

    long countByEventTypeAndCreatedAtAfter(ProductAnalyticsEventType eventType, LocalDateTime createdAt);

    @Query("""
            select avg(event.durationMs)
            from ProductAnalyticsEventEntity event
            where event.eventType = :eventType
              and event.durationMs is not null
              and event.createdAt >= :createdAt
            """)
    Double averageDurationMs(@Param("eventType") ProductAnalyticsEventType eventType,
                             @Param("createdAt") LocalDateTime createdAt);

    @Query("""
            select count(distinct event.user.id)
            from ProductAnalyticsEventEntity event
            where event.user is not null
              and event.user.createdAt >= :registeredFrom
              and event.user.createdAt < :registeredTo
              and event.eventType = :eventType
              and event.createdAt < :eventBefore
            """)
    long countUsersForRegistrationCohort(@Param("eventType") ProductAnalyticsEventType eventType,
                                         @Param("registeredFrom") Instant registeredFrom,
                                         @Param("registeredTo") Instant registeredTo,
                                         @Param("eventBefore") LocalDateTime eventBefore);

    @Query("""
            select event.eventType as eventType, count(event.id) as eventCount,
                   count(distinct event.user.id) as userCount, avg(event.durationMs) as averageDurationMs
            from ProductAnalyticsEventEntity event
            where event.createdAt >= :since
              and (:region is null or event.marketRegion = :region)
              and (:language is null or upper(event.language) = :language)
              and (:plan is null or exists (
                  select subscription.id from SubscriptionEntity subscription
                  where subscription.user = event.user and subscription.planType = :plan
              ))
            group by event.eventType
            """)
    List<EventMetricProjection> summarizeEvents(
            @Param("since") LocalDateTime since,
            @Param("region") String region,
            @Param("language") String language,
            @Param("plan") SubscriptionPlan plan);

    @Query("""
            select event.targetType as feature, count(event.id) as eventCount,
                   count(distinct event.user.id) as userCount, avg(event.durationMs) as averageDurationMs
            from ProductAnalyticsEventEntity event
            where event.createdAt >= :since
              and event.eventType = com.grun.calorietracker.enums.ProductAnalyticsEventType.FEATURE_USED
              and event.targetType is not null
              and (:region is null or event.marketRegion = :region)
              and (:language is null or upper(event.language) = :language)
              and (:plan is null or exists (
                  select subscription.id from SubscriptionEntity subscription
                  where subscription.user = event.user and subscription.planType = :plan
              ))
            group by event.targetType
            """)
    List<FeatureMetricProjection> summarizeFeatures(
            @Param("since") LocalDateTime since,
            @Param("region") String region,
            @Param("language") String language,
            @Param("plan") SubscriptionPlan plan);

    @Query("""
            select event.marketRegion as segment, count(event.id) as eventCount,
                   count(distinct event.user.id) as userCount
            from ProductAnalyticsEventEntity event
            where event.createdAt >= :since and event.marketRegion is not null
            group by event.marketRegion
            """)
    List<SegmentMetricProjection> summarizeRegions(@Param("since") LocalDateTime since);

    @Query("""
            select upper(event.language) as segment, count(event.id) as eventCount,
                   count(distinct event.user.id) as userCount
            from ProductAnalyticsEventEntity event
            where event.createdAt >= :since and event.language is not null
            group by upper(event.language)
            """)
    List<SegmentMetricProjection> summarizeLanguages(@Param("since") LocalDateTime since);

    @Query("""
            select subscription.planType as segment, count(event.id) as eventCount,
                   count(distinct event.user.id) as userCount
            from ProductAnalyticsEventEntity event, SubscriptionEntity subscription
            where event.createdAt >= :since and subscription.user = event.user
            group by subscription.planType
            """)
    List<PlanSegmentMetricProjection> summarizePlans(@Param("since") LocalDateTime since);

    interface PlanSegmentMetricProjection {
        SubscriptionPlan getSegment();
        long getEventCount();
        long getUserCount();
    }

    interface EventMetricProjection {
        ProductAnalyticsEventType getEventType();
        long getEventCount();
        long getUserCount();
        Double getAverageDurationMs();
    }

    interface FeatureMetricProjection {
        String getFeature();
        long getEventCount();
        long getUserCount();
        Double getAverageDurationMs();
    }

    interface SegmentMetricProjection {
        String getSegment();
        long getEventCount();
        long getUserCount();
    }
}
