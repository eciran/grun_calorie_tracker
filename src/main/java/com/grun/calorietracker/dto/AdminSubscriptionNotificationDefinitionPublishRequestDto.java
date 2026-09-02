package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSubscriptionNotificationDefinitionPublishRequestDto(
        @NotNull @Valid AdminNotificationDefinitionRequestDto definition,
        @NotBlank @Size(max = 500) String reason) { }
