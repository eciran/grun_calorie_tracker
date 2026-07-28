package com.grun.calorietracker.service;
import com.grun.calorietracker.dto.*;
import java.time.LocalDate;
public interface AdvancedFastingExecutionService {
 FastingOccurrenceDto getOrCreate(String email,LocalDate date);
 FastingOccurrenceDto recalculate(String email,LocalDate date);
 FastingOccurrenceDto skip(String email,LocalDate date,FastingOccurrenceSkipRequestDto request);
 FastingSessionDto start(String email,LocalDate date,FastingSessionStartRequestDto request);
}
