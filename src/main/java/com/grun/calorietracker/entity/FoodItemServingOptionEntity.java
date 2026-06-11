package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "food_item_serving_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemServingOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @Column(nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodServingOptionUnit unitType;

    @Column(nullable = false)
    private Double quantity;

    private Double gramWeight;

    private Double mlVolume;

    @Column(nullable = false)
    private Boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodServingOptionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodServingOptionQualityStatus qualityStatus;
}
