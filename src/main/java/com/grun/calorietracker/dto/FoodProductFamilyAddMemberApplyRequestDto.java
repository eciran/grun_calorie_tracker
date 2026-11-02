package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FoodProductFamilyAddMemberApplyRequestDto(
        @NotNull @Valid FoodProductFamilyMemberRequestDto member,
        @NotBlank @Size(min = 10, max = 1000) String reason,
        @NotBlank @Size(min = 64, max = 64) String previewFingerprint,
        @NotNull List<@NotBlank @Size(max = 1000) String> acknowledgedWarnings
) {
}
