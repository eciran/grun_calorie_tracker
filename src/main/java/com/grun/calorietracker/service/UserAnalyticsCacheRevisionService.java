package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.service.support.UserAnalyticsCacheIdentity;

public interface UserAnalyticsCacheRevisionService {

    UserAnalyticsCacheIdentity requireIdentity(String email);

    void bump(Long userId, AnalyticsMutationSource source);

    void bumpForEmail(String email, AnalyticsMutationSource source);
}

