package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;

public interface ProductionVerificationService {
    ProductionVerificationRunDto record(ProductionVerificationRunRequestDto request, String adminEmail, String correlationId);
    ProductionVerificationRunPageDto list(String provider, int page, int size);
}
