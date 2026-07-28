package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.FastingDayRuleType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.*;
@Data public class FastingDayRuleRequestDto {
 @NotNull private DayOfWeek dayOfWeek;
 @NotNull private FastingDayRuleType ruleType;
 @Min(60) @Max(1440) private Integer fastingMinutes;
 private LocalTime preferredStartTime;
 @Min(300) @Max(1200) private Integer reducedCalorieTarget;
}
