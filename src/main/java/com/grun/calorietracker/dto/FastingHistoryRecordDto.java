package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Correctable fasting history record.")
public class FastingHistoryRecordDto {
    private Long sessionId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer actualMinutes;
    private Boolean targetReached;
    private Boolean manualEntry;
    private String note;
}