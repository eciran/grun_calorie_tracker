package com.grun.calorietracker.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveUnmatchedAiExerciseRequestDto(@NotNull Long exerciseItemId, Boolean createAlias) {}
