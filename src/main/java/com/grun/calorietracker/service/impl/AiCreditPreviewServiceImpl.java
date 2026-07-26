package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AiCreditPreviewDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.AiCreditPreviewService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCreditPreviewServiceImpl implements AiCreditPreviewService {

    private final SubscriptionService subscriptionService;

    @Override
    public AiCreditPreviewDto preview(String email, AiRequestType requestType) {
        SubscriptionFeature feature = featureFor(requestType);
        subscriptionService.assertFeatureAccess(email, feature);
        int creditCost = subscriptionService.resolveAiCreditCost(email, feature);
        SubscriptionFeatureAccessDto access = subscriptionService.getFeatureAccess(email);

        AiCreditPreviewDto preview = new AiCreditPreviewDto();
        preview.setRequestType(requestType);
        preview.setFeature(feature);
        preview.setCreditCost(creditCost);
        preview.setAiBaseRemainingThisPeriod(access.getAiBaseRemainingThisPeriod());
        preview.setAiAddonRemainingThisPeriod(access.getAiAddonRemainingThisPeriod());
        preview.setAiRemainingThisPeriod(access.getAiRemainingThisPeriod());
        preview.setCanAfford(access.getAiRemainingThisPeriod() != null
                && access.getAiRemainingThisPeriod() >= creditCost);
        preview.setConfirmationRequired(requestType != AiRequestType.AI_DAILY_INSIGHT
                && requestType != AiRequestType.AI_WEEKLY_INSIGHT);
        return preview;
    }

    private SubscriptionFeature featureFor(AiRequestType requestType) {
        if (requestType == null) {
            throw new IllegalArgumentException("AI request type is required.");
        }
        return switch (requestType) {
            case VOICE_FOOD_LOG, PHOTO_MEAL_LOG -> SubscriptionFeature.AI_MEAL_DRAFTS;
            case AI_RECIPE_GENERATION -> SubscriptionFeature.AI_RECIPE_GENERATION;
            case AI_MEAL_PREPARATION_GUIDE -> SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE;
            case AI_DAILY_INSIGHT, AI_WEEKLY_INSIGHT -> SubscriptionFeature.AI_INSIGHTS;
            case AI_NUTRITION_PLAN -> throw new IllegalArgumentException(
                    "Use the nutrition-plan credit-cost endpoint for a request-specific preview.");
            case AI_WORKOUT_PLAN -> throw new IllegalArgumentException(
                    "Use the workout-plan credit-cost endpoint for a request-specific preview.");
            default -> throw new IllegalArgumentException(
                    "Credit preview is not available for this AI request type.");
        };
    }
}