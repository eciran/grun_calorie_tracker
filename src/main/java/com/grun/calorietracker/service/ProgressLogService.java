package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;

import java.util.List;

public interface ProgressLogService {

    void saveLog(ProgressLogDto log, String email);
    List<ProgressLogDto> getUserLogs(String email);
}
