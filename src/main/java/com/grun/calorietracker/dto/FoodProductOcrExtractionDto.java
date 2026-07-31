package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record FoodProductOcrExtractionDto(
        @NotBlank @Size(max = 80) String engine,
        @Size(max = 40) String engineVersion,
        @NotBlank @Size(max = 40) String parserVersion,
        @Size(max = 20) String locale,
        @NotEmpty @Size(max = 500) List<@NotBlank @Size(max = 500) String> recognizedLines,
        @NotNull @Size(max = 100) Map<@Size(max = 80) String, Object> parsedValues,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 300) String> parserWarnings
) {
}
