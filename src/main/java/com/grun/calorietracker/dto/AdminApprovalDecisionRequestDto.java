package com.grun.calorietracker.dto;
import jakarta.validation.constraints.*;
public record AdminApprovalDecisionRequestDto(@NotBlank @Size(max=500) String reason) {}