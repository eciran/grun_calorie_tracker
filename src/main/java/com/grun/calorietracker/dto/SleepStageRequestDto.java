package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SleepStageType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SleepStageRequestDto {
    @NotNull
    private SleepStageType stageType;
    @NotNull
    private OffsetDateTime startAt;
    @NotNull
    private OffsetDateTime endAt;
}
