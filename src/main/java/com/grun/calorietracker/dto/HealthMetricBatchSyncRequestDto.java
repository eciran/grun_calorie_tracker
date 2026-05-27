package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Batch health metric sync payload sent by the mobile app.")
public class HealthMetricBatchSyncRequestDto {

    @NotEmpty
    @Size(max = 500)
    @Valid
    @Schema(description = "Normalized health metrics to sync. Maximum 500 metrics per request.")
    private List<HealthMetricSyncRequestDto> metrics;
}
