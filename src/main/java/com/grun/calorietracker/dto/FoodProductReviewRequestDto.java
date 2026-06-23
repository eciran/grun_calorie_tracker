package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin request payload for reviewing imported food product data and display image quality.")
public class FoodProductReviewRequestDto {

    @Schema(description = "Curated product display name.", example = "Nutella Hazelnut Spread")
    private String productName;

    @Schema(description = "Approved image URL that mobile clients should prefer.", example = "https://cdn.grun.app/products/3017620422003.jpg")
    private String displayImageUrl;

    @Schema(description = "Product data verification status.", example = "VERIFIED")
    private VerificationStatus verificationStatus;

    @Schema(description = "Curated image source.", example = "ADMIN_UPLOAD")
    private ImageSource imageSource;

    @Schema(description = "Image review status.", example = "APPROVED")
    private ImageStatus imageStatus;

    @Schema(description = "Market region for this food product. Supported values: GLOBAL, TR, UK_IE, EU.", example = "UK_IE")
    private MarketRegion marketRegion;

    @Schema(description = "Food catalog classification.", example = "BRANDED_PRODUCT")
    private FoodCatalogType catalogType;

    @Schema(description = "Preparation/cooking state for raw, cooked, grilled, fried, baked, or prepared foods.", example = "COOKED")
    private FoodPreparationState preparationState;

    @Schema(description = "Calories per 100g/ml or configured product base.", example = "539.0")
    private Double calories;

    @Schema(description = "Protein amount in grams per 100g/ml or configured product base.", example = "6.3")
    private Double protein;

    @Schema(description = "Fat amount in grams per 100g/ml or configured product base.", example = "30.9")
    private Double fat;

    @Schema(description = "Carbohydrate amount in grams per 100g/ml or configured product base.", example = "57.5")
    private Double carbs;

    @Schema(description = "Fiber amount in grams per 100g/ml or configured product base.", example = "3.4")
    private Double fiber;

    @Schema(description = "Sugar amount in grams per 100g/ml or configured product base.", example = "56.3")
    private Double sugar;

    @Schema(description = "Sodium amount per 100g/ml or configured product base.", example = "0.107")
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
    private Double servingSizeGrams;

    @Schema(description = "Display unit for the serving size.", example = "g")
    private String servingUnit;

    @Schema(description = "Admin review note. Required when rejecting product data or image.", example = "Image is blurry and product label is unreadable.")
    private String reviewNote;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDisplayImageUrl() {
        return displayImageUrl;
    }

    public void setDisplayImageUrl(String displayImageUrl) {
        this.displayImageUrl = displayImageUrl;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public ImageSource getImageSource() {
        return imageSource;
    }

    public void setImageSource(ImageSource imageSource) {
        this.imageSource = imageSource;
    }

    public ImageStatus getImageStatus() {
        return imageStatus;
    }

    public void setImageStatus(ImageStatus imageStatus) {
        this.imageStatus = imageStatus;
    }

    public MarketRegion getMarketRegion() {
        return marketRegion;
    }

    public void setMarketRegion(MarketRegion marketRegion) {
        this.marketRegion = marketRegion;
    }

    public FoodCatalogType getCatalogType() {
        return catalogType;
    }

    public void setCatalogType(FoodCatalogType catalogType) {
        this.catalogType = catalogType;
    }

    public FoodPreparationState getPreparationState() {
        return preparationState;
    }

    public void setPreparationState(FoodPreparationState preparationState) {
        this.preparationState = preparationState;
    }

    public Double getCalories() {
        return calories;
    }

    public void setCalories(Double calories) {
        this.calories = calories;
    }

    public Double getProtein() {
        return protein;
    }

    public void setProtein(Double protein) {
        this.protein = protein;
    }

    public Double getFat() {
        return fat;
    }

    public void setFat(Double fat) {
        this.fat = fat;
    }

    public Double getCarbs() {
        return carbs;
    }

    public void setCarbs(Double carbs) {
        this.carbs = carbs;
    }

    public Double getFiber() {
        return fiber;
    }

    public void setFiber(Double fiber) {
        this.fiber = fiber;
    }

    public Double getSugar() {
        return sugar;
    }

    public void setSugar(Double sugar) {
        this.sugar = sugar;
    }

    public Double getSodium() {
        return sodium;
    }

    public void setSodium(Double sodium) {
        this.sodium = sodium;
    }

    public Double getPotassium() {
        return potassium;
    }

    public void setPotassium(Double potassium) {
        this.potassium = potassium;
    }

    public Double getCholesterol() {
        return cholesterol;
    }

    public void setCholesterol(Double cholesterol) {
        this.cholesterol = cholesterol;
    }

    public Double getCalcium() {
        return calcium;
    }

    public void setCalcium(Double calcium) {
        this.calcium = calcium;
    }

    public Double getIron() {
        return iron;
    }

    public void setIron(Double iron) {
        this.iron = iron;
    }

    public Double getMagnesium() {
        return magnesium;
    }

    public void setMagnesium(Double magnesium) {
        this.magnesium = magnesium;
    }

    public Double getZinc() {
        return zinc;
    }

    public void setZinc(Double zinc) {
        this.zinc = zinc;
    }

    public Double getVitaminA() {
        return vitaminA;
    }

    public void setVitaminA(Double vitaminA) {
        this.vitaminA = vitaminA;
    }

    public Double getVitaminC() {
        return vitaminC;
    }

    public void setVitaminC(Double vitaminC) {
        this.vitaminC = vitaminC;
    }

    public Double getVitaminD() {
        return vitaminD;
    }

    public void setVitaminD(Double vitaminD) {
        this.vitaminD = vitaminD;
    }

    public Double getVitaminE() {
        return vitaminE;
    }

    public void setVitaminE(Double vitaminE) {
        this.vitaminE = vitaminE;
    }

    public Double getVitaminB12() {
        return vitaminB12;
    }

    public void setVitaminB12(Double vitaminB12) {
        this.vitaminB12 = vitaminB12;
    }

    public Double getSaturatedFat() {
        return saturatedFat;
    }

    public void setSaturatedFat(Double saturatedFat) {
        this.saturatedFat = saturatedFat;
    }

    public Double getTransFat() {
        return transFat;
    }

    public void setTransFat(Double transFat) {
        this.transFat = transFat;
    }

    public Double getSugarAlcohol() {
        return sugarAlcohol;
    }

    public void setSugarAlcohol(Double sugarAlcohol) {
        this.sugarAlcohol = sugarAlcohol;
    }

    public Double getServingSizeGrams() {
        return servingSizeGrams;
    }

    public void setServingSizeGrams(Double servingSizeGrams) {
        this.servingSizeGrams = servingSizeGrams;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(String servingUnit) {
        this.servingUnit = servingUnit;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }
}
