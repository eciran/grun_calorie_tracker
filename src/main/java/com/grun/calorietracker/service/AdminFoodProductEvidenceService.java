package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminFoodProductEvidenceReadDto;

public interface AdminFoodProductEvidenceService {
    AdminFoodProductEvidenceReadDto authorizeRead(String adminEmail, Long assetId);
}
