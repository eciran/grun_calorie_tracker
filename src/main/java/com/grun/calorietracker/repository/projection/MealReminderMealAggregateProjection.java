package com.grun.calorietracker.repository.projection;

public interface MealReminderMealAggregateProjection {
    Long getUserId();

    String getMealType();

    Long getRecordCount();

    Double getCalories();
}
