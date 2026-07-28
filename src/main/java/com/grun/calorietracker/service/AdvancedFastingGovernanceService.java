package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdvancedFastingGovernanceDto;
import com.grun.calorietracker.dto.AdvancedFastingOperationsConfigRequestDto;
import com.grun.calorietracker.entity.AdvancedFastingOperationsConfigEntity;

public interface AdvancedFastingGovernanceService {
    AdvancedFastingGovernanceDto getGovernance();
    AdvancedFastingGovernanceDto updateOperations(AdvancedFastingOperationsConfigRequestDto request);
    AdvancedFastingOperationsConfigEntity currentOperations();
}