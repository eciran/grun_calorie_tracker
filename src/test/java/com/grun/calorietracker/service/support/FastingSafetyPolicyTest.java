package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FastingAdvancedEligibilityRequestDto;
import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingPlanType;
import com.grun.calorietracker.enums.FastingSafetyErrorCode;
import com.grun.calorietracker.exception.FastingSafetyException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FastingSafetyPolicyTest {
    private final FastingSafetyPolicy policy = new FastingSafetyPolicy();

    @Test
    void rejectsContinuousFastAbovePolicyMaximum() {
        FastingSafetyException exception = assertThrows(FastingSafetyException.class,
                () -> policy.validateBasicPlan(plan(FastingPlanType.CUSTOM, 25, 1)));
        assertEquals(FastingSafetyErrorCode.FASTING_UNSAFE_DURATION, exception.getCode());
    }

    @Test
    void rejectsPresetMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateBasicPlan(plan(FastingPlanType.FASTING_16_8, 18, 6)));
    }

    @Test
    void requiresCurrentPolicyAcknowledgement() {
        var request = eligibility();
        request.setSafetyAcknowledged(false);
        FastingSafetyException exception = assertThrows(FastingSafetyException.class,
                () -> policy.evaluateAdvanced(adult(), request));
        assertEquals(FastingSafetyErrorCode.FASTING_SAFETY_ACKNOWLEDGEMENT_REQUIRED, exception.getCode());
    }

    @Test
    void blocksDeclaredRisk() {
        var request = eligibility();
        request.setDiabetesOrGlucoseMedication(true);
        var result = policy.evaluateAdvanced(adult(), request);
        assertFalse(result.eligible());
        assertTrue(result.blockingReasons().contains("DIABETES_OR_GLUCOSE_MEDICATION"));
    }

    @Test
    void allowsAdultWithoutDeclaredRisk() {
        var result = policy.evaluateAdvanced(adult(), eligibility());
        assertTrue(result.eligible());
        assertEquals(FastingSafetyPolicy.VERSION, result.policyVersion());
        assertEquals(24, result.maximumContinuousFastingHours());
    }

    private FastingPlanRequestDto plan(FastingPlanType type, int fastingHours, int eatingHours) {
        var request = new FastingPlanRequestDto();
        request.setPlanType(type);
        request.setFastingHours(fastingHours);
        request.setEatingWindowHours(eatingHours);
        return request;
    }

    private FastingAdvancedEligibilityRequestDto eligibility() {
        var request = new FastingAdvancedEligibilityRequestDto();
        request.setPregnantOrBreastfeeding(false);
        request.setEatingDisorderRiskOrHistory(false);
        request.setDiabetesOrGlucoseMedication(false);
        request.setOtherClinicianManagedCondition(false);
        request.setSafetyAcknowledged(true);
        request.setAcknowledgedPolicyVersion(FastingSafetyPolicy.VERSION);
        return request;
    }

    private UserEntity adult() {
        var user = new UserEntity();
        user.setBirthDate(LocalDate.now().minusYears(30));
        return user;
    }
}
