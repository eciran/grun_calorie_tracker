package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodSearchTelemetryEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select count(t) from FoodSearchTelemetryEntity t where t.searchedAt >= :since
              and (:region is null or t.marketRegion = :region)
              and (:language is null or t.queryLanguage = :language)
            """)
    long countFiltered(@Param("since") Instant since, @Param("region") MarketRegion region,
                       @Param("language") PreferredLanguage language);

    @Query("""
            select count(t) from FoodSearchTelemetryEntity t
            where t.searchedAt >= :since and t.resultCount = 0
              and (:region is null or t.marketRegion = :region)
              and (:language is null or t.queryLanguage = :language)
            """)
    long countZeroResultFiltered(@Param("since") Instant since, @Param("region") MarketRegion region,
                                 @Param("language") PreferredLanguage language);

    @Query("""
            select count(t) from FoodSearchTelemetryEntity t
            where t.searchedAt >= :since and t.selectedAt is not null
              and (:region is null or t.marketRegion = :region)
              and (:language is null or t.queryLanguage = :language)
            """)
    long countSelectedFiltered(@Param("since") Instant since, @Param("region") MarketRegion region,
                               @Param("language") PreferredLanguage language);

    @Query("""
            select count(t) from FoodSearchTelemetryEntity t
            where t.searchedAt >= :since and t.searchedAt < :cutoff
              and t.resultCount > 0 and t.selectedAt is null
              and (:region is null or t.marketRegion = :region)
              and (:language is null or t.queryLanguage = :language)
            """)
    long countNoSelectionFiltered(@Param("since") Instant since, @Param("cutoff") Instant cutoff,
                                  @Param("region") MarketRegion region,
                                  @Param("language") PreferredLanguage language);

    @Query("""
            select t.safeQuery as query, count(t.id) as searches from FoodSearchTelemetryEntity t
            where t.searchedAt >= :since and t.resultCount = 0 and t.safeQuery <> ''
              and (:region is null or t.marketRegion = :region)
              and (:language is null or t.queryLanguage = :language)
            group by t.safeQuery order by count(t.id) desc
            """)
    java.util.List<ZeroResultQueryProjection> topZeroResultQueries(
            @Param("since") Instant since, @Param("region") MarketRegion region,
            @Param("language") PreferredLanguage language, Pageable pageable);

    interface ZeroResultQueryProjection {
        String getQuery();
        long getSearches();
    }
}