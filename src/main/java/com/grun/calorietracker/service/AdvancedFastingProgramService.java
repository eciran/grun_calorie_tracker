package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;

import java.time.LocalDate;
import java.util.List;

public interface AdvancedFastingProgramService {
    FastingProgramCreateResult create(String email, String idempotencyKey, FastingProgramRequestDto request);
    List<FastingProgramDto> list(String email, List<String> statuses, boolean includeArchived);
    FastingProgramDto get(String email, Long id);
    FastingProgramDto update(String email, Long id, FastingProgramRequestDto request);
    FastingProgramPreviewDto preview(String email, Long id, LocalDate startDate);
    FastingProgramDto activate(String email, Long id);
    FastingProgramDto pause(String email, Long id);
    FastingProgramDto archive(String email, Long id);
}