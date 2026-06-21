package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.PreferredLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_item_search_aliases")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemSearchAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @Column(nullable = false)
    private String alias;

    @Column(nullable = false)
    private String normalizedAlias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreferredLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodSearchAliasType aliasType;

    private String source;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}