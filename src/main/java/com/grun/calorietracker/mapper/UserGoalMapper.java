package com.grun.calorietracker.mapper;

import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import org.springframework.stereotype.Component;

@Component
public class UserGoalMapper {

    public static UserGoalEntity toEntity(UserGoalDto dto, UserEntity user) {
        if (dto == null || user == null) {
            return null;
        }
        UserGoalEntity entity = new UserGoalEntity();
        entity.setId(dto.getId());
        entity.setUser(user);
        entity.setTargetWeight(dto.getTargetWeight());
        entity.setDailyCalorieGoal(dto.getDailyCalorieGoal());
        entity.setDailyProteinGoal(dto.getDailyProteinGoal());
        entity.setDailyFatGoal(dto.getDailyFatGoal());
        entity.setDailyCarbGoal(dto.getDailyCarbGoal());
        entity.setWeeklyWeightChangeTargetKg(dto.getWeeklyWeightChangeTargetKg());
        entity.setGoalType(dto.getGoalType());
        entity.setActivityLevel(dto.getActivityLevel());
        entity.setCreatedAt(dto.getCreatedAt());
        return entity;
    }

    public static UserGoalDto toDto(UserGoalEntity entity) {
        if (entity == null) {
            return null;
        }
        UserGoalDto dto = new UserGoalDto();
        dto.setId(entity.getId());
        dto.setTargetWeight(entity.getTargetWeight());
        dto.setDailyCalorieGoal(entity.getDailyCalorieGoal());
        dto.setDailyProteinGoal(entity.getDailyProteinGoal());
        dto.setDailyFatGoal(entity.getDailyFatGoal());
        dto.setDailyCarbGoal(entity.getDailyCarbGoal());
        dto.setWeeklyWeightChangeTargetKg(entity.getWeeklyWeightChangeTargetKg());
        dto.setGoalType(entity.getGoalType());
        dto.setActivityLevel(entity.getActivityLevel());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
