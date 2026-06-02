package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Photo meal AI draft request. The mobile app should upload/store the image first and send a short-lived reference URL or storage key.")
public class AiPhotoMealDraftRequestDto {
    @NotBlank(message = "Image reference is required")
    @Size(max = 2048, message = "Image reference must not exceed 2048 characters")
    @Schema(description = "Image URL or storage key that a future AI provider can access.", example = "s3://grun-meals/user-1/photo-123.jpg")
    private String imageReference;

    @Size(max = 1000, message = "User note must not exceed 1000 characters")
    @Schema(description = "Optional user note to improve AI interpretation.", example = "Lunch plate with rice and chicken")
    private String userNote;

    @Schema(description = "Suggested meal type from the client.", example = "LUNCH")
    private String mealType;

    @Schema(description = "Diary timestamp to use for the draft.", example = "2026-06-01T13:30:00")
    private LocalDateTime logDate;
}
