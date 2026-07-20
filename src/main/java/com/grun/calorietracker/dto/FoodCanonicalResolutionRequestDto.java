package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FoodCanonicalResolutionRequestDto {

    @NotBlank
    @Schema(example = "GLOBAL:GENERIC_INGREDIENT:RAW:banana")
    private String canonicalFoodKey;

    @NotNull
    @Schema(example = "1001")
    private Long primaryProductId;
}