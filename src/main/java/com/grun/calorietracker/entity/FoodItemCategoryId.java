package com.grun.calorietracker.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemCategoryId implements Serializable {
    private Long foodItem;
    private Long category;
}
