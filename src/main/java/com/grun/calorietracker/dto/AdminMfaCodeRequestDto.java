package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMfaCodeRequestDto(
        @NotBlank @Size(max = 32) String code,
        com.grun.calorietracker.enums.AdminReauthenticationPurpose purpose
) {}