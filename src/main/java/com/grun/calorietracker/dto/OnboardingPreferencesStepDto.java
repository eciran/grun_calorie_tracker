package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Regional and display preferences collected during onboarding.")
public class OnboardingPreferencesStepDto {

    @NotNull(message = "Market region is required.")
    private MarketRegion marketRegion;

    @Schema(description = "ISO country code, independent from the food catalog market group.", example = "IE")
    private CountryCode countryCode;

    @NotNull(message = "Preferred language is required.")
    private PreferredLanguage preferredLanguage;

    @NotBlank(message = "Time zone is required.")
    private String timeZone;

    @NotNull(message = "Unit preference is required.")
    private UnitPreference unitPreference;
}
