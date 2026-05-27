package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Health data deletion result.")
public class HealthDataDeleteResponseDto {

    @Schema(description = "Provider whose data was deleted. Null when all providers were deleted.", example = "APPLE_HEALTH")
    private HealthProvider provider;

    @Schema(description = "Number of deleted metric rows.", example = "120")
    private Long deletedMetricCount;
}
