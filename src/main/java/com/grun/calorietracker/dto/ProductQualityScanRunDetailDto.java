package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed product quality scan run with product-level results.")
public class ProductQualityScanRunDetailDto {
    private ProductQualityScanRunDto run;
    private List<ProductQualityScanRunItemDto> items;
}