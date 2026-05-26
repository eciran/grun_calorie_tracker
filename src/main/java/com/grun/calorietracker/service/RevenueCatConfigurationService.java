package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatConfigStatusDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationRequestDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationResponseDto;

public interface RevenueCatConfigurationService {
    RevenueCatConfigStatusDto getConfigStatus();

    RevenueCatMappingValidationResponseDto validateMapping(RevenueCatMappingValidationRequestDto request);
}
