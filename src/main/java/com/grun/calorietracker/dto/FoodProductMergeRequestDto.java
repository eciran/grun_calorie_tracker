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

    @NotNull
    @Schema(description = "Product id that will remain after merge.", example = "1")
    private Long targetProductId;

    @NotEmpty
    @Schema(description = "Duplicate product ids to merge into the target product.", example = "[2, 3]")
    private List<Long> duplicateProductIds;
}
