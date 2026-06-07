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
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    private Double portionSize;

    @Enumerated(EnumType.STRING)
    private FoodPortionUnit portionUnit;

    private Double normalizedPortionGrams;
    private Integer itemOrder;
}
