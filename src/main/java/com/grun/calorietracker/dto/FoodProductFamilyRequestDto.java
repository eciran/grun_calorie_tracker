package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FoodProductFamilyRequestDto(
        @NotBlank
        @Size(max = 180)
        @Pattern(regexp = "[a-z0-9]+(?:[a-z0-9:_-]*[a-z0-9])?", message = "familyKey must be a normalized stable key")
        String familyKey,
        @NotBlank @Size(max = 300) String displayName,
        @NotNull Long representativeProductId,
        @NotBlank @Size(min = 10, max = 1000) String evidenceReason,
        @NotNull @Size(min = 2, max = 20) List<@Valid FoodProductFamilyMemberRequestDto> members
) {
}
