package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRuntimePolicyRollbackRequestDto {
    @NotNull
    private Long version;
    @NotBlank @Size(min = 8, max = 500)
    private String reason;
}
