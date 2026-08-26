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
        entity.setCalculationMode(dto.getCalculationMode());
        entity.setControlledStrategy(dto.getControlledStrategy());
        entity.setLockedMacros(dto.getLockedMacros());
        entity.setMacroCalculatedCalories(dto.getMacroCalculatedCalories());
        entity.setAutomaticReferenceCalories(dto.getAutomaticReferenceCalories());
        entity.setAutomaticReferenceProtein(dto.getAutomaticReferenceProtein());
        entity.setAutomaticReferenceCarbs(dto.getAutomaticReferenceCarbs());
        entity.setAutomaticReferenceFat(dto.getAutomaticReferenceFat());
        entity.setEffectiveFrom(dto.getEffectiveFrom());
        entity.setEffectiveLocalDate(dto.getEffectiveLocalDate());
        entity.setEffectiveTimeZone(dto.getEffectiveTimeZone());
        entity.setVersion(dto.getVersion());
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
        dto.setCalculationMode(entity.getCalculationMode());
        dto.setControlledStrategy(entity.getControlledStrategy());
        dto.setLockedMacros(entity.getLockedMacros());
        dto.setMacroCalculatedCalories(entity.getMacroCalculatedCalories());
        dto.setAutomaticReferenceCalories(entity.getAutomaticReferenceCalories());
        dto.setAutomaticReferenceProtein(entity.getAutomaticReferenceProtein());
        dto.setAutomaticReferenceCarbs(entity.getAutomaticReferenceCarbs());
        dto.setAutomaticReferenceFat(entity.getAutomaticReferenceFat());
        dto.setEffectiveFrom(entity.getEffectiveFrom());
        dto.setEffectiveLocalDate(entity.getEffectiveLocalDate());
        dto.setEffectiveTimeZone(entity.getEffectiveTimeZone());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
