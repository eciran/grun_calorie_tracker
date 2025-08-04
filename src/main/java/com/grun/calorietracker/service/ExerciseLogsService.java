package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.UserEntity;

import java.util.List;

// Service interface for exercise log operations
public interface ExerciseLogsService {
    ExerciseLogsDto addExerciseLog(ExerciseLogsDto dto, UserEntity user);
    List<ExerciseLogsDto> getExerciseLogs(UserEntity user, String date);
    ExerciseLogsDto getExerciseLogById(Long id, UserEntity user);
    void deleteExerciseLog(Long id, UserEntity user);

    ExerciseLogsDto addExerciseLogFromExternal(ExerciseLogsDto dto, UserEntity user);
    List<ExerciseLogsDto> getExerciseLogsBySource(UserEntity user, String source);
}
