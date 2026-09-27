package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminFoodProductEvidenceReadDto;

public interface AdminFoodProductEvidenceService {
    AdminFoodProductEvidenceReadDto authorizeRead(String adminEmail, Long assetId);

    EvidenceContent loadEvidence(String adminEmail, Long assetId);

    record EvidenceContent(byte[] bytes, String contentType) {
    }
}
