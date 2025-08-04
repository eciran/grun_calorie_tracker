package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.ExerciseItemNotFoundException;
import com.grun.calorietracker.exception.ExerciseLogNotFoundException;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
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

    @Override
    public ExerciseLogsDto addExerciseLog(ExerciseLogsDto dto, UserEntity user) {
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
    public List<ExerciseLogsDto> getExerciseLogs(UserEntity user, String date) {
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
    public ExerciseLogsDto getExerciseLogById(Long id, UserEntity user) {
        ExerciseLogsEntity entity = exerciseLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ExerciseLogNotFoundException("Exercise log not found"));
        return toDto(entity);
    }

    @Override
    public void deleteExerciseLog(Long id, UserEntity user) {
        ExerciseLogsEntity entity = exerciseLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ExerciseLogNotFoundException("Exercise log not found"));
        exerciseLogsRepository.delete(entity);
    }

    @Override
    public ExerciseLogsDto addExerciseLogFromExternal(ExerciseLogsDto dto, UserEntity user) {
        return null;
    }

    @Override
    public List<ExerciseLogsDto> getExerciseLogsBySource(UserEntity user, String source) {
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
