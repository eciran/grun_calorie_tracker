package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GdprDeleteRequestDto {
    @NotBlank(message = "confirmText is required")
    private String confirmText;

    @NotBlank(message = "currentPassword is required")
    private String currentPassword;
}
