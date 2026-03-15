package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.List;

// Service interface for exercise log operations
public interface ExerciseLogsService {
    ExerciseLogsDto addExerciseLog(ExerciseLogsDto dto, String email);
    List<ExerciseLogsDto> getExerciseLogsByDateAndUser(String email, LocalDateTime startDate, LocalDateTime endDate, String range);
    ExerciseLogsDto getExerciseLogById(Long id, String email);
    void deleteExerciseLog(Long id, String email);

    ExerciseLogsDto addExerciseLogFromExternal(ExerciseLogsDto dto, String email);
    List<ExerciseLogsDto> getExerciseLogsBySource(String email, String source);

}
