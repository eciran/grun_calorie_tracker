package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PreferredLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record AdminSubscriptionNotificationPreviewRequestDto(
        @NotBlank @Pattern(regexp = "(?:subscription_[a-z_]+|ai_addon_purchased)") String definitionKey,
        @NotNull PreferredLanguage language,
        @NotNull @Size(max = 12) Map<String, @NotBlank @Size(max = 200) String> parameters) { }
