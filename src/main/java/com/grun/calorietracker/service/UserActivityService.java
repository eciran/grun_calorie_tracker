package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.UserActivitySource;

public interface UserActivityService {

    void recordLogin(String email, UserActivitySource source);

    void recordActivity(String email, UserActivitySource source);
}
