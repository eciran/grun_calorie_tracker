package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paged product quality suggestion result.")
public class ProductQualitySuggestionPageDto {
    private List<ProductQualitySuggestionDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
