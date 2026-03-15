package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.ExerciseItemNotFoundException;
import com.grun.calorietracker.exception.ExerciseLogNotFoundException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.ExerciseLogsMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.ExerciseLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseLogsServiceImpl implements ExerciseLogsService {

    private final ExerciseLogRepository exerciseLogsRepository;
    private final ExerciseItemRepository exerciseItemRepository;
    private final UserRepository userRepository;
    private final ExerciseLogsMapper exerciseLogsMapper;

    @Override
    public ExerciseLogsDto addExerciseLog(ExerciseLogsDto dto, String email) {
        UserEntity user = getUserByEmail(email);
        ExerciseItemEntity exerciseItem = getExerciseItemById(dto.getExerciseItemId());

        ExerciseLogsEntity entity = new ExerciseLogsEntity();
        entity.setUser(user);
        entity.setExerciseItem(exerciseItem);
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setCaloriesBurned(dto.getCaloriesBurned());
        entity.setLogDate(dto.getLogDate());
        entity.setSource(dto.getSource());
        entity.setExternalId(dto.getExternalId());
        entity.setExtraData(dto.getExtraData());

        return exerciseLogsMapper.toDto(exerciseLogsRepository.save(entity));
    }

    @Override
    public List<ExerciseLogsDto> getExerciseLogsByDateAndUser(String email, LocalDateTime startDate, LocalDateTime endDate, String range) {
        UserEntity user = getUserByEmail(email);

        List<Object[]> rows = exerciseLogsRepository.findByUserAndLogDateBetween(user.getId(), startDate, endDate, range);
        return rows.stream().map(r -> {
            ExerciseLogsDto dto = new ExerciseLogsDto();
            dto.setLogDate(((Timestamp) r[0]).toLocalDateTime());
            dto.setDurationMinutes(((Number) r[1]).intValue());
            dto.setCaloriesBurned(((Number) r[2]).doubleValue());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public ExerciseLogsDto getExerciseLogById(Long id, String email) {
        UserEntity user = getUserByEmail(email);
        ExerciseLogsEntity entity = getLogsItemById(id,user);
        return exerciseLogsMapper.toDto(entity);
    }

    @Override
    public void deleteExerciseLog(Long id, String email) {
        UserEntity user = getUserByEmail(email);
        ExerciseLogsEntity entity = getLogsItemById(id,user);
        exerciseLogsRepository.delete(entity);
    }

    @Override
    public ExerciseLogsDto addExerciseLogFromExternal(ExerciseLogsDto dto, String email) {
        UserEntity user = getUserByEmail(email);
        // TODO: implement mapping from external source
        return null;
    }

    @Override
    public List<ExerciseLogsDto> getExerciseLogsBySource(String email, String source) {
        UserEntity user = getUserByEmail(email);
        // TODO: implement query filtering by source
        return List.of();
    }

    private UserEntity getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private ExerciseItemEntity getExerciseItemById(Long id) {
        return exerciseItemRepository.findById(id)
                .orElseThrow(() -> new ExerciseItemNotFoundException("Exercise item not found"));
    }

    private ExerciseLogsEntity getLogsItemById(Long id, UserEntity user) {
        return exerciseLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ExerciseLogNotFoundException("Exercise log not found"));
    }
}
