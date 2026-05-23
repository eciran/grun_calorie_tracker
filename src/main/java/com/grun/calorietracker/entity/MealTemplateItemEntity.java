package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodPortionUnit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "meal_template_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealTemplateItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private MealTemplateEntity template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    private Double portionSize;

    @Enumerated(EnumType.STRING)
    private FoodPortionUnit portionUnit;

    private Double normalizedPortionGrams;
    private LocalTime logTime;
    private Integer itemOrder;
}
