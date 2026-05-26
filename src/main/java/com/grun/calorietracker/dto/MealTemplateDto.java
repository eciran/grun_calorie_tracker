package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "User-owned saved meal template.")
public class MealTemplateDto {

    private Long id;
    private String name;
    private String mealType;
    private LocalDateTime createdAt;
    private List<MealTemplateItemDto> items;
}
