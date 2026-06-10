package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Schema(description = "Client-side product analytics event used for measuring speed, funnel and retention KPIs.")
public class ProductAnalyticsEventRequestDto {

    @NotNull(message = "Event type is required.")
    @Schema(description = "Type of product event.", example = "LOG_FLOW_COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProductAnalyticsEventType eventType;

    @Size(max = 120)
    @Schema(description = "Client surface or screen that emitted the event.", example = "food_diary_quick_log")
    private String surface;

    @Size(max = 20)
    @Schema(description = "Client language at the time of the event.", example = "tr")
    private String language;

    @Schema(description = "When the measured action started.")
    private LocalDateTime startedAt;

    @Schema(description = "When the measured action completed.")
    private LocalDateTime completedAt;

    @Schema(description = "Client-measured duration in milliseconds.", example = "8200")
    private Long durationMs;

    @Size(max = 80)
    @Schema(description = "Optional target type for selected objects.", example = "FOOD_PRODUCT")
    private String targetType;

    @Schema(description = "Optional target id for selected objects.", example = "123")
    private Long targetId;

    @Schema(description = "Small metadata map. Do not send raw transcripts, full prompts, or sensitive content.")
    private Map<String, Object> metadata;
}
