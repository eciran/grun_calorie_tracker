package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import org.springframework.stereotype.Component;

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
        dto.setAiEligible(entity.getAiEligible());
        dto.setActive(entity.getActive());
        return dto;
    }
}
