package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;

import java.util.UUID;

public interface AiInsightService {
    default AiInsightResponseDto createDailyInsight(String email, AiInsightRequestDto request) {
        return createDailyInsight(email, UUID.randomUUID().toString(), request);
    }
    AiInsightResponseDto createDailyInsight(
            String email, String idempotencyKey, AiInsightRequestDto request);
    default AiInsightResponseDto createWeeklyInsight(String email, AiInsightRequestDto request) {
        return createWeeklyInsight(email, UUID.randomUUID().toString(), request);
    }
    AiInsightResponseDto createWeeklyInsight(
            String email, String idempotencyKey, AiInsightRequestDto request);
}
