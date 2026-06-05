package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodCatalogType;
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
