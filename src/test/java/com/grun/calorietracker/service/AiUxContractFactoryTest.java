package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiUxContractDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.enums.AiClientLifecycleStatus;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.service.support.AiUxContractFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUxContractFactoryTest {

    @Test
    void draftSuccess_mapsInternalStatusAndCreditMetadataToStableClientContract() {
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiBaseRemainingThisPeriod(8);
        quota.setAiAddonRemainingThisPeriod(3);
        quota.setAiRemainingThisPeriod(11);

        AiUxContractDto result = AiUxContractFactory.success(
                AiRequestStatus.DRAFT_CREATED, true, 4, quota, PreferredLanguage.TR);

        assertEquals(AiClientLifecycleStatus.COMPLETED, result.getLifecycleStatus());
        assertTrue(result.getReviewRequired());
        assertTrue(result.getConfirmationRequired());
        assertFalse(result.getRetryable());
        assertEquals(4, result.getCreditCost());
        assertTrue(result.getCreditCharged());
        assertEquals(8, result.getAiBaseRemainingThisPeriod());
        assertEquals(3, result.getAiAddonRemainingThisPeriod());
        assertEquals(11, result.getAiRemainingThisPeriod());
        assertEquals(PreferredLanguage.TR, result.getOutputLanguage());
    }

    @Test
    void insightSuccess_isCompletedWithoutConfirmation() {
        AiUxContractDto result = AiUxContractFactory.success(
                AiRequestStatus.CONFIRMED, false, 1, null, PreferredLanguage.EN);

        assertEquals(AiClientLifecycleStatus.COMPLETED, result.getLifecycleStatus());
        assertFalse(result.getReviewRequired());
        assertFalse(result.getConfirmationRequired());
    }

    @Test
    void refundedFailure_isRetryableAndNotCharged() {
        AiUxContractDto result = AiUxContractFactory.failure(true, 2, false, PreferredLanguage.EN);

        assertEquals(AiClientLifecycleStatus.FAILED, result.getLifecycleStatus());
        assertTrue(result.getRetryable());
        assertFalse(result.getCreditCharged());
        assertEquals(2, result.getCreditCost());
    }
}