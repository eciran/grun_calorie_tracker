package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Batch health metric sync result.")
public class HealthMetricBatchSyncResponseDto {

    @Schema(description = "Provider used for this batch sync.", example = "APPLE_HEALTH")
    private HealthProvider provider;

    @Schema(description = "Number of metrics accepted in the batch.", example = "25")
    private Integer acceptedCount;

    @Schema(description = "Number of new rows inserted.", example = "20")
    private Integer insertedCount;

    @Schema(description = "Number of existing rows updated.", example = "5")
    private Integer updatedCount;

    @Schema(description = "Latest recordedAt accepted in this batch.")
    private LocalDateTime latestRecordedAt;

    @Schema(description = "Per-metric sync results.")
    private List<HealthMetricSyncResponseDto> results;
}
