package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Regional and display preferences for the authenticated user.")
public class ProfilePreferencesDto {
    private MarketRegion marketRegion;
    private CountryCode countryCode;
    private PreferredLanguage preferredLanguage;
    private String timeZone;
    private UnitPreference unitPreference;
}
