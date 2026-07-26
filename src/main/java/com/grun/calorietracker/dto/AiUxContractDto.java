package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiClientLifecycleStatus;
import com.grun.calorietracker.enums.PreferredLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Stable client-facing lifecycle, review and credit metadata shared by every AI result.")
public class AiUxContractDto {
    private String schemaVersion = "ai_ux_v1";
    private AiClientLifecycleStatus lifecycleStatus;
    private Boolean reviewRequired;
    private Boolean confirmationRequired;
    private Boolean retryable;
    private Integer creditCost;
    private Boolean creditCharged;
    private Integer aiBaseRemainingThisPeriod;
    private Integer aiAddonRemainingThisPeriod;
    private Integer aiRemainingThisPeriod;
    private PreferredLanguage outputLanguage;
}