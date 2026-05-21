package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Single food product import row error.")
public class FoodProductImportErrorDto {

    @Schema(description = "CSV row number including the header row.", example = "42")
    private int rowNumber;

    @Schema(description = "Barcode value from the invalid CSV row.", example = "3017620422003")
    private String barcode;

    @Schema(description = "Reason why the row could not be imported.", example = "Product name is required.")
    private String reason;
}
