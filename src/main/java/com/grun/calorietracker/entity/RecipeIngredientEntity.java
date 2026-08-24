package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_ingredients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeIngredientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private RecipeEntity recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItemEntity foodItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serving_option_id")
    private FoodItemServingOptionEntity servingOption;

    private Double portionSize;

    @Enumerated(EnumType.STRING)
    private FoodPortionUnit portionUnit;

    @Column(name = "normalized_portion_grams")
    private Double normalizedPortionGrams;

    @Column(name = "normalized_portion_milliliters")
    private Double normalizedPortionMilliliters;
    @Column(name = "snapshot_food_name")
    private String snapshotFoodName;
    @Column(name = "snapshot_calories")
    private Double snapshotCalories;
    @Column(name = "snapshot_protein")
    private Double snapshotProtein;
    @Column(name = "snapshot_carbs")
    private Double snapshotCarbs;
    @Column(name = "snapshot_fat")
    private Double snapshotFat;
    @Column(name = "snapshot_fiber")
    private Double snapshotFiber;
    @Column(name = "snapshot_sugar")
    private Double snapshotSugar;
    @Column(name = "snapshot_saturated_fat")
    private Double snapshotSaturatedFat;
    @Column(name = "snapshot_sodium")
    private Double snapshotSodium;
    @Column(name = "snapshot_potassium")
    private Double snapshotPotassium;
    @Column(name = "snapshot_cholesterol")
    private Double snapshotCholesterol;
    @Column(name = "snapshot_calcium")
    private Double snapshotCalcium;
    @Column(name = "snapshot_iron")
    private Double snapshotIron;
    @Column(name = "snapshot_magnesium")
    private Double snapshotMagnesium;
    @Column(name = "snapshot_zinc")
    private Double snapshotZinc;
    @Column(name = "snapshot_vitamin_a")
    private Double snapshotVitaminA;
    @Column(name = "snapshot_vitamin_c")
    private Double snapshotVitaminC;
    @Column(name = "snapshot_vitamin_d")
    private Double snapshotVitaminD;
    @Column(name = "snapshot_vitamin_e")
    private Double snapshotVitaminE;
    @Column(name = "snapshot_vitamin_b12")
    private Double snapshotVitaminB12;
    private Integer itemOrder;
}
