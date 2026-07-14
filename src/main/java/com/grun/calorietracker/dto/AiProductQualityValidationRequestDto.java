package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProductQualityValidationRequestDto {
    private String schemaVersion;
    private String promptVersion;
    private Long productId;
    private String name;
    private String displayName;
    private String shortDisplayName;
    private String brand;
    private String barcode;
    private String sourceKey;
    private String canonicalFoodKey;
    private MarketRegion marketRegion;
    private FoodDataSource dataSource;
    private FoodCatalogType catalogType;
    private VerificationStatus verificationStatus;
    private ImageStatus imageStatus;
    private FoodPreparationState preparationState;
    private String allergens;
    private String nutriScore;
    private Double calories;
    private Double protein;
    private Double fat;
    private Double carbs;
    private Double fiber;
    private Double sugar;
    private Double sodium;
    private Double potassium;
    private Double cholesterol;
    private Double calcium;
    private Double iron;
    private Double magnesium;
    private Double zinc;
    private Double vitaminA;
    private Double vitaminC;
    private Double vitaminD;
    private Double vitaminE;
    private Double vitaminB12;
    private Double saturatedFat;
    private Double transFat;
    private Double sugarAlcohol;
    private Double servingSizeGrams;
    private String servingUnit;
    private Integer qualityScore;
    private Integer confidenceScore;
    private Long usageCount;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime qualityValidatedAt;
    private List<LocalizationContext> localizations;
    private List<SearchAliasContext> searchAliases;
    private List<ServingOptionContext> servingOptions;
    private List<QualityIssueContext> activeQualityIssues;
    private CanonicalDuplicateContext canonicalDuplicate;
    private List<FoodProductEvidenceDto> sourceEvidence;
    private List<FoodProductEvidenceComparisonDto> evidenceComparisons;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalizationContext {
        private Long id;
        private String language;
        private String displayName;
        private String shortDisplayName;
        private String source;
        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchAliasContext {
        private Long id;
        private String alias;
        private String normalizedAlias;
        private String language;
        private String aliasType;
        private String source;
        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServingOptionContext {
        private Long id;
        private String label;
        private String unitType;
        private Double quantity;
        private Double gramWeight;
        private Double mlVolume;
        private Boolean defaultOption;
        private String source;
        private String qualityStatus;
        private List<ServingLocalizationContext> localizations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServingLocalizationContext {
        private Long id;
        private String language;
        private String label;
        private String source;
        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityIssueContext {
        private Long id;
        private String issueType;
        private String identifier;
        private String reason;
        private LocalDateTime firstDetectedAt;
        private LocalDateTime lastDetectedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CanonicalDuplicateContext {
        private String canonicalFoodKey;
        private Long resolvedPrimaryProductId;
        private List<CanonicalCandidateContext> candidates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CanonicalCandidateContext {
        private Long productId;
        private String displayName;
        private String brand;
        private String dataSource;
        private String marketRegion;
        private String preparationState;
        private String verificationStatus;
        private Integer qualityScore;
        private Integer confidenceScore;
        private Long usageCount;
    }
}