package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin request to validate selected products with AI.")
public class AdminProductQualityAiValidationRequestDto {
    private List<Long> productIds;
    private List<Long> suggestionIds;
    private Integer limit;
    private Boolean forceRescan;
}
