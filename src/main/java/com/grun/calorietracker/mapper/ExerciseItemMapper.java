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
        return entity;
    }

    public  ExerciseItemDto toDto(ExerciseItemEntity entity) {
        if (entity == null) return null;
        ExerciseItemDto dto = new ExerciseItemDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setMetCode(entity.getMetCode());
        dto.setCaloriesPerMinute(entity.getCaloriesPerMinute());
        dto.setDescription(entity.getDescription());
        dto.setIconUrl(entity.getIconUrl());
        return dto;
    }
}
