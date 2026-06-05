package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request to start a fasting session.")
public class FastingSessionStartRequestDto {
    @Schema(description = "Optional explicit start time. If omitted, server time is used.", example = "2026-06-05T20:00:00")
    private LocalDateTime startedAt;

    @Min(value = 30, message = "{validation.fasting-session.target-minutes.min}")
    @Max(value = 2880, message = "{validation.fasting-session.target-minutes.max}")
    @Schema(description = "Optional custom target duration in minutes. If omitted, active plan fasting hours are used.", example = "960")
    private Integer targetMinutes;

    @Size(max = 500, message = "{validation.fasting.note.size}")
    @Schema(description = "Optional session note.", example = "Starting after dinner.")
    private String note;
}
