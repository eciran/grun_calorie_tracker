package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.*;
import java.time.*;
public record FastingOccurrenceDto(Long id,Long programId,Integer programVersion,Long dayRuleId,Long sessionId,LocalDate occurrenceDate,FastingDayRuleType ruleType,FastingOccurrenceStatus status,FastingAdherenceStatus adherenceStatus,LocalDateTime plannedStartAt,LocalDateTime plannedEndAt,Integer plannedFastingMinutes,Integer plannedCalorieTarget,Double actualCalories,LocalDateTime evaluatedAt,FastingSkipReason skipReason,String reasonNote) {}
