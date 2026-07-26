package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Safe basic-profile update for the authenticated user.")
public class MyProfileUpdateRequestDto {
    @NotBlank(message = "{validation.user-profile.name.required}")
    @Size(max = 100, message = "{validation.user-profile.name.max}")
    private String name;
}
