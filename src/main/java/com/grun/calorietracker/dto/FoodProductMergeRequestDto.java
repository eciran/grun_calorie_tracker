package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin request for merging duplicate food products.")
public class FoodProductMergeRequestDto {

    @NotNull(message = "{validation.food-product-merge.target-product-id.required}")
    @Schema(description = "Product id that will remain after merge.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetProductId;

    @NotEmpty(message = "{validation.food-product-merge.duplicate-product-ids.required}")
    @Schema(description = "Duplicate product ids to merge into the target product.", example = "[2, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> duplicateProductIds;
}
