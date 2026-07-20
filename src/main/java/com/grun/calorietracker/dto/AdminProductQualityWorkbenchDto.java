package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionSource;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.PreferredLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete admin workbench context for one food product.")
public class AdminProductQualityWorkbenchDto {
    private FoodProductDto product;
    private List<LocalizationDto> localizations;
    private List<FoodSearchAliasDto> aliases;
    private List<ServingOptionDto> servingOptions;
    private FoodProductEvidenceContextDto evidence;
    private List<FoodProductQualityIssueDto> qualityIssues;
    private List<ProductQualitySuggestionDto> suggestions;
    private AiProductQualityValidationRequestDto.CanonicalDuplicateContext canonicalDuplicate;
    private List<FoodProductReviewAuditDto> audit;

    public record LocalizationDto(
            Long id,
            PreferredLanguage language,
            String displayName,
            String shortDisplayName,
            String source,
            Boolean active
    ) {
    }

    public record ServingOptionDto(
            Long id,
            String label,
            FoodServingOptionUnit unitType,
            Double quantity,
            Double gramWeight,
            Double mlVolume,
            Boolean defaultOption,
            FoodServingOptionSource source,
            FoodServingOptionQualityStatus qualityStatus,
            List<ServingLocalizationDto> localizations
    ) {
    }

    public record ServingLocalizationDto(
            Long id,
            PreferredLanguage language,
            String label,
            String source,
            Boolean active
    ) {
    }
}
