package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualityLabel;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food product response used by product search and barcode lookup.")
public class FoodProductDto {

    @Schema(description = "Internal product id.", example = "1")
    private Long id;

    @Schema(description = "Product barcode.", example = "3017620422003")
    private String barcode;

    @Schema(description = "Normalized barcode used internally for deduplication and lookup.", example = "3017620422003")
    private String normalizedBarcode;

    @Schema(description = "Stable internal or external source key used for non-barcode catalog records.", example = "TR:LOCAL_DISH:mercimek_corbasi")
    private String sourceKey;

    @Schema(description = "Product display name.", example = "Nutella")
    private String productName;

    @Schema(description = "Product brand.", example = "Ferrero")
    private String brand;

    @Schema(description = "Product image URL selected for display.", example = "https://example.com/product.jpg")
    private String imageUrl;

    @Schema(description = "Raw image URL imported from an external source.", example = "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_en.3.400.jpg")
    private String externalImageUrl;

    @Schema(description = "Approved or curated image URL that mobile clients should prefer.", example = "https://cdn.grun.app/products/3017620422003.jpg")
    private String displayImageUrl;

    @Schema(description = "Food product data source.", example = "OPEN_FOOD_FACTS")
    private FoodDataSource dataSource;

    @Schema(description = "Food catalog classification.", example = "BRANDED_PRODUCT")
    private FoodCatalogType catalogType;

    @Schema(description = "Catalog verification status for product data.", example = "NEEDS_REVIEW")
    private VerificationStatus verificationStatus;

    @Schema(description = "Source of the selected product image.", example = "OPEN_FOOD_FACTS")
    private ImageSource imageSource;

    @Schema(description = "Image quality review status.", example = "RAW")
    private ImageStatus imageStatus;

    @Schema(description = "Market region this food product belongs to.", example = "UK_IE")
    private MarketRegion marketRegion;

    @Schema(description = "How many times this product has been added to food logs.", example = "42")
    private Long usageCount;

    @Schema(description = "Computed product data quality score from 0 to 100.", example = "85")
    private Integer qualityScore;

    @Schema(description = "Computed admin review priority. Higher values should be reviewed first.", example = "120")
    private Integer reviewPriority;

    @Schema(description = "Last time this product was imported or synced from an external provider.", example = "2026-05-11T23:55:00")
    private String lastExternalSyncAt;

    @Schema(description = "Last time this product was reviewed by an admin.", example = "2026-05-11T23:55:00")
    private String lastReviewedAt;

    @Schema(description = "Admin identifier that last reviewed this product.", example = "admin@grun.app")
    private String reviewedBy;

    @Schema(description = "Calories per serving or configured base unit.", example = "539.0")
    private Double calories;

    @Schema(description = "Protein amount in grams.", example = "6.3")
    private Double protein;

    @Schema(description = "Fat amount in grams.", example = "30.9")
    private Double fat;

    @Schema(description = "Carbohydrate amount in grams.", example = "57.5")
    private Double carbs;

    @Schema(description = "Fiber amount in grams.", example = "3.4")
    private Double fiber;

    @Schema(description = "Sugar amount in grams.", example = "56.3")
    private Double sugar;

    @Schema(description = "Sodium amount.", example = "0.107")
    private Double sodium;

    @Schema(description = "Potassium amount per 100g/ml or configured product base.", example = "120.0")
    private Double potassium;

    @Schema(description = "Cholesterol amount per 100g/ml or configured product base.", example = "0.0")
    private Double cholesterol;

    @Schema(description = "Calcium amount per 100g/ml or configured product base.", example = "43.0")
    private Double calcium;

    @Schema(description = "Iron amount per 100g/ml or configured product base.", example = "1.2")
    private Double iron;

    @Schema(description = "Magnesium amount per 100g/ml or configured product base.", example = "22.0")
    private Double magnesium;

    @Schema(description = "Zinc amount per 100g/ml or configured product base.", example = "0.6")
    private Double zinc;

    @Schema(description = "Vitamin A amount per 100g/ml or configured product base.", example = "0.0")
    private Double vitaminA;

    @Schema(description = "Vitamin C amount per 100g/ml or configured product base.", example = "0.0")
    private Double vitaminC;

    @Schema(description = "Vitamin D amount per 100g/ml or configured product base.", example = "0.0")
    private Double vitaminD;

    @Schema(description = "Vitamin E amount per 100g/ml or configured product base.", example = "0.0")
    private Double vitaminE;

    @Schema(description = "Vitamin B12 amount per 100g/ml or configured product base.", example = "0.0")
    private Double vitaminB12;

    @Schema(description = "Saturated fat amount in grams per 100g/ml or configured product base.", example = "10.6")
    private Double saturatedFat;

    @Schema(description = "Trans fat amount in grams per 100g/ml or configured product base.", example = "0.0")
    private Double transFat;

    @Schema(description = "Sugar alcohol amount in grams per 100g/ml or configured product base.", example = "0.0")
    private Double sugarAlcohol;

    @Schema(description = "Serving size in grams used for SERVING and PIECE food log conversion.", example = "30.0")
    private Double servingSize;

    @Schema(description = "Display unit for the serving size.", example = "g")
    private String servingUnit;

    @Schema(description = "Product ingredient text.", example = "Sugar, palm oil, hazelnuts, cocoa...")
    private String ingredientsText;

    @Schema(description = "Known allergens.", example = "milk, nuts, soy")
    private String allergens;

    @Schema(description = "Nutri-Score grade when available.", example = "e")
    private String nutriScore;

    @Schema(description = "Whether this product was manually created by the authenticated user.", example = "false")
    private Boolean custom;

    @Schema(description = "User-facing product data quality label.", example = "VERIFIED")
    private ProductQualityLabel productQualityLabel;

    @Schema(description = "Short user-facing data quality message.", example = "Verified by GRun")
    private String productQualityMessage;

    @Schema(description = "Default product-specific serving option id when available.", example = "5")
    private Long defaultServingOptionId;
}
