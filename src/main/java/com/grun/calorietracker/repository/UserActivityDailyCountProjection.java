package com.grun.calorietracker.repository;

import java.time.LocalDate;

public interface UserActivityDailyCountProjection {
    LocalDate getActivityDate();

    long getActiveUsers();
}
