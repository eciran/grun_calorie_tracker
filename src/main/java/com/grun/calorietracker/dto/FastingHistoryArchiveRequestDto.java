package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FastingHistoryArchiveRequestDto {
    @NotBlank
    @Size(max = 64)
    private String correctionReason;
}