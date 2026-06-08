package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Safe admin mail monitoring response. Secrets are never returned.")
public class AdminMailMonitoringDto {
    @Schema(description = "Configured mail provider.", example = "BREVO")
    private String provider;
    @Schema(description = "Whether the provider API key is configured without exposing it.", example = "true")
    private boolean apiKeyConfigured;
    @Schema(description = "Whether live provider metrics were fetched successfully.", example = "true")
    private boolean providerReachable;
    @Schema(description = "Provider API base URL without credentials.", example = "https://api.brevo.com")
    private String providerBaseUrl;
    @Schema(description = "Configured sender email.", example = "no-reply@grun.app")
    private String fromEmail;
    @Schema(description = "Configured sender display name.", example = "GRun")
    private String fromName;
    @Schema(description = "Provider status message safe for admin UI.")
    private String statusMessage;
    @Schema(description = "Aggregated transactional counters returned by provider.")
    private Map<String, Long> counters;
    @Schema(description = "Recent transactional events returned by provider.")
    private List<AdminMailEventDto> recentEvents;
    @Schema(description = "Timestamp when monitoring data was checked.")
    private LocalDateTime checkedAt;
}
