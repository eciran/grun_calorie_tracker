package com.grun.calorietracker.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import jakarta.validation.constraints.*;
public record AdminApprovalCreateRequestDto(@NotNull AdminApprovalActionType actionType,
 @NotBlank @Size(max=128) String targetKey, @NotNull JsonNode payload,
 @NotBlank @Size(max=500) String reason) {}