package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.PreferredLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to create or reactivate a product search alias.")
public class FoodSearchAliasRequestDto {
    @NotBlank
    @Schema(description = "Alias text users may search for.", example = "yarım yağlı süt", requiredMode = Schema.RequiredMode.REQUIRED)
    private String alias;

    @NotNull
    @Schema(description = "Alias language.", example = "TR", requiredMode = Schema.RequiredMode.REQUIRED)
    private PreferredLanguage language;

    @Schema(description = "Alias type. Defaults to ADMIN_MANUAL when omitted.", example = "TRANSLATION")
    private FoodSearchAliasType aliasType;

    @Schema(description = "Source note for audit/debugging.", example = "admin")
    private String source;

    @Schema(description = "Whether alias should be active immediately. Defaults to true.", example = "true")
    private Boolean active;
}