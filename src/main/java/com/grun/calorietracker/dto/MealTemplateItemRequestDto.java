package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Editable meal template food item.")
public class MealTemplateItemRequestDto {

    @NotNull(message = "{validation.food-log.food-item-id.required}")
    @Positive(message = "{validation.food-log.food-item-id.positive}")
    @Schema(example = "12")
    private Long foodItemId;

    @NotNull(message = "{validation.food-log.portion-size.required}")
    @Positive(message = "{validation.food-log.portion-size.positive}")
    @Schema(example = "1.5")
    private Double portionSize;

    @Schema(description = "Portion unit. Defaults to GRAM.", example = "SERVING")
    private FoodPortionUnit portionUnit;
}
