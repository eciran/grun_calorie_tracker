package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.MicronutrientDataQualityDto;
import com.grun.calorietracker.dto.MicronutrientTotalsDto;
import com.grun.calorietracker.service.MicronutrientReferenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultMicronutrientReferenceService implements MicronutrientReferenceService {

    static final String PROFILE_CODE = "EU_ADULT_DIETARY_REFERENCE_V1";
    private static final int ADULT_AGE = 18;
    private static final List<String> REFERENCE_SOURCES = List.of(
            "EU_1169_2011_ANNEX_XIII",
            "EFSA_SODIUM_2019",
            "EFSA_POTASSIUM_2016"
    );

    @Override
    public MicronutrientTotalsDto resolveTargets(Integer age) {
        if (age == null || age < ADULT_AGE) {
            return null;
        }
        MicronutrientTotalsDto target = new MicronutrientTotalsDto();
        target.setFiber(25.0);
        target.setSaturatedFat(20.0);
        target.setSodium(2000.0);
        target.setPotassium(3500.0);
        target.setCalcium(800.0);
        target.setIron(14.0);
        target.setMagnesium(375.0);
        target.setZinc(10.0);
        target.setVitaminA(800.0);
        target.setVitaminC(80.0);
        target.setVitaminD(5.0);
        target.setVitaminE(12.0);
        target.setVitaminB12(2.5);
        return target;
    }

    @Override
    public MicronutrientTotalsDto calculateRemaining(MicronutrientTotalsDto consumed, MicronutrientTotalsDto target) {
        if (consumed == null || target == null) {
            return null;
        }
        MicronutrientTotalsDto remaining = new MicronutrientTotalsDto();
        remaining.setFiber(subtractWhenKnown(target.getFiber(), consumed.getFiber()));
        remaining.setSugar(subtractWhenKnown(target.getSugar(), consumed.getSugar()));
        remaining.setSaturatedFat(subtractWhenKnown(target.getSaturatedFat(), consumed.getSaturatedFat()));
        remaining.setSodium(subtractWhenKnown(target.getSodium(), consumed.getSodium()));
        remaining.setPotassium(subtractWhenKnown(target.getPotassium(), consumed.getPotassium()));
        remaining.setCholesterol(subtractWhenKnown(target.getCholesterol(), consumed.getCholesterol()));
        remaining.setCalcium(subtractWhenKnown(target.getCalcium(), consumed.getCalcium()));
        remaining.setIron(subtractWhenKnown(target.getIron(), consumed.getIron()));
        remaining.setMagnesium(subtractWhenKnown(target.getMagnesium(), consumed.getMagnesium()));
        remaining.setZinc(subtractWhenKnown(target.getZinc(), consumed.getZinc()));
        remaining.setVitaminA(subtractWhenKnown(target.getVitaminA(), consumed.getVitaminA()));
        remaining.setVitaminC(subtractWhenKnown(target.getVitaminC(), consumed.getVitaminC()));
        remaining.setVitaminD(subtractWhenKnown(target.getVitaminD(), consumed.getVitaminD()));
        remaining.setVitaminE(subtractWhenKnown(target.getVitaminE(), consumed.getVitaminE()));
        remaining.setVitaminB12(subtractWhenKnown(target.getVitaminB12(), consumed.getVitaminB12()));
        return remaining;
    }

    @Override
    public MicronutrientDataQualityDto assessDataQuality(MicronutrientTotalsDto consumed, Integer age) {
        Map<String, Double> coreValues = coreValues(consumed);
        List<String> missing = new ArrayList<>();
        int available = 0;
        for (Map.Entry<String, Double> entry : coreValues.entrySet()) {
            if (entry.getValue() == null) {
                missing.add(entry.getKey());
            } else {
                available++;
            }
        }

        MicronutrientDataQualityDto quality = new MicronutrientDataQualityDto();
        quality.setAvailableNutrientCount(available);
        quality.setTrackedNutrientCount(coreValues.size());
        quality.setCoveragePercent(roundPercent(available, coreValues.size()));
        quality.setCoverageLevel(available == 0 ? "NONE" : available == coreValues.size() ? "COMPLETE" : "PARTIAL");
        quality.setMissingNutrients(List.copyOf(missing));
        quality.setTargetProfileCode(PROFILE_CODE);
        quality.setTargetProfileApplicable(age != null && age >= ADULT_AGE);
        quality.setTargetProfileUnavailableReason(resolveUnavailableReason(age));
        quality.setReferenceSources(REFERENCE_SOURCES);
        return quality;
    }

    private Map<String, Double> coreValues(MicronutrientTotalsDto consumed) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("SODIUM", consumed == null ? null : consumed.getSodium());
        values.put("POTASSIUM", consumed == null ? null : consumed.getPotassium());
        values.put("CALCIUM", consumed == null ? null : consumed.getCalcium());
        values.put("IRON", consumed == null ? null : consumed.getIron());
        values.put("MAGNESIUM", consumed == null ? null : consumed.getMagnesium());
        values.put("ZINC", consumed == null ? null : consumed.getZinc());
        values.put("VITAMIN_A", consumed == null ? null : consumed.getVitaminA());
        values.put("VITAMIN_C", consumed == null ? null : consumed.getVitaminC());
        values.put("VITAMIN_D", consumed == null ? null : consumed.getVitaminD());
        values.put("VITAMIN_E", consumed == null ? null : consumed.getVitaminE());
        values.put("VITAMIN_B12", consumed == null ? null : consumed.getVitaminB12());
        return values;
    }

    private String resolveUnavailableReason(Integer age) {
        if (age == null) {
            return "AGE_REQUIRED";
        }
        return age < ADULT_AGE ? "ADULT_PROFILE_NOT_APPLICABLE" : null;
    }

    private Double subtractWhenKnown(Double target, Double consumed) {
        if (target == null || consumed == null) {
            return null;
        }
        return Math.round((target - consumed) * 100.0) / 100.0;
    }

    private double roundPercent(int available, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((available * 10000.0) / total) / 100.0;
    }
}