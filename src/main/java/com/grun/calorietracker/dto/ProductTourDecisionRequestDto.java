package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductTourStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductTourDecisionRequestDto {
    @NotNull
    private ProductTourStatus status;
}
