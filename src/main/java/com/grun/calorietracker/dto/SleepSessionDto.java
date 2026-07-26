package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.enums.SleepQualityConfidence;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class SleepSessionDto {
    private Long id;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private LocalDate sleepDate;
    private int durationMinutes;
    private String timeZone;
    private HealthProvider provider;
    private String externalId;
    private int qualityScore;
    private SleepQualityConfidence qualityConfidence;
    private String note;
    private List<SleepStageDto> stages;
}
