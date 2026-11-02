package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FoodProductFamilyApplyRequestDto(
        @NotBlank
        @Size(max = 180)
        @Pattern(regexp = "[a-z0-9]+(?:[a-z0-9:_-]*[a-z0-9])?", message = "familyKey must be a normalized stable key")
        String familyKey,
        @NotBlank @Size(max = 300) String displayName,
        @NotNull Long representativeProductId,
        @NotBlank @Size(min = 10, max = 1000) String evidenceReason,
        @NotNull @Size(min = 2, max = 20) List<@Valid FoodProductFamilyMemberRequestDto> members,
        @NotBlank @Size(min = 64, max = 64) @Pattern(regexp = "[0-9a-f]{64}") String previewFingerprint,
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 1000) String> acknowledgedWarnings
) {
    public FoodProductFamilyRequestDto familyRequest() {
        return new FoodProductFamilyRequestDto(
                familyKey, displayName, representativeProductId, evidenceReason, members
        );
    }
}
