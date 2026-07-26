package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BodyMeasurementDto;
import com.grun.calorietracker.dto.BodyMeasurementRequestDto;
import com.grun.calorietracker.dto.BodyMeasurementSummaryDto;

import java.time.LocalDateTime;
import java.util.List;

public interface BodyMeasurementService {
    BodyMeasurementDto create(BodyMeasurementRequestDto request, String email);
    BodyMeasurementDto update(Long id, BodyMeasurementRequestDto request, String email);
    List<BodyMeasurementDto> list(String email, LocalDateTime start, LocalDateTime end);
    BodyMeasurementSummaryDto summary(String email);
    void delete(Long id, String email);
    void syncWeightFromProgress(Long progressLogId, Double weightKg, LocalDateTime recordedAt, String email);
    void deleteWeightFromProgress(Long progressLogId, String email);
}
