package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "A recent logged meal occurrence that can be copied again.")
public class FoodLogRecentMealDto extends FoodLogMealSummaryDto {

    @Schema(description = "Diary day that contains the recent meal.", example = "2026-05-21")
    private LocalDate sourceDate;
}
