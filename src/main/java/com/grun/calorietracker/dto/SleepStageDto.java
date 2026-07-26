package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SleepStageType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class SleepStageDto {
    private SleepStageType stageType;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private int durationMinutes;
}
