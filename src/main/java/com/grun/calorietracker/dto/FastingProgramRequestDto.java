package com.grun.calorietracker.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.*;
@Data public class FastingProgramRequestDto {
 @NotBlank @Size(max=120) private String name;
 private LocalDate effectiveFrom;
 private LocalDate effectiveUntil;
 @NotNull @Size(min=7,max=7) @Valid private List<FastingDayRuleRequestDto> rules;
}
