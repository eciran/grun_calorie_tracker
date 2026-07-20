package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiPreparationGuideGenerateRequestDto {
    @Size(max = 10)
    private String language;
}
