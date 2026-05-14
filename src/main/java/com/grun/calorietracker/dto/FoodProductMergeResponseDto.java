package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin response for a completed food product merge.")
public class FoodProductMergeResponseDto {

    @Schema(description = "Product that remains after merge.")
    private FoodProductDto targetProduct;

    @Schema(description = "Product ids that were merged into the target product.", example = "[2, 3]")
    private List<Long> mergedProductIds;

    @Schema(description = "Food log rows reassigned to the target product.", example = "18")
    private Integer reassignedFoodLogCount;

    @Schema(description = "Favorite rows reassigned to the target product.", example = "4")
    private Integer reassignedFavoriteCount;

    @Schema(description = "Favorite rows removed because the same user already favorited the target product.", example = "2")
    private Integer removedConflictingFavoriteCount;
}
