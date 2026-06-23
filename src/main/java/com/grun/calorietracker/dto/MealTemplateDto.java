package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "User-owned saved meal template.")
public class MealTemplateDto {

    private Long id;
    private String name;
    private String mealType;
    private LocalDateTime createdAt;
    private List<MealTemplateItemDto> items;

    @Schema(description = "Total calories for all template items using their stored portions.", example = "520.0")
    private Double totalCalories;

    @Schema(description = "Total protein in grams for all template items using their stored portions.", example = "32.5")
    private Double totalProtein;

    @Schema(description = "Total carbohydrates in grams for all template items using their stored portions.", example = "58.0")
    private Double totalCarbs;

    @Schema(description = "Total fat in grams for all template items using their stored portions.", example = "18.2")
    private Double totalFat;
}