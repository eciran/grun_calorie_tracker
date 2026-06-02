package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI meal logging draft response. The draft must be reviewed and confirmed by the user before diary writes.")
public class AiMealDraftResponseDto {
    private Long requestId;
    private AiRequestType requestType;
    private AiProvider provider;
    private String model;
    private AiRequestStatus status;
    private String suggestedMealType;
    private LocalDateTime suggestedLogDate;
    private String summary;
    private AiSafetyResultDto safety;
    private List<AiMealDraftItemDto> items = new ArrayList<>();
    private Integer aiRemainingThisPeriod;
}
