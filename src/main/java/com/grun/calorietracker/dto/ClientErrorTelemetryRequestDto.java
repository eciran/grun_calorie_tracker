package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record ClientErrorTelemetryRequestDto(
        @NotBlank @Pattern(regexp = "ADMIN_WEB|MOBILE") String source,
        @NotBlank @Pattern(regexp = "NETWORK|TIMEOUT") String failureKind,
        @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
        @NotBlank @Size(max = 300) String route,
        @Size(max = 36) String correlationId,
        @Size(max = 24) String clientPlatform,
        @Size(max = 40) String appVersion,
        @NotNull Instant occurredAt,
        @PositiveOrZero @Max(300000) long durationMs) {}
