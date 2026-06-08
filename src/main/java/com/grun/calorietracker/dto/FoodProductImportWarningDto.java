package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single non-blocking food product import row warning.")
public class FoodProductImportWarningDto {

    @Schema(description = "CSV row number including the header row.", example = "42")
    private int rowNumber;

    @Schema(description = "Barcode, source key, or row identifier.", example = "3017620422003")
    private String identifier;

    @Schema(description = "Machine-readable warning code.", example = "MISSING_MACROS")
    private String code;

    @Schema(description = "Human-readable warning reason.", example = "Product has no image URL.")
    private String reason;
}
