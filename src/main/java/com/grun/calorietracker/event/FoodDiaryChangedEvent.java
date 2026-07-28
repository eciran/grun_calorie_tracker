package com.grun.calorietracker.event;

import java.time.LocalDate;

public record FoodDiaryChangedEvent(String email, LocalDate date) {}