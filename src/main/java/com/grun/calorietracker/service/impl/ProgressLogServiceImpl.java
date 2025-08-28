package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.ProgressLogMapper;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.service.ProgressLogService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressLogServiceImpl implements ProgressLogService {

    private final ProgressLogRepository progressLogRepository;
    private final UserService userService;
    private final ProgressLogMapper progressLogMapper;

    @Override
    public void saveLog(ProgressLogDto log, String email) {
        UserEntity user= getUserByEmail(email);
        ProgressLogEntity entity= progressLogMapper.toEntity(log,user);
        progressLogRepository.save(entity);
    }

    @Override
    public List<ProgressLogDto> getUserLogs(String email) {
        UserEntity user= getUserByEmail(email);
        return progressLogRepository.findByUserOrderByLogDateAsc(user);
    }

    private UserEntity getUserByEmail(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

}
