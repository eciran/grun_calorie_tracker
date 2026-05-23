package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Mobile application startup state for the authenticated user.")
public class AppStartupDto {

    @Schema(description = "Authenticated user profile.")
    private UserProfileDto profile;

    @Schema(description = "Current saved goal. Null when the user has not completed goal setup.")
    private UserGoalDto goal;

    @Schema(description = "Whether the user profile has enough data for calorie and goal calculations.", example = "true")
    private boolean profileComplete;

    @Schema(description = "Whether the user has a saved active goal.", example = "true")
    private boolean hasActiveGoal;

    @Schema(description = "Whether onboarding is complete enough for the main tracking flow.", example = "true")
    private boolean onboardingCompleted;

    @Schema(description = "Whether the user's email address is verified.", example = "false")
    private boolean emailVerified;

    @Schema(description = "Whether the account has a user-managed password set.", example = "false")
    private boolean passwordSet;

    @Schema(description = "Login providers linked to the authenticated account.")
    private List<LinkedIdentityDto> linkedIdentities;

    @Schema(description = "Whether the mobile app can open the dashboard directly.", example = "true")
    private boolean dashboardReady;

    @Schema(description = "Recommended next mobile screen or action.", example = "OPEN_DASHBOARD")
    private String nextStep;
}
