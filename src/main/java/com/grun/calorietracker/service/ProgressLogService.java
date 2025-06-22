package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;

import java.util.List;

public interface ProgressLogService {
    void saveLog(ProgressLogEntity log);
    List<ProgressLogEntity> getUserLogs(UserEntity user);
}
