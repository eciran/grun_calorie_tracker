package com.grun.calorietracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminAiOperationsPolicyUpdateRequestDto {
    @NotNull
    private Long version;
    @NotNull
    private Boolean circuitOpen;
    @NotNull @DecimalMin("0.01") @DecimalMax("1.0")
    private Double failureRateThreshold;
    @NotNull @DecimalMin("0.01") @DecimalMax("1.0")
    private Double rejectionRateThreshold;
    @NotNull @Min(1000) @Max(1000000000)
    private Long maxTokensPer24Hours;
    @NotNull @DecimalMin("0.01") @DecimalMax("1000000")
    private Double maxCostPer24Hours;
    @NotBlank @Pattern(regexp = "[A-Z]{3,12}")
    private String costCurrency;
    @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._:-]+")
    private String activeModel;
    @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._:-]+")
    private String activePromptVersion;
    @NotBlank @Size(min = 8, max = 500)
    private String reason;
}