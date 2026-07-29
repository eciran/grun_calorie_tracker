package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class GroceryListRefreshRequestDto {

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;
}
