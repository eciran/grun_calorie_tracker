package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductIntakeApplyField;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminProductIntakeApplyRequestDto(
        @NotEmpty @Size(max = 9) Set<@NotNull ProductIntakeApplyField> fields,
        boolean confirmed
) {
}