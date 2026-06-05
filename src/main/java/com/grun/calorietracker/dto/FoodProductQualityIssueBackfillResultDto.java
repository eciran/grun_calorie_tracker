package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of rebuilding persistent food product quality issue records.")
public class FoodProductQualityIssueBackfillResultDto {

    @Schema(description = "Total product rows scanned.", example = "12500")
    private long scannedProducts;

    @Schema(description = "Number of batches processed.", example = "25")
    private int processedBatches;

    @Schema(description = "Batch size used by the job.", example = "500")
    private int pageSize;
}
