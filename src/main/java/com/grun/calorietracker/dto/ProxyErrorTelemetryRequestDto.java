package com.grun.calorietracker.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record ProxyErrorTelemetryRequestDto(@NotNull UUID eventKey, @NotNull Instant occurredAt,
        @NotNull @Min(502) @Max(504) Integer status,
        @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
        @NotBlank @Size(max = 300) String route, @Size(max = 36) String correlationId,
        @PositiveOrZero @Max(300000) long durationMs) {}
