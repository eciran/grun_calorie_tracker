package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FastingSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "Persisted fasting session.")
public class FastingSessionDto {
    @Schema(description = "Session id.", example = "12")
    private Long id;
    @Schema(description = "Session status.", example = "ACTIVE")
    private FastingSessionStatus status;
    @Schema(description = "Diary date assigned from session start.", example = "2026-06-05")
    private LocalDate fastingDate;
    @Schema(description = "Session start timestamp.", example = "2026-06-05T20:00:00")
    private LocalDateTime startedAt;
    @Schema(description = "Target end timestamp.", example = "2026-06-06T12:00:00")
    private LocalDateTime targetEndAt;
    @Schema(description = "Actual end timestamp.", example = "2026-06-06T12:10:00")
    private LocalDateTime endedAt;
    @Schema(description = "Time when the near-target reminder notification was created.", example = "2026-06-06T11:30:00")
    private LocalDateTime reminderSentAt;
    @Schema(description = "Target fasting duration in minutes.", example = "960")
    private Integer targetMinutes;
    @Schema(description = "Actual fasting duration in minutes.", example = "970")
    private Integer actualMinutes;
    @Schema(description = "Whether the target duration was reached.", example = "true")
    private Boolean targetReached;
    @Schema(description = "Optional user note.", example = "Felt good.")
    private String note;
}
