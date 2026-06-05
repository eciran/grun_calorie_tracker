package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of importing admin food product nutrition corrections from CSV.")
public class FoodProductNutritionCorrectionImportResultDto {

    private int totalRows;
    private int updatedRows;
    private int skippedRows;
    private List<String> errors;
}
