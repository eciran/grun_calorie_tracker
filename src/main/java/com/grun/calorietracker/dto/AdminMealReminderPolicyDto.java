package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.MealReminderPolicyStatus;
import com.grun.calorietracker.service.reminder.MealReminderContract;
import java.time.*; import java.util.Set;
public record AdminMealReminderPolicyDto(Long id,Long version,String policyVersion,MealReminderPolicyStatus status,
 MealReminderContract.Mode mode,boolean deploymentGateEnabled,boolean pushGateEnabled,boolean kcalEnabled,
 LocalTime breakfastTime,LocalTime lunchTime,LocalTime dinnerTime,int slotAgeMinutes,int maxDaily,int maxRolling,
 int maxCatchups,int minimumGapMinutes,int routineGapMinutes,LocalTime quietStart,LocalTime quietEnd,
 Set<Long> pilotUserIds,boolean emergencyStopped,String stopReason,String updatedBy,Instant updatedAt,Instant publishedAt) {}
