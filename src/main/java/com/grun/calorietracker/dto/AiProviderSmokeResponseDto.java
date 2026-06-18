package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiProviderSmokeResponseDto {
    private AiProvider provider;
    private String model;
    private AiRequestType requestType;
    private boolean configured;
    private boolean providerReachable;
    private String status;
    private String message;
    private Integer returnedItemCount;
    private LocalDateTime checkedAt;
}
