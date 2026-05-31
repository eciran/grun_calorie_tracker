package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RetentionPolicyDto;
import com.grun.calorietracker.dto.RetentionPolicyUpdateRequestDto;
import com.grun.calorietracker.enums.RetentionPolicyKey;

import java.util.List;

public interface RetentionPolicyService {
    List<RetentionPolicyDto> listPolicies();

    RetentionPolicyDto upsertPolicy(String adminEmail, RetentionPolicyKey key, RetentionPolicyUpdateRequestDto request);
}
