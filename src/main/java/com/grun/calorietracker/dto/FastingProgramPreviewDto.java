package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.FastingDayRuleType;
import com.grun.calorietracker.enums.FastingScheduleExceptionType;
import java.time.*;
import java.util.List;
public record FastingProgramPreviewDto(Long programId,Integer versionNumber,String timeZone,LocalDate startDate,
 List<PreviewDay> days) {
 public record PreviewDay(LocalDate date,FastingDayRuleType ruleType,ZonedDateTime startAt,ZonedDateTime targetEndAt,
  Integer reducedCalorieTarget, FastingScheduleExceptionType exceptionType, LocalDate exceptionSourceDate) {}
}
