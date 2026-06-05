package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "Persisted water intake entry.")
public class WaterLogDto {
    @Schema(description = "Water log id.", example = "1")
    private Long id;

    @Schema(description = "Diary date for this water intake.", example = "2026-06-05")
    private LocalDate logDate;

    @Schema(description = "Water amount in milliliters.", example = "250")
    private Integer amountMl;

    @Schema(description = "Source label for this entry.", example = "MANUAL")
    private String source;

    @Schema(description = "Exact time when the water was consumed.", example = "2026-06-05T10:15:00")
    private LocalDateTime loggedAt;

    @Schema(description = "Server creation time.", example = "2026-06-05T10:15:01")
    private LocalDateTime createdAt;
}
