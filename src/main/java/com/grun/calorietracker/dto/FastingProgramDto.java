package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.*;
import java.time.*;
import java.util.List;
public record FastingProgramDto(Long id,String name,FastingProgramStatus status,LocalDate effectiveFrom,
 LocalDate effectiveUntil,Integer versionNumber,Long lockVersion,String safetyPolicyVersion,List<FastingDayRuleDto> rules) {
 public record FastingDayRuleDto(Long id,DayOfWeek dayOfWeek,FastingDayRuleType ruleType,Integer fastingMinutes,
  LocalTime preferredStartTime,Integer reducedCalorieTarget) {}
}
