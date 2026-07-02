package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Result of importing a provider workout as an exercise log.")
public class HealthWorkoutSyncResponseDto {
    @Schema(description = "Provider used for the workout import.", example = "APPLE_HEALTH")
    private HealthProvider provider;

    @Schema(description = "Raw provider activity type from the request.", example = "HKWorkoutActivityTypeRunning")
    private String providerActivityType;

    @Schema(description = "Mapped exercise item id.", example = "3")
    private Long exerciseItemId;

    @Schema(description = "Mapped exercise display name.", example = "Running")
    private String exerciseItemName;

    @Schema(description = "Created exercise log.")
    private ExerciseLogsDto exerciseLog;
}
