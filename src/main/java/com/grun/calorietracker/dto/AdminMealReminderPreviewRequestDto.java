package com.grun.calorietracker.dto;
import com.grun.calorietracker.service.reminder.*;
import jakarta.validation.constraints.*;
import java.time.*; import java.util.Map;
public record AdminMealReminderPreviewRequestDto(@NotNull Instant evaluatedAt,@NotNull LocalDate localDate,
 @NotBlank @Size(max=64) String timeZone,@Pattern(regexp="tr|en") String language,
 @NotNull Map<MealReminderContract.Meal,MealReminderContract.MealState> mealStates,
 Integer targetCalories,Double consumedCalories,boolean fastingActive,boolean pushEnabled,boolean mealRemindersEnabled) {}
