package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodDiaryNoteDto;
import com.grun.calorietracker.dto.FoodDiaryNoteRequestDto;

import java.time.LocalDate;

public interface FoodDiaryNoteService {
    FoodDiaryNoteDto upsertNote(String email, LocalDate diaryDate, FoodDiaryNoteRequestDto request);
    FoodDiaryNoteDto getNote(String email, LocalDate diaryDate);
    void deleteNote(String email, LocalDate diaryDate);
}
