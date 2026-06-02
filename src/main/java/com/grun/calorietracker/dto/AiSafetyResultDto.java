package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Safety review metadata for an AI meal draft.")
public class AiSafetyResultDto {
    @Schema(description = "Whether the draft requires extra user review before confirmation.", example = "true")
    private boolean reviewRequired;

    @Schema(description = "Whether the request or provider response was blocked by backend safety rules.", example = "false")
    private boolean blocked;

    @Schema(description = "Safety rule identifiers that were triggered.")
    private List<String> reasons = new ArrayList<>();

    public static AiSafetyResultDto clear() {
        return new AiSafetyResultDto(false, false, new ArrayList<>());
    }

    public static AiSafetyResultDto blocked(String reason) {
        return new AiSafetyResultDto(true, true, new ArrayList<>(List.of(reason)));
    }

    public void addReviewReason(String reason) {
        reviewRequired = true;
        reasons.add(reason);
    }
}
