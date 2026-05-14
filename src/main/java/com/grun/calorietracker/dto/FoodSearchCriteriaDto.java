package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Advanced product search criteria used internally by the food product search service.")
public class FoodSearchCriteriaDto {
    @Schema(description = "Search text.", example = "milk")
    private String query;

    @Schema(description = "Optional brand filter.", example = "Arla")
    private String brand;

    @Schema(description = "Optional category filter.", example = "dairy")
    private String category;

    @Schema(description = "Minimum calories filter.", example = "50.0")
    private Double minCalories;

    @Schema(description = "Maximum calories filter.", example = "300.0")
    private Double maxCalories;

    @Schema(description = "Sort field.", example = "calories")
    private String sortBy;

    @Schema(description = "Sort direction.", example = "asc")
    private String sortOrder;

    @Schema(description = "Nutri-Score filter.", example = "a")
    private String nutriScore;
}
