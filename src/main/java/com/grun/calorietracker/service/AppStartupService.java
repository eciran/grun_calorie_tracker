package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AppStartupDto;

public interface AppStartupService {

    AppStartupDto getStartupState(String email);
}
