package com.grun.calorietracker.dto;
import com.grun.calorietracker.service.reminder.MealReminderContract;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.Set;
public record AdminMealReminderPolicyRequestDto(
 Long version, @NotNull MealReminderContract.Mode mode, boolean kcalEnabled,
 @NotNull LocalTime breakfastTime,@NotNull LocalTime lunchTime,@NotNull LocalTime dinnerTime,
 @Min(1) @Max(60) int slotAgeMinutes,@Min(0) @Max(3) int maxDaily,
 @Min(0) @Max(3) int maxRolling,@Min(0) @Max(1) int maxCatchups,
 @Min(180) int minimumGapMinutes,@Min(30) int routineGapMinutes,
 @NotNull LocalTime quietStart,@NotNull LocalTime quietEnd,
 @NotNull @Size(max=100) Set<@Positive Long> pilotUserIds) {}
