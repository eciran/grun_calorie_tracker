package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminGdprRequestDto;
import com.grun.calorietracker.dto.AdminGdprRequestPageDto;
import com.grun.calorietracker.dto.AdminGdprRequestUpdateDto;
import com.grun.calorietracker.enums.GdprRequestStatus;
import com.grun.calorietracker.enums.GdprRequestType;

public interface GdprRequestTrackingService {
    Long begin(String userEmail, GdprRequestType type);
    void complete(Long id, String resultCode);
    void fail(Long id, RuntimeException exception);
    AdminGdprRequestPageDto list(GdprRequestStatus status, int page, int size);
    AdminGdprRequestDto update(Long id, AdminGdprRequestUpdateDto request, String adminEmail, String correlationId);
}
