package com.grun.calorietracker.dto;

import lombok.Data;

@Data
public class AiNutritionPlanConfirmRequestDto {
    /**
     * Deprecated compatibility field. Confirmation always uses the immutable,
     * server-stored draft; client-provided draft content is intentionally ignored.
     */
    @Deprecated
    private AiNutritionPlanDraftResponseDto draft;
}
