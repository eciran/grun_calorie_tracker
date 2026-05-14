package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
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
}
