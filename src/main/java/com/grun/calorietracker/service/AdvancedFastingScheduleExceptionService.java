package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import java.time.LocalDate;

public interface AdvancedFastingScheduleExceptionService {
    FastingScheduleExceptionDto put(String email, LocalDate date, FastingScheduleExceptionRequestDto request);
    void delete(String email, LocalDate date);
}