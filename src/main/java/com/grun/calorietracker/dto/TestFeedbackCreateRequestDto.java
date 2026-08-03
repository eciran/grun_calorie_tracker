package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.TestFeedbackPlatform;
import com.grun.calorietracker.enums.TestFeedbackType;
import jakarta.validation.constraints.*;

public record TestFeedbackCreateRequestDto(
        @NotNull TestFeedbackType feedbackType,
        @NotNull TestFeedbackPlatform platform,
        @NotBlank @Size(max = 240) String route,
        @Size(max = 240) String previousRoute,
        @Size(max = 2000) String description,
        @Size(max = 40) String appVersion,
        @Size(max = 40) String buildNumber,
        @Size(max = 100) String easBuildId,
        @Pattern(regexp = "^[a-fA-F0-9]{7,64}$", message = "commitSha must be a hexadecimal Git SHA") String commitSha,
        @Size(max = 80) String osVersion,
        @Size(max = 120) String deviceModel,
        @Size(max = 20) String languageTag,
        @Size(max = 24) String marketRegion,
        @Min(100) @Max(599) Integer lastHttpStatus,
        @PositiveOrZero Long lastHttpDurationMs,
        @Size(max = 100) String lastCorrelationId,
        @Pattern(regexp = "^(ONLINE|OFFLINE|UNKNOWN|WIFI|CELLULAR|ETHERNET)$") String networkState
) { }
