package com.grun.calorietracker.dto;

import java.time.LocalDateTime;

public record AdminUnmatchedAiExerciseDto(Long id, String displayName, String normalizedName,
        String language, String equipment, String targetMuscleGroup, Long occurrenceCount,
        LocalDateTime firstSeenAt, LocalDateTime lastSeenAt, String status, Long resolvedExerciseItemId) {}
