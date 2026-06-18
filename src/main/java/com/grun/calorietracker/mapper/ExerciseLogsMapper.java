package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import org.springframework.stereotype.Component;

@Component
public class ExerciseLogsMapper {

    public ExerciseLogsDto toDto(ExerciseLogsEntity entity) {
        ExerciseLogsDto dto = new ExerciseLogsDto();
        dto.setId(entity.getId());
        if (entity.getExerciseItem() != null) {
            dto.setExerciseItemId(entity.getExerciseItem().getId());
            dto.setExerciseItemName(entity.getExerciseItem().getName());
        }
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setMeasurementType(entity.getMeasurementType());
        dto.setSetCount(entity.getSetCount());
        dto.setReps(entity.getReps());
        dto.setWeightKg(entity.getWeightKg());
        dto.setDistanceKm(entity.getDistanceKm());
        dto.setCaloriesBurned(entity.getCaloriesBurned());
        dto.setLogDate(entity.getLogDate());
        dto.setSource(entity.getSource());
        dto.setExternalId(entity.getExternalId());
        dto.setExtraData(entity.getExtraData());
        return dto;
    }
}
