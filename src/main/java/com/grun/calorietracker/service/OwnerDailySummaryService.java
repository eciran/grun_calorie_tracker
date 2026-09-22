package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OwnerDailySummaryDto;
import java.time.LocalDate;

public interface OwnerDailySummaryService {
    OwnerDailySummaryDto get(LocalDate date);
    OwnerDailySummaryDto enqueue(LocalDate date);
    LocalDate today();
}
