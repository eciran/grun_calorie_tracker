package com.grun.calorietracker.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*;
public record AdminMealReminderDefinitionPublishRequestDto(@NotNull @Valid AdminNotificationDefinitionRequestDto definition,
 @NotBlank @Size(max=500) String reason) {}
