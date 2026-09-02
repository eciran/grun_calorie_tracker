package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSubscriptionNotificationPolicyRequestDto(
        @NotNull Long version,
        @NotNull Boolean requestedDeliveryEnabled,
        @NotBlank @Size(max = 500) String reason) { }
