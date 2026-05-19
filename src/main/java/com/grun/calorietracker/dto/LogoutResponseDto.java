package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Logout operation response.")
public class LogoutResponseDto {

    @Schema(description = "Human-readable logout result.", example = "Logout successful")
    private String message;
}
