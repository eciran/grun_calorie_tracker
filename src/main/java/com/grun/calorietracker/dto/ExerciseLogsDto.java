package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExerciseLogsDto {

    private Long exerciseItemId;
    private String exerciseItemName;
    private Integer durationMinutes;
    private Double caloriesBurned;
    private LocalDateTime logDate;
    private String source;
    private String externalId;
    private String extraData;

}
