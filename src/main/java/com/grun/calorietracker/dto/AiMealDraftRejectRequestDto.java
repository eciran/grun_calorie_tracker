package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiDraftRejectReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Optional feedback when a user rejects an AI meal draft.")
public class AiMealDraftRejectRequestDto {

    @Schema(description = "User-selected reason for rejecting the AI draft.", example = "IRRELEVANT_RESULT")
    private AiDraftRejectReason reason;

    @Size(max = 500, message = "Feedback must be 500 characters or fewer.")
    @Schema(description = "Optional user explanation to improve AI quality. Limited to 500 characters.",
            example = "The photo was a salad, but the result suggested pizza.")
    private String feedback;
}
