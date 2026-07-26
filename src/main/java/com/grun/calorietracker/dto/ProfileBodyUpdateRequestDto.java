package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Partial body-metric update for the authenticated user.")
public class ProfileBodyUpdateRequestDto {
    @Min(value = 13, message = "{validation.user-profile.age.min}")
    @Max(value = 100, message = "{validation.user-profile.age.max}")
    private Integer age;

    @Schema(description = "Date of birth. Overrides legacy age when supplied.", example = "1994-06-18")
    private LocalDate birthDate;

    @Pattern(regexp = "(?i)MALE|FEMALE", message = "{validation.user-profile.gender.invalid}")
    private String gender;

    @Min(value = 100, message = "{validation.user-profile.height.min}")
    @Max(value = 250, message = "{validation.user-profile.height.max}")
    private Double height;

    @Min(value = 30, message = "{validation.user-profile.weight.min}")
    @Max(value = 300, message = "{validation.user-profile.weight.max}")
    private Double weight;

    @Min(value = 0, message = "{validation.user-profile.body-fat.min}")
    @Max(value = 80, message = "{validation.user-profile.body-fat.max}")
    private Double bodyFat;
}
