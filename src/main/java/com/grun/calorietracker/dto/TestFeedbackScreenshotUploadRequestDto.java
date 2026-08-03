package com.grun.calorietracker.dto;
import jakarta.validation.constraints.*;
public record TestFeedbackScreenshotUploadRequestDto(
 @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String contentType,
 @NotNull @Min(1) @Max(6291456) Long sizeBytes,
 @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sha256) { }