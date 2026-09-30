package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record AdminProductIntakeSubmittedFieldsUpdateRequestDto(
        @NotEmpty @Size(max = 9) Map<String, Object> fields
) {}
