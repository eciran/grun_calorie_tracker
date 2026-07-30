package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductUploadFinalizeDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionRequestDto;

public interface FoodProductUploadSessionService {
    FoodProductUploadSessionDto create(String userEmail, FoodProductUploadSessionRequestDto request);

    FoodProductUploadFinalizeDto finalizeUpload(String userEmail, String sessionId);
}
