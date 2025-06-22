package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.service.ProgressLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProgressLogServiceImpl implements ProgressLogService {

    private final ProgressLogRepository progressLogRepository;

    public ProgressLogServiceImpl(ProgressLogRepository progressLogRepository) {
        this.progressLogRepository = progressLogRepository;
    }

    @Override
    public void saveLog(ProgressLogEntity log) {
        progressLogRepository.save(log);
    }

    @Override
    public List<ProgressLogEntity> getUserLogs(UserEntity user) {
        return progressLogRepository.findByUserOrderByLogDateAsc(user);
    }
}
