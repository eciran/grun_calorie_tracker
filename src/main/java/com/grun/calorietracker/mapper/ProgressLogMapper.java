package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class ProgressLogMapper {

    public ProgressLogEntity toEntity(ProgressLogDto dto, UserEntity user) {
        if (dto == null || user == null) {
            return null;
        }
        ProgressLogEntity entity = new ProgressLogEntity();
        entity.setId(dto.getId());
        entity.setUser(user);
        entity.setLogDate(dto.getLogDate());
        entity.setWeight(dto.getWeight());
        entity.setCalorieIntake(dto.getCalorieIntake());
        entity.setProteinIntake(dto.getProteinIntake());
        entity.setFatIntake(dto.getFatIntake());
        entity.setCarbIntake(dto.getCarbIntake());
        entity.setNote(dto.getNote());
        return entity;
    }

    public ProgressLogDto toDto(ProgressLogEntity entity) {
        if (entity == null) {
            return null;
        }
        ProgressLogDto dto = new ProgressLogDto();
        dto.setId(entity.getId());
        dto.setLogDate(entity.getLogDate());
        dto.setWeight(entity.getWeight());
        dto.setCalorieIntake(entity.getCalorieIntake());
        dto.setProteinIntake(entity.getProteinIntake());
        dto.setFatIntake(entity.getFatIntake());
        dto.setCarbIntake(entity.getCarbIntake());
        dto.setNote(entity.getNote());
        return dto;
    }
}
