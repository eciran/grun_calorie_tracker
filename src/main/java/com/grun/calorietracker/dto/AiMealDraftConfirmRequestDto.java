package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "User confirmation request for an AI meal draft.")
public class AiMealDraftConfirmRequestDto {
    @Valid
    @NotEmpty(message = "At least one confirmed food item is required")
    @Size(max = 20, message = "At most 20 food items can be confirmed from one AI draft")
    private List<AiMealDraftConfirmItemRequestDto> items;
}
