package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.RetentionPolicyKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicyDto {
    private Long id;
    private RetentionPolicyKey policyKey;
    private Integer retentionDays;
    private String legalBasis;
    private String description;
    private Boolean active;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
