package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "Water intake entry for the authenticated user.")
public class WaterLogRequestDto {

    @NotNull(message = "{validation.water-log.date.required}")
    @Schema(description = "Diary date for this water intake.", example = "2026-06-05", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate logDate;

    @NotNull(message = "{validation.water-log.amount.required}")
    @Positive(message = "{validation.water-log.amount.positive}")
    @Schema(description = "Water amount in milliliters.", example = "250", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer amountMl;

    @Size(max = 50, message = "{validation.water-log.source.size}")
    @Schema(description = "Optional source label, such as MANUAL, QUICK_ADD, or HEALTH_IMPORT.", example = "MANUAL")
    private String source;

    @Schema(description = "Optional exact time when the water was consumed. If omitted, server time is used.", example = "2026-06-05T10:15:00")
    private LocalDateTime loggedAt;
}
