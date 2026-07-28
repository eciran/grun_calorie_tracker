package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.grun.calorietracker.enums.FastingSessionOutcomeReason;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request to finish an active fasting session.")
public class FastingSessionFinishRequestDto {
    @Schema(description = "Optional explicit finish time. If omitted, server time is used.", example = "2026-06-06T12:00:00")
    private LocalDateTime endedAt;

    @Schema(description = "Structured completion or early-stop reason.")
    private FastingSessionOutcomeReason reason;

    @Size(max = 500, message = "{validation.fasting.note.size}")
    @Schema(description = "Optional completion note.", example = "Completed without issues.")
    private String note;
}
