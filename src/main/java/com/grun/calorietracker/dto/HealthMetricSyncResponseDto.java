package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Health metric sync result.")
public class HealthMetricSyncResponseDto {

    @Schema(description = "Stored metric id.", example = "12")
    private Long id;

    @Schema(description = "Provider used for this sync.", example = "APPLE_HEALTH")
    private HealthProvider provider;

    @Schema(description = "Whether a new row was inserted instead of updating an existing provider metric.", example = "true")
    private boolean inserted;
}
