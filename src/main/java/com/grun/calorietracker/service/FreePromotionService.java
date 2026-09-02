package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;

public interface FreePromotionService {
    AdminFreePromotionPolicyDto getPolicy();
    AdminFreePromotionPolicyDto updatePolicy(AdminFreePromotionPolicyRequestDto request, String admin, String correlationId);
    FreePromotionDecisionDto decide(String email, FreePromotionDecisionRequestDto request);
    void recordImpression(String email, FreePromotionEventRequestDto request);
    void recordDismissal(String email, FreePromotionEventRequestDto request);
    void recordCta(String email, FreePromotionEventRequestDto request);
}
