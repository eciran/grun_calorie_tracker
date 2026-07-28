package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Manual fasting history creation or correction request.")
public class FastingHistoryCorrectionRequestDto {
    @NotNull
    private LocalDateTime startedAt;
    @NotNull
    private LocalDateTime endedAt;
    @Size(max = 500)
    private String note;
    @NotBlank
    @Size(max = 64)
    private String correctionReason;
}