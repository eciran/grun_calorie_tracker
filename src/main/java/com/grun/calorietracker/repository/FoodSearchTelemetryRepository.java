package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodSearchTelemetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface FoodSearchTelemetryRepository extends JpaRepository<FoodSearchTelemetryEntity, String> {
    long deleteByExpiresAtBefore(Instant cutoff);
    long countBySearchedAtAfter(Instant since);
    long countBySearchedAtAfterAndResultCount(Instant since, Integer resultCount);
    long countBySearchedAtAfterAndSelectedAtIsNotNull(Instant since);

    @Query("""
            select count(t)
            from FoodSearchTelemetryEntity t
            where t.searchedAt >= :since
              and t.searchedAt < :cutoff
              and t.resultCount > 0
              and t.selectedAt is null
            """)
    long countNoSelection(@Param("since") Instant since, @Param("cutoff") Instant cutoff);
}