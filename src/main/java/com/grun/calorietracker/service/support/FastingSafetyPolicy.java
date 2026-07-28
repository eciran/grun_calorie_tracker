package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.FastingAdvancedEligibilityDto;
import com.grun.calorietracker.dto.FastingAdvancedEligibilityRequestDto;
import com.grun.calorietracker.dto.FastingPlanRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FastingPlanType;
import com.grun.calorietracker.enums.FastingSafetyErrorCode;
import com.grun.calorietracker.exception.FastingSafetyException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FastingSafetyPolicy {
    public static final String VERSION = "FASTING_SAFETY_V1";
    public static final int MAX_CONTINUOUS_FASTING_HOURS = 24;
    private static final int MINIMUM_ADVANCED_AGE = 18;
    private static final Map<FastingPlanType, Integer> PRESET_HOURS = Map.of(
            FastingPlanType.FASTING_16_8, 16,
            FastingPlanType.FASTING_18_6, 18,
            FastingPlanType.FASTING_20_4, 20,
            FastingPlanType.OMAD, 23
    );

    public void validateBasicPlan(FastingPlanRequestDto request) {
        int fastingHours = request.getFastingHours();
        int eatingWindowHours = request.getEatingWindowHours();
        if (fastingHours > MAX_CONTINUOUS_FASTING_HOURS) {
            throw new FastingSafetyException(FastingSafetyErrorCode.FASTING_UNSAFE_DURATION,
                    "Continuous fasting cannot exceed 24 hours.");
        }
        if (fastingHours + eatingWindowHours != 24) {
            throw new IllegalArgumentException("fastingHours and eatingWindowHours must total 24.");
        }
        Integer presetHours = PRESET_HOURS.get(request.getPlanType());
        if (presetHours != null && (fastingHours != presetHours || eatingWindowHours != 24 - presetHours)) {
            throw new IllegalArgumentException("Fasting duration must match the selected preset.");
        }
    }

    public FastingAdvancedEligibilityDto evaluateAdvanced(UserEntity user,
                                                            FastingAdvancedEligibilityRequestDto request) {
        if (!Boolean.TRUE.equals(request.getSafetyAcknowledged())
                || !VERSION.equals(request.getAcknowledgedPolicyVersion())) {
            throw new FastingSafetyException(FastingSafetyErrorCode.FASTING_SAFETY_ACKNOWLEDGEMENT_REQUIRED,
                    "The current fasting safety policy must be acknowledged.");
        }
        List<String> reasons = new ArrayList<>();
        if (resolvedAge(user) < MINIMUM_ADVANCED_AGE) reasons.add("UNDER_18");
        if (Boolean.TRUE.equals(request.getPregnantOrBreastfeeding())) reasons.add("PREGNANCY_OR_BREASTFEEDING");
        if (Boolean.TRUE.equals(request.getEatingDisorderRiskOrHistory())) reasons.add("EATING_DISORDER_RISK_OR_HISTORY");
        if (Boolean.TRUE.equals(request.getDiabetesOrGlucoseMedication())) reasons.add("DIABETES_OR_GLUCOSE_MEDICATION");
        if (Boolean.TRUE.equals(request.getOtherClinicianManagedCondition())) reasons.add("CLINICIAN_REVIEW_REQUIRED");
        return new FastingAdvancedEligibilityDto(reasons.isEmpty(), VERSION,
                MAX_CONTINUOUS_FASTING_HOURS, List.copyOf(reasons));
    }

    private int resolvedAge(UserEntity user) {
        if (user.getBirthDate() != null) return Period.between(user.getBirthDate(), LocalDate.now()).getYears();
        return user.getAge() == null ? -1 : user.getAge();
    }
}
