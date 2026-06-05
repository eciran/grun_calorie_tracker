package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductQualityIssue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Persistent quality issue detected for a food product.")
public class FoodProductQualityIssueDto {

    private Long id;
    private Long foodItemId;
    private FoodProductQualityIssue issueType;
    private String identifier;
    private String reason;
    private Boolean resolved;
    private LocalDateTime firstDetectedAt;
    private LocalDateTime lastDetectedAt;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
}
