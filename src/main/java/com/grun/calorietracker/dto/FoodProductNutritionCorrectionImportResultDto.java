package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Result of importing admin food product nutrition corrections from CSV.")
public class FoodProductNutritionCorrectionImportResultDto {

    private int totalRows;
    private int updatedRows;
    private int skippedRows;
    private List<String> errors;
    private boolean dryRun;
    private int candidateRows;

    public FoodProductNutritionCorrectionImportResultDto(int totalRows, int updatedRows, int skippedRows, List<String> errors) {
        this(totalRows, updatedRows, skippedRows, errors, false, updatedRows);
    }

    public FoodProductNutritionCorrectionImportResultDto(
            int totalRows,
            int updatedRows,
            int skippedRows,
            List<String> errors,
            boolean dryRun,
            int candidateRows
    ) {
        this.totalRows = totalRows;
        this.updatedRows = updatedRows;
        this.skippedRows = skippedRows;
        this.errors = errors;
        this.dryRun = dryRun;
        this.candidateRows = candidateRows;
    }
}