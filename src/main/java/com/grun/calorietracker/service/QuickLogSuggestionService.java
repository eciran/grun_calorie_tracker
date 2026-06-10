package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.QuickLogSuggestionDto;

import java.time.LocalDate;
import java.time.LocalTime;

public interface QuickLogSuggestionService {
    QuickLogSuggestionDto getSuggestions(String email, LocalDate targetDate, LocalTime localTime, int limit);
}
