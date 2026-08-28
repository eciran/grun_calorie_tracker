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
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 300) String> parserWarnings,
        @Size(max = 1_000) List<Map<String, Object>> wordBoxes,
        @Size(max = 100) Map<@Size(max = 80) String, Object> fieldEvidence,
        @Size(max = 100) Map<@Size(max = 80) String, Object> qualitySignals,
        @Size(max = 100) Map<@Size(max = 80) String, Object> decisions,
        @Size(max = 100) List<Map<String, Object>> correctionAudit,
        @Size(max = 100) Map<@Size(max = 80) String, Object> shadowV3Values,
        @Size(max = 100) Map<@Size(max = 80) String, Object> shadowV4Values,
        @Size(max = 100) Map<@Size(max = 80) String, Object> shadowUserValues
) {
    public FoodProductOcrExtractionDto(
            String engine,
            String engineVersion,
            String parserVersion,
            String locale,
            List<String> recognizedLines,
            Map<String, Object> parsedValues,
            List<String> parserWarnings) {
        this(engine, engineVersion, parserVersion, locale, recognizedLines, parsedValues, parserWarnings,
                null, null, null, null, null, null, null, null);
    }
}
