package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Detailed payment provider event including raw payload.")
public class SubscriptionProviderEventDetailDto extends SubscriptionProviderEventDto {
    private String rawPayload;
}
