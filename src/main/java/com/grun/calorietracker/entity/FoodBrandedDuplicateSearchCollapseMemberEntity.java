package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(name = "food_branded_duplicate_search_collapse_members", uniqueConstraints =
        @UniqueConstraint(name = "uq_food_branded_duplicate_search_collapse_member",
                columnNames = {"collapse_id", "suppressed_food_item_id"}))
public class FoodBrandedDuplicateSearchCollapseMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collapse_id", nullable = false)
    private FoodBrandedDuplicateSearchCollapseEntity collapse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suppressed_food_item_id", nullable = false)
    private FoodItemEntity suppressedFoodItem;
}
