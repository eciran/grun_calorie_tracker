package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiCreditPreviewDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.impl.AiCreditPreviewServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCreditPreviewServiceImplTest {

    @Test
    void previewsFixedMealCreditWithoutConsumingQuota() {
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        SubscriptionFeatureAccessDto access = new SubscriptionFeatureAccessDto();
        access.setAiBaseRemainingThisPeriod(3);
        access.setAiAddonRemainingThisPeriod(2);
        access.setAiRemainingThisPeriod(5);
        when(subscriptions.resolveAiCreditCost("user@example.com", SubscriptionFeature.AI_MEAL_DRAFTS))
                .thenReturn(2);
        when(subscriptions.getFeatureAccess("user@example.com")).thenReturn(access);
        AiCreditPreviewService service = new AiCreditPreviewServiceImpl(subscriptions);

        AiCreditPreviewDto preview = service.preview("user@example.com", AiRequestType.VOICE_FOOD_LOG);

        assertEquals(2, preview.getCreditCost());
        assertEquals(5, preview.getAiRemainingThisPeriod());
        assertTrue(preview.isCanAfford());
        assertTrue(preview.isConfirmationRequired());
        verify(subscriptions).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_MEAL_DRAFTS);
        verify(subscriptions, org.mockito.Mockito.never()).consumeAiQuota("user@example.com", 2);
    }

    @Test
    void insightsAreReadOnlyAndCanReportInsufficientCredit() {
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        SubscriptionFeatureAccessDto access = new SubscriptionFeatureAccessDto();
        access.setAiRemainingThisPeriod(0);
        when(subscriptions.resolveAiCreditCost("user@example.com", SubscriptionFeature.AI_INSIGHTS))
                .thenReturn(1);
        when(subscriptions.getFeatureAccess("user@example.com")).thenReturn(access);
        AiCreditPreviewService service = new AiCreditPreviewServiceImpl(subscriptions);

        AiCreditPreviewDto preview = service.preview("user@example.com", AiRequestType.AI_DAILY_INSIGHT);

        assertFalse(preview.isCanAfford());
        assertFalse(preview.isConfirmationRequired());
    }

    @Test
    void dynamicPlansUseTheirRequestSpecificPreviewEndpoints() {
        AiCreditPreviewService service = new AiCreditPreviewServiceImpl(mock(SubscriptionService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.preview("user@example.com", AiRequestType.AI_WORKOUT_PLAN));
        assertThrows(IllegalArgumentException.class,
                () -> service.preview("user@example.com", AiRequestType.AI_NUTRITION_PLAN));
    }

    @Test
    void preparationGuideUsesFixedCostPreview() {
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        SubscriptionFeatureAccessDto access = new SubscriptionFeatureAccessDto();
        access.setAiRemainingThisPeriod(4);
        when(subscriptions.resolveAiCreditCost(
                "user@example.com", SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE)).thenReturn(2);
        when(subscriptions.getFeatureAccess("user@example.com")).thenReturn(access);
        AiCreditPreviewService service = new AiCreditPreviewServiceImpl(subscriptions);

        AiCreditPreviewDto preview = service.preview(
                "user@example.com", AiRequestType.AI_MEAL_PREPARATION_GUIDE);

        assertEquals(2, preview.getCreditCost());
        assertTrue(preview.isCanAfford());
        assertTrue(preview.isConfirmationRequired());
        verify(subscriptions).assertFeatureAccess(
                "user@example.com", SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE);
    }
}