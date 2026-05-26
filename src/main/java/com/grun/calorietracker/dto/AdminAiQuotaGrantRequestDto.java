package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AdminAiQuotaGrantRequestDto {
    @NotNull(message = "{validation.subscription.ai-addon.required}")
    @Positive(message = "{validation.subscription.ai-addon.positive}")
    private Integer amount;

    @NotNull(message = "{validation.subscription.ai-addon-validity.required}")
    @Positive(message = "{validation.subscription.ai-addon-validity.positive}")
    private Integer validityDays;

    private String note;
}
