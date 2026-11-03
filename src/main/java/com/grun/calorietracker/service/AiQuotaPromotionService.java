package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAiQuotaPromotionPolicyDto;
import com.grun.calorietracker.dto.AdminAiQuotaPromotionPolicyRequestDto;
import com.grun.calorietracker.dto.AiQuotaPromotionDecisionDto;
import com.grun.calorietracker.dto.AiQuotaPromotionDecisionRequestDto;
import com.grun.calorietracker.dto.AiQuotaPromotionEventRequestDto;

public interface AiQuotaPromotionService {
    AdminAiQuotaPromotionPolicyDto getPolicy();
    AdminAiQuotaPromotionPolicyDto updatePolicy(AdminAiQuotaPromotionPolicyRequestDto request, String admin, String correlationId);
    AiQuotaPromotionDecisionDto decide(String email, AiQuotaPromotionDecisionRequestDto request);
    void recordImpression(String email, AiQuotaPromotionEventRequestDto request);
    void recordDismissal(String email, AiQuotaPromotionEventRequestDto request);
    void recordCta(String email, AiQuotaPromotionEventRequestDto request);
}
