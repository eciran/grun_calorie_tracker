package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FoodProductUploadSessionRequestDto(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotEmpty @Size(min = 2, max = 2) List<@Valid Asset> assets
) {
    public record Asset(
            @NotNull FoodProductReviewAssetType assetType,
            @NotBlank @Size(max = 80) String contentType,
            @Positive long sizeBytes,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String sha256
    ) {
    }
}
