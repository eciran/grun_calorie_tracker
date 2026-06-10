package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductAnalyticsEventDto {
    private Long id;
    private ProductAnalyticsEventType eventType;
    private String surface;
    private Long durationMs;
    private String targetType;
    private Long targetId;
    private LocalDateTime createdAt;
}
