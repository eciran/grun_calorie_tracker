package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "User daily hydration goal.")
public class WaterGoalDto {
    @Schema(description = "Daily water target in milliliters.", example = "2500")
    private Integer targetMl;

    @Schema(description = "Server update time.", example = "2026-06-05T12:00:00")
    private LocalDateTime updatedAt;
}
