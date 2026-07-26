package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
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
}
