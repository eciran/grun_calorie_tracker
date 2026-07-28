package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.FastingSkipReason;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class FastingOccurrenceSkipRequestDto { @NotNull private FastingSkipReason reason; @Size(max=500,message="{validation.fasting.note.size}") private String note; }
