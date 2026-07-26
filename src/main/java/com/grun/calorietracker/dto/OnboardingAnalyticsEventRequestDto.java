package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.OnboardingClientAnalyticsEventType;
import com.grun.calorietracker.enums.OnboardingStep;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Privacy-safe client onboarding event. Health and profile values are intentionally not accepted.")
public class OnboardingAnalyticsEventRequestDto {

    @NotNull(message = "Onboarding event type is required.")
    @Schema(description = "Client-observed onboarding lifecycle event.", example = "STEP_VIEWED")
    private OnboardingClientAnalyticsEventType eventType;

    @Schema(description = "Onboarding step related to the event.", example = "GOAL")
    private OnboardingStep step;

    @Pattern(regexp = "^[A-Za-z]{2,3}([_-][A-Za-z]{2,4})?$", message = "Language must be a short locale code.")
    @Schema(description = "Short client locale code.", example = "en_IE")
    private String language;

    @Min(value = 0, message = "Duration must not be negative.")
    @Max(value = 86400000, message = "Duration must not exceed 24 hours.")
    @Schema(description = "Optional client-measured time spent in onboarding.", example = "32000")
    private Long durationMs;

    @AssertTrue(message = "Step is required for a viewed event.")
    @Schema(hidden = true)
    public boolean isStepValid() {
        return eventType != OnboardingClientAnalyticsEventType.STEP_VIEWED || step != null;
    }
}
