package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Safe transactional mail event metadata for admin monitoring.")
public class AdminMailEventDto {
    @Schema(description = "Mail event type.", example = "delivered")
    private String event;
    @Schema(description = "Recipient email returned by provider, when available.", example = "user@example.com")
    private String email;
    @Schema(description = "Email subject returned by provider, when available.", example = "Verify your GRun email")
    private String subject;
    @Schema(description = "Provider message id, when available.")
    private String messageId;
    @Schema(description = "Provider event timestamp, when available.")
    private String date;
    @Schema(description = "Provider reason field for failed/bounced/deferred events, when available.")
    private String reason;
}
