package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FoodProductBarcodeUpdateRequestDto(
        @NotBlank
        @Pattern(regexp = "(?:\\d{8}|\\d{12}|\\d{13}|\\d{14})", message = "barcode must be a GTIN-8, UPC-A, EAN-13, or GTIN-14")
        @Schema(example = "3017620422003")
        String barcode,
        @NotBlank @Size(max = 500)
        @Schema(description = "Required audit reason for replacing the product barcode.", example = "Corrected from the verified package label.")
        String reason
) {
}
