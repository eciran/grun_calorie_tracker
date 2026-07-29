package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.GroceryCategory;
import com.grun.calorietracker.enums.GroceryListItemSource;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "grocery_list_items")
@Getter
@Setter
@NoArgsConstructor
public class GroceryListItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grocery_list_id", nullable = false)
    private GroceryListEntity groceryList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItemEntity foodItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroceryListItemSource source;

    @Column(name = "generated_source_key", length = 120)
    private String generatedSourceKey;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GroceryCategory category = GroceryCategory.OTHER;

    @Column(nullable = false)
    private Boolean purchased = false;

    @Column(nullable = false)
    private Boolean excluded = false;

    @Column(name = "quantity_overridden", nullable = false)
    private Boolean quantityOverridden = false;

    @Column(name = "display_quantity", nullable = false)
    private Double displayQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_unit", nullable = false, length = 30)
    private FoodPortionUnit displayUnit;

    @Column(name = "normalized_grams")
    private Double normalizedGrams;

    @Column(name = "planned_uses", nullable = false)
    private Integer plannedUses = 0;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (category == null) category = GroceryCategory.OTHER;
        if (purchased == null) purchased = false;
        if (excluded == null) excluded = false;
        if (quantityOverridden == null) quantityOverridden = false;
        if (plannedUses == null) plannedUses = 0;
        if (version == null) version = 0L;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
