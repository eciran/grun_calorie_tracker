package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.ExerciseItemNotFoundException;
import com.grun.calorietracker.exception.ExerciseLogNotFoundException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.ExerciseLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseLogsServiceImpl implements ExerciseLogsService {

    private final ExerciseLogRepository exerciseLogsRepository;
    private final ExerciseItemRepository exerciseItemRepository;
    private final UserRepository userRepository;

    @Override
    public ExerciseLogsDto addExerciseLog(ExerciseLogsDto dto, String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ExerciseItemEntity exerciseItem = exerciseItemRepository.findById(dto.getExerciseItemId())
                .orElseThrow(() -> new ExerciseItemNotFoundException("Exercise item not found"));

        ExerciseLogsEntity entity = new ExerciseLogsEntity();
        entity.setUser(user);
        entity.setExerciseItem(exerciseItem);
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setCaloriesBurned(dto.getCaloriesBurned());
        entity.setLogDate(dto.getLogDate());

        entity.setSource(dto.getSource());
        entity.setExternalId(dto.getExternalId());
        entity.setExtraData(dto.getExtraData());

        ExerciseLogsEntity saved = exerciseLogsRepository.save(entity);
        return toDto(saved);
    }

    @Override
    public List<ExerciseLogsDto> getExerciseLogs(String email, String date) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        List<ExerciseLogsEntity> logs;
        if (date != null) {
            LocalDate targetDate = LocalDate.parse(date);
            logs = exerciseLogsRepository.findByUserAndLogDateBetween(
                    user,
                    targetDate.atStartOfDay(),
                    targetDate.plusDays(1).atStartOfDay()
            );
        } else {
            logs = exerciseLogsRepository.findByUser(user);
        }
        return logs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public ExerciseLogsDto getExerciseLogById(Long id, String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ExerciseLogsEntity entity = exerciseLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ExerciseLogNotFoundException("Exercise log not found"));
        return toDto(entity);
    }

    @Override
    public void deleteExerciseLog(Long id, String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ExerciseLogsEntity entity = exerciseLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ExerciseLogNotFoundException("Exercise log not found"));
        exerciseLogsRepository.delete(entity);
    }

    @Override
    public ExerciseLogsDto addExerciseLogFromExternal(ExerciseLogsDto dto, String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return null;
    }

    @Override
    public List<ExerciseLogsDto> getExerciseLogsBySource(String email, String source) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        return List.of();
    }

    private ExerciseLogsDto toDto(ExerciseLogsEntity entity) {
        ExerciseLogsDto dto = new ExerciseLogsDto();
        dto.setExerciseItemId(entity.getId());
        if (entity.getExerciseItem() != null) {
            dto.setExerciseItemId(entity.getExerciseItem().getId());
            dto.setExerciseItemName(entity.getExerciseItem().getName());
        }
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setCaloriesBurned(entity.getCaloriesBurned());
        dto.setLogDate(entity.getLogDate());
        dto.setSource(entity.getSource());
        dto.setExternalId(entity.getExternalId());
        dto.setExtraData(entity.getExtraData());
        return dto;
    }
}
