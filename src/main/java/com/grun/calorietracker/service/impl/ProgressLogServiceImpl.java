package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProgressLogNotFoundException;
import com.grun.calorietracker.mapper.ProgressLogMapper;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.service.ProgressLogService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressLogServiceImpl implements ProgressLogService {

    private final ProgressLogRepository progressLogRepository;
    private final UserService userService;
    private final ProgressLogMapper progressLogMapper;

    @Override
    public ProgressLogDto saveLog(ProgressLogDto log, String email) {
        UserEntity user = getUserByEmail(email);
        ProgressLogEntity entity = progressLogMapper.toEntity(log, user);
        return progressLogMapper.toDto(progressLogRepository.save(entity));
    }

    @Override
    public ProgressLogDto updateLog(Long id, ProgressLogDto log, String email) {
        UserEntity user = getUserByEmail(email);
        ProgressLogEntity entity = getOwnedLog(id, user);
        entity.setWeight(log.getWeight());
        entity.setCalorieIntake(log.getCalorieIntake());
        entity.setProteinIntake(log.getProteinIntake());
        entity.setFatIntake(log.getFatIntake());
        entity.setCarbIntake(log.getCarbIntake());
        entity.setNote(log.getNote());
        return progressLogMapper.toDto(progressLogRepository.save(entity));
    }

    @Override
    public ProgressLogDto getLog(Long id, String email) {
        UserEntity user = getUserByEmail(email);
        return progressLogMapper.toDto(getOwnedLog(id, user));
    }

    @Override
    public void deleteLog(Long id, String email) {
        UserEntity user = getUserByEmail(email);
        progressLogRepository.delete(getOwnedLog(id, user));
    }

    @Override
    public List<ProgressLogDto> getUserLogs(String email) {
        UserEntity user = getUserByEmail(email);
        return progressLogRepository.findByUserOrderByLogDateAsc(user)
                .stream()
                .map(progressLogMapper::toDto)
                .toList();
    }

    @Override
    public List<ProgressLogDto> getUserLogs(String email, LocalDateTime start, LocalDateTime end) {
        UserEntity user = getUserByEmail(email);
        return progressLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end)
                .stream()
                .map(progressLogMapper::toDto)
                .toList();
    }

    private ProgressLogEntity getOwnedLog(Long id, UserEntity user) {
        return progressLogRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ProgressLogNotFoundException("Progress log not found"));
    }

    private UserEntity getUserByEmail(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }
}
