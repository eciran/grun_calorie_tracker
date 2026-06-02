package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Result of confirming an AI meal draft into actual food logs.")
public class AiMealDraftConfirmResponseDto {
    private Long requestId;
    private AiRequestStatus status;
    private List<FoodLogsDto> createdLogs;
}
