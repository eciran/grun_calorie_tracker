package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRecipeImportReviewRequestDto {
    @Size(max = 1000)
    private String reviewNote;
}
