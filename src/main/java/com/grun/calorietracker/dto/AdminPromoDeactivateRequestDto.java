package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminPromoDeactivateRequestDto {
    @NotBlank @Size(max = 500)
    private String reason;
}
