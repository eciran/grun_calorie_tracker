package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FastingHistoryCorrectionRequestDto;
import com.grun.calorietracker.dto.FastingHistoryRecordDto;

public interface AdvancedFastingHistoryService {
    FastingHistoryRecordDto create(String email, FastingHistoryCorrectionRequestDto request);
    FastingHistoryRecordDto correct(String email, Long sessionId, FastingHistoryCorrectionRequestDto request);
    void archive(String email, Long sessionId, String correctionReason);
}