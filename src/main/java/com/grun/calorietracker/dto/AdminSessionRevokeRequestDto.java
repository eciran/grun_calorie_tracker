package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSessionRevokeRequestDto(@NotBlank @Size(max=500) String reason) {}
