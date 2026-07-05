package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Controlled AI coaching insight. It contains app-scoped observations and actions only.")
public class AiInsightResponseDto {
    private Long requestId;
    private AiRequestType requestType;
    private AiRequestStatus status;
    private AiProvider provider;
    private String model;
    private String title;
    private String summary;
    private List<String> highlights = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private Integer aiRemainingThisPeriod;
}
