package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductUploadFinalizeDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionRequestDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionStateDto;

public interface FoodProductUploadSessionService {
    FoodProductUploadSessionDto create(String userEmail, FoodProductUploadSessionRequestDto request);

    FoodProductUploadSessionStateDto get(String userEmail, String sessionId);

    FoodProductUploadFinalizeDto finalizeUpload(String userEmail, String sessionId);
}
