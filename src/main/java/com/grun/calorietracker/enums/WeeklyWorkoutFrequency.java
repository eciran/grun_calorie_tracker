package com.grun.calorietracker.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Typical number of planned workout days per week.")
public enum WeeklyWorkoutFrequency {
    NONE,
    ONE_TO_TWO,
    THREE_TO_FOUR,
    FIVE_PLUS
}
