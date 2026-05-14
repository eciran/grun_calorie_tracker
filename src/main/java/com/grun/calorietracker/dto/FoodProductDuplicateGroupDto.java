package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Duplicate product group sharing the same normalized barcode.")
public class FoodProductDuplicateGroupDto {

    @Schema(description = "Normalized barcode shared by all products in this duplicate group.", example = "3017620422003")
    private String normalizedBarcode;

    @Schema(description = "Number of products in this duplicate group.", example = "2")
    private Integer productCount;

    @Schema(description = "Products that share the same normalized barcode.")
    private List<FoodProductDto> products;
}
