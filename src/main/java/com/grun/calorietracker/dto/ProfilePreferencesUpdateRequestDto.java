package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.CountryCode;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Partial regional and display preference update for the authenticated user.")
public class ProfilePreferencesUpdateRequestDto {
    private MarketRegion marketRegion;
    private CountryCode countryCode;
    private PreferredLanguage preferredLanguage;

    @Size(max = 100, message = "{validation.user-profile.time-zone.max}")
    private String timeZone;

    private UnitPreference unitPreference;
}
