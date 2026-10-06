package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.PreferredLanguage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminFoodServingOptionUpsertRequestDto {

    @NotBlank
    @Size(max = 120)
    private String label;

    @NotNull
    private FoodServingOptionUnit unitType;

    @NotNull
    @Positive
    private Double quantity;

    @Positive
    private Double gramWeight;

    @Positive
    private Double mlVolume;

    private Boolean defaultOption = false;
    private Boolean userVisible = true;
    private FoodServingOptionQualityStatus qualityStatus = FoodServingOptionQualityStatus.VERIFIED;

    @Valid
    @Size(min = 2, max = 2)
    private List<Localization> localizations = new ArrayList<>();

    public record Localization(
            @NotNull PreferredLanguage language,
            @NotBlank @Size(max = 120) String label
    ) {
    }
}
