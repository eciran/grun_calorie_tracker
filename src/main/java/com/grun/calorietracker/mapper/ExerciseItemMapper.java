package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class ExerciseItemMapper {

    public ExerciseItemEntity toEntity(ExerciseItemDto dto) {
        if (dto == null) return null;
        ExerciseItemEntity entity = new ExerciseItemEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setMetCode(dto.getMetCode());
        entity.setCaloriesPerMinute(dto.getCaloriesPerMinute());
        entity.setDescription(dto.getDescription());
        entity.setIconUrl(dto.getIconUrl());
        entity.setPrimaryMuscleGroup(dto.getPrimaryMuscleGroup());
        entity.setSecondaryMuscleGroups(dto.getSecondaryMuscleGroups());
        entity.setEquipment(dto.getEquipment());
        entity.setDifficulty(dto.getDifficulty());
        entity.setInstructions(dto.getInstructions());
        entity.setSafetyNotes(dto.getSafetyNotes());
        entity.setThumbnailUrl(dto.getThumbnailUrl());
        entity.setVideoUrl(dto.getVideoUrl());
        entity.setAnimationUrl(dto.getAnimationUrl());
        entity.setDefaultMeasurementType(dto.getDefaultMeasurementType());
        entity.setAllowedMeasurementTypes(toAllowedMeasurementTypesCsv(dto.getAllowedMeasurementTypes()));
        entity.setAiEligible(dto.getAiEligible());
        entity.setActive(dto.getActive());
        return entity;
    }

    public ExerciseItemDto toDto(ExerciseItemEntity entity) {
        if (entity == null) return null;
        ExerciseItemDto dto = new ExerciseItemDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setMetCode(entity.getMetCode());
        dto.setCaloriesPerMinute(entity.getCaloriesPerMinute());
        dto.setDescription(entity.getDescription());
        dto.setIconUrl(entity.getIconUrl());
        dto.setPrimaryMuscleGroup(entity.getPrimaryMuscleGroup());
        dto.setSecondaryMuscleGroups(entity.getSecondaryMuscleGroups());
        dto.setEquipment(entity.getEquipment());
        dto.setDifficulty(entity.getDifficulty());
        dto.setInstructions(entity.getInstructions());
        dto.setSafetyNotes(entity.getSafetyNotes());
        dto.setThumbnailUrl(entity.getThumbnailUrl());
        dto.setVideoUrl(entity.getVideoUrl());
        dto.setAnimationUrl(entity.getAnimationUrl());
        dto.setDefaultMeasurementType(entity.getDefaultMeasurementType());
        dto.setAllowedMeasurementTypes(parseAllowedMeasurementTypes(entity.getAllowedMeasurementTypes()));
        dto.setAiEligible(entity.getAiEligible());
        dto.setActive(entity.getActive());
        return dto;
    }

    public static String toAllowedMeasurementTypesCsv(List<ExerciseLogMeasurementType> measurementTypes) {
        if (measurementTypes == null || measurementTypes.isEmpty()) {
            return null;
        }
        return measurementTypes.stream()
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public static List<ExerciseLogMeasurementType> parseAllowedMeasurementTypes(String measurementTypes) {
        if (measurementTypes == null || measurementTypes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(measurementTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> ExerciseLogMeasurementType.valueOf(value.toUpperCase(Locale.ROOT)))
                .toList();
    }
}