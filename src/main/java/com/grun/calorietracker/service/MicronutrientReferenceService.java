package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.MicronutrientDataQualityDto;
import com.grun.calorietracker.dto.MicronutrientTotalsDto;

public interface MicronutrientReferenceService {

    MicronutrientTotalsDto resolveTargets(Integer age);

    MicronutrientTotalsDto calculateRemaining(MicronutrientTotalsDto consumed, MicronutrientTotalsDto target);

    MicronutrientDataQualityDto assessDataQuality(MicronutrientTotalsDto consumed, Integer age);
}