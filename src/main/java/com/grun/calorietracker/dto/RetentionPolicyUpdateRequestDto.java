package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicyUpdateRequestDto {

    @NotNull
    @Min(0)
    @Max(36500)
    @Schema(description = "Retention duration in days. Use 0 only for immediate purge policies.", example = "2555")
    private Integer retentionDays;

    @NotBlank
    @Schema(description = "Legal basis for keeping the data.", example = "Contract and tax/payment audit obligation")
    private String legalBasis;

    @NotBlank
    @Schema(description = "Plain-language policy explanation for admin/legal review.", example = "Payment audit events are retained for reconciliation and fraud prevention.")
    private String description;

    @NotNull
    @Schema(description = "Whether this retention rule is currently active.", example = "true")
    private Boolean active;
}
