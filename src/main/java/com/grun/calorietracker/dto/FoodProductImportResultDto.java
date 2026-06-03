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

    @Schema(description = "Saved row count grouped by catalog type.", example = "{\"BRANDED_PRODUCT\": 120, \"LOCAL_DISH\": 20}")
    private Map<String, Integer> catalogTypeCounts;

    @Schema(description = "Saved row count grouped by data source.", example = "{\"OPEN_FOOD_FACTS\": 120, \"USDA_FOODDATA\": 80}")
    private Map<String, Integer> dataSourceCounts;

    @Schema(description = "Saved row warning count grouped by quality issue.", example = "{\"MISSING_IMAGE\": 120, \"MISSING_MACROS\": 8}")
    private Map<String, Integer> qualityWarningCounts;

    @Schema(description = "Overall import quality score from 0 to 100. Errors and warnings reduce this score.", example = "87")
    private int importQualityScore;

    @Schema(description = "Detected import format.", example = "TSV")
    private String importFormat;

    @Schema(description = "Detected source column format.", example = "OPEN_FOOD_FACTS_EXPORT")
    private String sourceFormat;

    @Schema(description = "First import errors. The list is capped to keep responses small.")
    private List<FoodProductImportErrorDto> errors;

    @Schema(description = "First non-blocking import warnings. The list is capped to keep responses small.")
    private List<FoodProductImportWarningDto> warnings;

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
        this(totalRows, insertedRows, updatedRows, skippedRows, savedRows, duplicateInputRows, reviewRequiredRows, 0, 0, Map.of(), Map.of(), Map.of(), Map.of(), 100, importFormat, "GRUN_STANDARD", errors, List.of());
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
                                      Map<String, Integer> catalogTypeCounts,
                                      Map<String, Integer> dataSourceCounts,
                                      String importFormat,
                                      List<FoodProductImportErrorDto> errors) {
        this(totalRows, insertedRows, updatedRows, skippedRows, savedRows, duplicateInputRows, reviewRequiredRows, missingMarketRegionRows, unsupportedMarketRegionRows, marketRegionCounts, catalogTypeCounts, dataSourceCounts, Map.of(), 100, importFormat, "GRUN_STANDARD", errors, List.of());
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
                                      Map<String, Integer> catalogTypeCounts,
                                      Map<String, Integer> dataSourceCounts,
                                      Map<String, Integer> qualityWarningCounts,
                                      String importFormat,
                                      String sourceFormat,
                                      List<FoodProductImportErrorDto> errors) {
        this(totalRows, insertedRows, updatedRows, skippedRows, savedRows, duplicateInputRows, reviewRequiredRows, missingMarketRegionRows, unsupportedMarketRegionRows, marketRegionCounts, catalogTypeCounts, dataSourceCounts, qualityWarningCounts, 100, importFormat, sourceFormat, errors, List.of());
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
                                      Map<String, Integer> catalogTypeCounts,
                                      Map<String, Integer> dataSourceCounts,
                                      Map<String, Integer> qualityWarningCounts,
                                      int importQualityScore,
                                      String importFormat,
                                      String sourceFormat,
                                      List<FoodProductImportErrorDto> errors,
                                      List<FoodProductImportWarningDto> warnings) {
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
        this.catalogTypeCounts = catalogTypeCounts;
        this.dataSourceCounts = dataSourceCounts;
        this.qualityWarningCounts = qualityWarningCounts;
        this.importQualityScore = importQualityScore;
        this.importFormat = importFormat;
        this.sourceFormat = sourceFormat;
        this.errors = errors;
        this.warnings = warnings;
    }
}
