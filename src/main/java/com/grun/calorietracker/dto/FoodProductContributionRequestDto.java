package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Metadata accompanying a private packaged-food label upload.")
public class FoodProductContributionRequestDto {

    @NotBlank
    @Pattern(regexp = "(?:\\d{8}|\\d{12}|\\d{13}|\\d{14})", message = "barcode must be a GTIN-8, UPC-A, EAN-13, or GTIN-14")
    private String barcode;

    @NotBlank
    @Size(max = 255)
    private String productName;

    @NotBlank
    @Size(max = 160)
    private String brand;

    @NotNull
    private MarketRegion marketRegion;

    @NotNull @PositiveOrZero @DecimalMax("1000")
    private Double calories;

    @NotNull @PositiveOrZero @DecimalMax("100")
    private Double protein;

    @NotNull @PositiveOrZero @DecimalMax("100")
    private Double fat;

    @NotNull @PositiveOrZero @DecimalMax("100")
    private Double carbs;

    @PositiveOrZero @DecimalMax("100")
    private Double fiber;

    @PositiveOrZero @DecimalMax("100")
    private Double sugar;

    @PositiveOrZero
    private Double sodium;

    @PositiveOrZero
    private Double servingSizeGrams;

    @Size(max = 40)
    private String servingUnit;

    @AssertTrue(message = "commercial use consent is required")
    private boolean commercialUseAllowed;

    @AssertTrue(message = "persistent storage consent is required")
    private boolean persistentStorageAllowed;
}