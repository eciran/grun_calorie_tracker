package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile data returned to the mobile client.")
public class UserProfileDto {
    @Schema(description = "User id.", example = "1")
    private Long id;

    @Schema(description = "User email address.", example = "user@example.com")
    private String email;

    @Schema(description = "User display name.", example = "Emrah")
    private String name;

    @Schema(description = "User age in years.", example = "32")
    private Integer age;

    @Schema(description = "User gender value used by body composition calculations.", example = "MALE")
    private String gender;

    @Schema(description = "User height in centimeters.", example = "180.0")
    private Double height;

    @Schema(description = "User weight in kilograms.", example = "82.0")
    private Double weight;

    @Schema(description = "Latest calculated BMI.", example = "25.3")
    private Double bmi;

    @Schema(description = "Latest calculated body fat percentage.", example = "19.2")
    private Double bodyFat;

    @Schema(
            description = "Whether the mobile app should ask the user to recalculate and confirm calorie goals after profile changes.",
            example = "true"
    )
    private Boolean goalRecalculationRecommended;

    @Schema(description = "Reason why goal recalculation is recommended.", example = "Profile metrics that affect calorie calculation changed.")
    private String goalRecalculationReason;

}
