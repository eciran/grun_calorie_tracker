package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Password setup or change result.")
public record AccountPasswordResponseDto(
        @Schema(description = "Operation result message.", example = "Password updated successfully.")
        String message
) {
}
