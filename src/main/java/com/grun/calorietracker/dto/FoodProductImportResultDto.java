package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
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

    @Schema(description = "Number of duplicate barcode rows inside the imported file. The last row for a barcode wins within the same import.", example = "12")
    private int duplicateInputRows;

    @Schema(description = "Number of saved rows that still require admin product or image review.", example = "8200")
    private int reviewRequiredRows;

    @Schema(description = "Rows without an explicit market region. New products fall back to GLOBAL.", example = "14")
    private int missingMarketRegionRows;

    @Schema(description = "Rows with an unsupported market region value. Existing product region is preserved when available, otherwise GLOBAL is used.", example = "3")
    private int unsupportedMarketRegionRows;

    @Schema(description = "Saved row count grouped by resolved market region.", example = "{\"UK_IE\": 120, \"TR\": 80, \"GLOBAL\": 2}")
    private Map<String, Integer> marketRegionCounts;

    @Schema(description = "Detected import format.", example = "TSV")
    private String importFormat;

    @Schema(description = "First import errors. The list is capped to keep responses small.")
    private List<FoodProductImportErrorDto> errors;

    public FoodProductImportResultDto(int totalRows,
                                      int insertedRows,
                                      int updatedRows,
                                      int skippedRows,
                                      int savedRows,
                                      List<FoodProductImportErrorDto> errors) {
        this(totalRows, insertedRows, updatedRows, skippedRows, savedRows, 0, 0, "CSV", errors);
    }

    public FoodProductImportResultDto(int totalRows,
                                      int insertedRows,
                                      int updatedRows,
                                      int skippedRows,
                                      int savedRows,
                                      int duplicateInputRows,
                                      int reviewRequiredRows,
                                      String importFormat,
                                      List<FoodProductImportErrorDto> errors) {
        this(totalRows, insertedRows, updatedRows, skippedRows, savedRows, duplicateInputRows, reviewRequiredRows, 0, 0, Map.of(), importFormat, errors);
    }

    public FoodProductImportResultDto(int totalRows,
                                      int insertedRows,
                                      int updatedRows,
                                      int skippedRows,
                                      int savedRows,
                                      int duplicateInputRows,
                                      int reviewRequiredRows,
                                      int missingMarketRegionRows,
                                      int unsupportedMarketRegionRows,
                                      Map<String, Integer> marketRegionCounts,
                                      String importFormat,
                                      List<FoodProductImportErrorDto> errors) {
        this.totalRows = totalRows;
        this.insertedRows = insertedRows;
        this.updatedRows = updatedRows;
        this.skippedRows = skippedRows;
        this.savedRows = savedRows;
        this.duplicateInputRows = duplicateInputRows;
        this.reviewRequiredRows = reviewRequiredRows;
        this.missingMarketRegionRows = missingMarketRegionRows;
        this.unsupportedMarketRegionRows = unsupportedMarketRegionRows;
        this.marketRegionCounts = marketRegionCounts;
        this.importFormat = importFormat;
        this.errors = errors;
    }
}
