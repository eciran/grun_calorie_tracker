package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;

public interface AiInsightService {
    AiInsightResponseDto createDailyInsight(String email, AiInsightRequestDto request);
    AiInsightResponseDto createWeeklyInsight(String email, AiInsightRequestDto request);
}
