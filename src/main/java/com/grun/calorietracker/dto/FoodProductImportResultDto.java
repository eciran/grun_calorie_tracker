package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food product CSV import result.")
public class FoodProductImportResultDto {

    @Schema(description = "Number of data rows read from the CSV file.", example = "10000")
    private int totalRows;

    @Schema(description = "Number of new products inserted.", example = "9350")
    private int insertedRows;

    @Schema(description = "Number of existing products updated by normalized barcode.", example = "600")
    private int updatedRows;

    @Schema(description = "Number of rows skipped because of validation or parsing errors.", example = "50")
    private int skippedRows;

    @Schema(description = "Saved product count.", example = "9950")
    private int savedRows;

    @Schema(description = "First import errors. The list is capped to keep responses small.")
    private List<FoodProductImportErrorDto> errors;
}
