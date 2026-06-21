package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.PreferredLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search alias attached to a food product for multilingual search without duplicating product data.")
public class FoodSearchAliasDto {
    @Schema(description = "Alias id.", example = "10")
    private Long id;

    @Schema(description = "Food product id.", example = "123")
    private Long foodItemId;

    @Schema(description = "Original alias text shown to admins.", example = "yarım yağlı süt")
    private String alias;

    @Schema(description = "Normalized alias used by search.", example = "yarim yagli sut")
    private String normalizedAlias;

    @Schema(description = "Alias language.", example = "TR")
    private PreferredLanguage language;

    @Schema(description = "Alias classification.", example = "TRANSLATION")
    private FoodSearchAliasType aliasType;

    @Schema(description = "Alias source, such as admin, migration_seed, or ai_suggestion.", example = "admin")
    private String source;

    @Schema(description = "Whether this alias is active for search.", example = "true")
    private Boolean active;

    @Schema(description = "Creation timestamp.", example = "2026-06-19T13:45:00")
    private String createdAt;
}