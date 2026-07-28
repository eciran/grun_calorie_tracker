package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.grun.calorietracker.enums.FastingSessionOutcomeReason;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request for cancelling an active fasting session.")
public class FastingSessionCancelRequestDto {

    @Schema(description = "Optional cancellation time. Defaults to current server time.", example = "2026-06-06T08:30:00")
    private LocalDateTime cancelledAt;

    @Schema(description = "Structured cancellation reason.")
    private FastingSessionOutcomeReason reason;

    @Size(max = 500, message = "{validation.fasting.note.size}")
    @Schema(description = "Optional cancellation note.", example = "Started by mistake.")
    private String note;
}
