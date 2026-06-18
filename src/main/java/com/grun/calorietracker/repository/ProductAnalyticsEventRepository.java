package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductAnalyticsEventRepository extends JpaRepository<ProductAnalyticsEventEntity, Long> {

    long countByUser(UserEntity user);

    List<ProductAnalyticsEventEntity> findByUserOrderByCreatedAtDesc(UserEntity user);

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
}
