package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ProductTourStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ProductTourDto {
    private String tourKey;
    private String version;
    private ProductTourStatus status;
    private LocalDateTime completedAt;
}
