package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RevenueCat webhook processing result.")
public class RevenueCatWebhookResponseDto {

    @Schema(description = "True when the event was accepted by the backend.")
    private Boolean accepted;

    @Schema(description = "True when this provider event had already been received before.")
    private Boolean duplicate;

    @Schema(description = "Provider event id used for idempotency.")
    private String providerEventId;

    @Schema(description = "Processing result: PROCESSED, IGNORED, or FAILED.")
    private String status;

    @Schema(description = "Short processing detail.")
    private String message;
}
