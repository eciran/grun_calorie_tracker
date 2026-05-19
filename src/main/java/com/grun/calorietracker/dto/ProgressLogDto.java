package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "User progress entry with weight and optional nutrition snapshot.")
public class ProgressLogDto {

    @Schema(description = "Progress log id.", example = "1")
    private Long id;

    @Schema(description = "Server-side progress log timestamp.", example = "2026-05-11T12:00:00")
    private LocalDateTime logDate;

    @NotNull(message = "{validation.progress-log.weight.required}")
    @Min(value = 20, message = "{validation.progress-log.weight.min}")
    @Schema(description = "Current body weight in kilograms.", example = "82.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double weight;

    @Schema(description = "Optional calorie intake snapshot.", example = "2100")
    private Integer calorieIntake;

    @Schema(description = "Optional protein intake snapshot in grams.", example = "145.0")
    private Double proteinIntake;

    @Schema(description = "Optional fat intake snapshot in grams.", example = "65.0")
    private Double fatIntake;

    @Schema(description = "Optional carbohydrate intake snapshot in grams.", example = "230.0")
    private Double carbIntake;

    @Schema(description = "Optional user note.", example = "Felt strong during today's workout.")
    private String note;
}
