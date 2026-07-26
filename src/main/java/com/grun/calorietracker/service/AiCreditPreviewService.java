package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiCreditPreviewDto;
import com.grun.calorietracker.enums.AiRequestType;

public interface AiCreditPreviewService {
    AiCreditPreviewDto preview(String email, AiRequestType requestType);
}