package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Single request used by the mobile onboarding flow to complete profile and goal setup.")
public class OnboardingCompleteRequestDto {

    @NotBlank(message = "{validation.user-profile.name.required}")
    @Schema(description = "User display name.", example = "Emrah", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "{validation.user-profile.age.required}")
    @Min(value = 13, message = "{validation.user-profile.age.min}")
    @Max(value = 100, message = "{validation.user-profile.age.max}")
    @Schema(description = "User age in years.", example = "32", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer age;

    @NotBlank(message = "{validation.user-profile.gender.required}")
    @Schema(description = "User gender value used by calculations.", example = "MALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String gender;

    @NotNull(message = "{validation.user-profile.height.required}")
    @Min(value = 100, message = "{validation.user-profile.height.min}")
    @Max(value = 250, message = "{validation.user-profile.height.max}")
    @Schema(description = "User height in centimeters.", example = "180.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double height;

    @NotNull(message = "{validation.user-profile.weight.required}")
    @Min(value = 30, message = "{validation.user-profile.weight.min}")
    @Max(value = 300, message = "{validation.user-profile.weight.max}")
    @Schema(description = "User weight in kilograms.", example = "82.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double weight;

    @Min(value = 0, message = "{validation.user-profile.body-fat.min}")
    @Max(value = 80, message = "{validation.user-profile.body-fat.max}")
    @Schema(description = "Optional body fat percentage. If provided, calorie calculation uses Katch-McArdle.", example = "19.2")
    private Double bodyFat;

    @NotNull(message = "{validation.user-goal.target-weight.required}")
    @Min(value = 30, message = "{validation.user-goal.target-weight.min}")
    @Schema(description = "Target body weight in kilograms.", example = "78.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double targetWeight;

    @Schema(description = "Target weekly weight change in kilograms. The backend normalizes the sign by goal type.", example = "0.5")
    private Double weeklyWeightChangeTargetKg;

    @NotNull(message = "{validation.user-goal.goal-type.required}")
    @Schema(description = "User's goal type.", example = "LOSE_WEIGHT", requiredMode = Schema.RequiredMode.REQUIRED)
    private GoalType goalType;

    @NotNull(message = "{validation.user-goal.activity-level.required}")
    @Schema(description = "User's activity level.", example = "MODERATE", requiredMode = Schema.RequiredMode.REQUIRED)
    private ActivityLevel activityLevel;

    public UserProfileDto toUserProfileDto() {
        return UserProfileDto.builder()
                .name(name)
                .age(age)
                .gender(gender)
                .height(height)
                .weight(weight)
                .bodyFat(bodyFat)
                .build();
    }

    public GoalCalculationRequestDto toGoalCalculationRequestDto() {
        return new GoalCalculationRequestDto(targetWeight, weeklyWeightChangeTargetKg, goalType, activityLevel);
    }
}
