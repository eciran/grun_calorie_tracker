package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fasting window context derived for a diary entry at response time.")
public record FastingDiaryContextDto(
        @Schema(description = "Whether the entry falls inside the planned fasting window.") boolean insidePlannedWindow,
        @Schema(description = "Whether the entry falls inside the actual fasting session window.") boolean insideActualWindow,
        @Schema(description = "Related fasting program occurrence id, when present.") Long occurrenceId,
        @Schema(description = "Related fasting session id, when present.") Long sessionId
) {
    public static FastingDiaryContextDto outsideWindow() {
        return new FastingDiaryContextDto(false, false, null, null);
    }
}