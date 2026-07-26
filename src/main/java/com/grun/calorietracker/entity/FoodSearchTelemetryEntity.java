package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "food_search_telemetry")
@Data
public class FoodSearchTelemetryEntity {
    @Id
    private String id;
    private String safeQuery;
    private String queryFingerprint;
    @Enumerated(EnumType.STRING)
    private PreferredLanguage queryLanguage;
    @Enumerated(EnumType.STRING)
    private MarketRegion marketRegion;
    private Integer resultCount;
    private String resultFoodItemIds;
    private Integer selectedRank;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_food_item_id")
    private FoodItemEntity selectedFoodItem;
    private Instant searchedAt;
    private Instant selectedAt;
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (searchedAt == null) searchedAt = Instant.now();
        if (expiresAt == null) expiresAt = searchedAt.plus(90, ChronoUnit.DAYS);
    }
}