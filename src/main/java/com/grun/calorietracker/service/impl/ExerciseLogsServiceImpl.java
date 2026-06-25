package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.exception.DuplicateExternalExerciseLogException;
import com.grun.calorietracker.exception.ExerciseItemNotFoundException;
import com.grun.calorietracker.exception.ExerciseLogNotFoundException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.mapper.ExerciseItemMapper;
import com.grun.calorietracker.mapper.ExerciseLogsMapper;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.ExerciseLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseLogsServiceImpl implements ExerciseLogsService {

    private static final int DEFAULT_SOURCE_PAGE_SIZE = 50;
    private static final int MAX_SOURCE_PAGE_SIZE = 100;

    private final ExerciseLogRepository exerciseLogsRepository;
    private final ExerciseItemRepository exerciseItemRepository;
    private final UserRepository userRepository;
    private final ExerciseLogsMapper exerciseLogsMapper;

    @Override
    public ExerciseLogsDto addExerciseLog(ExerciseLogsDto dto, String email) {
        UserEntity user = getUserByEmail(email);
        ExerciseItemEntity exerciseItem = getExerciseItemById(dto.getExerciseItemId());

        ExerciseLogsEntity entity = buildExerciseLogEntity(dto, user, exerciseItem);
        entity.setSource(normalizeSource(dto.getSource(), "MANUAL"));
        entity.setExternalId(trimToNull(dto.getExternalId()));
        return exerciseLogsMapper.toDto(exerciseLogsRepository.save(entity));
    }

    @Override
    public ExerciseLogsDto updateExerciseLog(Long id, ExerciseLogsDto dto, String email) {
        UserEntity user = getUserByEmail(email);
        ExerciseLogsEntity entity = getLogsItemById(id, user);
        ExerciseItemEntity exerciseItem = getExerciseItemById(dto.getExerciseItemId());
        entity.setExerciseItem(exerciseItem);
        applyExerciseMetrics(entity, dto);
        entity.setCaloriesBurned(dto.getCaloriesBurned());
        entity.setLogDate(dto.getLogDate());
        entity.setExtraData(dto.getExtraData());
        return exerciseLogsMapper.toDto(exerciseLogsRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<ExerciseLogsDto> getExerciseLogsHistory(String email, LocalDateTime startDate, LocalDateTime endDate) {
        UserEntity user = getUserByEmail(email);
        return exerciseLogsRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, startDate, endDate)
                .stream()
                .map(exerciseLogsMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
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
        ExerciseItemEntity exerciseItem = getExerciseItemById(dto.getExerciseItemId());
        String source = normalizeRequired(dto.getSource(), "External source is required.");
        String externalId = requireTrimmed(dto.getExternalId(), "External id is required.");

        exerciseLogsRepository.findByUserAndSourceAndExternalId(user, source, externalId)
                .ifPresent(existing -> {
                    throw new DuplicateExternalExerciseLogException(
                            "Exercise log already exists for source " + source + " and external id " + externalId
                    );
                });

        ExerciseLogsEntity entity = buildExerciseLogEntity(dto, user, exerciseItem);
        entity.setSource(source);
        entity.setExternalId(externalId);
        return exerciseLogsMapper.toDto(exerciseLogsRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExerciseLogsDto> getExerciseLogsBySource(String email, String source, int page, int size) {
        UserEntity user = getUserByEmail(email);
        String normalizedSource = normalizeRequired(source, "Source is required.");
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SOURCE_PAGE_SIZE : Math.min(size, MAX_SOURCE_PAGE_SIZE);
        return exerciseLogsRepository.findByUserAndSource(
                        user,
                        normalizedSource,
                        PageRequest.of(safePage, safeSize, Sort.by("logDate").descending())
                )
                .getContent()
                .stream()
                .map(exerciseLogsMapper::toDto)
                .collect(Collectors.toList());
    }

    private ExerciseLogsEntity buildExerciseLogEntity(ExerciseLogsDto dto, UserEntity user, ExerciseItemEntity exerciseItem) {
        ExerciseLogsEntity entity = new ExerciseLogsEntity();
        entity.setUser(user);
        entity.setExerciseItem(exerciseItem);
        applyExerciseMetrics(entity, dto);
        entity.setCaloriesBurned(dto.getCaloriesBurned());
        entity.setLogDate(dto.getLogDate());
        entity.setExtraData(dto.getExtraData());
        return entity;
    }

    private void applyExerciseMetrics(ExerciseLogsEntity entity, ExerciseLogsDto dto) {
        validateAtLeastOneMeasurement(dto);
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setSetCount(dto.getSetCount());
        entity.setReps(dto.getReps());
        entity.setWeightKg(dto.getWeightKg());
        entity.setDistanceKm(dto.getDistanceKm());
        entity.setMeasurementType(resolveMeasurementType(dto));
    }

    private void validateAtLeastOneMeasurement(ExerciseLogsDto dto) {
        if (positive(dto.getDurationMinutes())
                || positive(dto.getSetCount())
                || positive(dto.getReps())
                || positive(dto.getWeightKg())
                || positive(dto.getDistanceKm())) {
            return;
        }
        throw new IllegalArgumentException("Exercise log requires at least one measurement: durationMinutes, setCount, reps, weightKg, or distanceKm.");
    }


    private void validateMeasurementAgainstExercise(ExerciseItemEntity exerciseItem, ExerciseLogsDto dto) {
        ExerciseLogMeasurementType measurementType = resolveMeasurementType(dto);
        Set<ExerciseLogMeasurementType> allowedTypes = allowedMeasurementTypes(exerciseItem);
        if (!allowedTypes.contains(measurementType)) {
            throw new IllegalArgumentException("Exercise " + exerciseItem.getName() + " does not allow measurement type " + measurementType + ". Allowed types: " + allowedTypes);
        }

        switch (measurementType) {
            case DURATION -> {
                require(dto.getDurationMinutes(), "durationMinutes is required for DURATION exercise logs.");
                reject(dto.getSetCount(), "setCount is not allowed for DURATION exercise logs.");
                reject(dto.getReps(), "reps is not allowed for DURATION exercise logs.");
                reject(dto.getWeightKg(), "weightKg is not allowed for DURATION exercise logs.");
                reject(dto.getDistanceKm(), "distanceKm is not allowed for DURATION exercise logs. Use DISTANCE or MIXED.");
            }
            case DISTANCE -> {
                require(dto.getDistanceKm(), "distanceKm is required for DISTANCE exercise logs.");
                reject(dto.getSetCount(), "setCount is not allowed for DISTANCE exercise logs.");
                reject(dto.getReps(), "reps is not allowed for DISTANCE exercise logs.");
                reject(dto.getWeightKg(), "weightKg is not allowed for DISTANCE exercise logs.");
            }
            case REPS -> {
                require(dto.getReps(), "reps is required for REPS exercise logs.");
                reject(dto.getDurationMinutes(), "durationMinutes is not allowed for REPS exercise logs.");
                reject(dto.getSetCount(), "setCount is not allowed for REPS exercise logs. Use SETS_REPS.");
                reject(dto.getWeightKg(), "weightKg is not allowed for REPS exercise logs. Use WEIGHT_REPS.");
                reject(dto.getDistanceKm(), "distanceKm is not allowed for REPS exercise logs.");
            }
            case SETS_REPS -> {
                require(dto.getSetCount(), "setCount is required for SETS_REPS exercise logs.");
                require(dto.getReps(), "reps is required for SETS_REPS exercise logs.");
                reject(dto.getDurationMinutes(), "durationMinutes is not allowed for SETS_REPS exercise logs.");
                reject(dto.getWeightKg(), "weightKg is not allowed for SETS_REPS exercise logs. Use WEIGHT_REPS.");
                reject(dto.getDistanceKm(), "distanceKm is not allowed for SETS_REPS exercise logs.");
            }
            case WEIGHT_REPS -> {
                require(dto.getWeightKg(), "weightKg is required for WEIGHT_REPS exercise logs.");
                require(dto.getReps(), "reps is required for WEIGHT_REPS exercise logs.");
                reject(dto.getDurationMinutes(), "durationMinutes is not allowed for WEIGHT_REPS exercise logs.");
                reject(dto.getDistanceKm(), "distanceKm is not allowed for WEIGHT_REPS exercise logs.");
            }
            case MIXED -> validateMixedMeasurement(dto);
        }
    }

    private Set<ExerciseLogMeasurementType> allowedMeasurementTypes(ExerciseItemEntity exerciseItem) {
        List<ExerciseLogMeasurementType> configured = ExerciseItemMapper.parseAllowedMeasurementTypes(exerciseItem.getAllowedMeasurementTypes());
        if (!configured.isEmpty()) {
            return EnumSet.copyOf(configured);
        }
        return EnumSet.of(ExerciseLogMeasurementType.DURATION);
    }

    private void validateMixedMeasurement(ExerciseLogsDto dto) {
        boolean hasDuration = positive(dto.getDurationMinutes());
        boolean hasDistance = positive(dto.getDistanceKm());
        boolean hasSetsOrReps = positive(dto.getSetCount()) || positive(dto.getReps());
        boolean hasWeight = positive(dto.getWeightKg());
        int metricKinds = (hasDuration ? 1 : 0) + (hasDistance ? 1 : 0) + (hasSetsOrReps ? 1 : 0) + (hasWeight ? 1 : 0);
        if (metricKinds < 2) {
            throw new IllegalArgumentException("MIXED exercise logs require at least two compatible measurement groups.");
        }
        if (hasSetsOrReps || hasWeight) {
            throw new IllegalArgumentException("MIXED exercise logs currently support cardio-style duration/distance combinations only.");
        }
    }

    private void require(Integer value, String message) {
        if (!positive(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void require(Double value, String message) {
        if (!positive(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void reject(Integer value, String message) {
        if (positive(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void reject(Double value, String message) {
        if (positive(value)) {
            throw new IllegalArgumentException(message);
        }
    }
    private ExerciseLogMeasurementType resolveMeasurementType(ExerciseLogsDto dto) {
        if (dto.getMeasurementType() != null) {
            return dto.getMeasurementType();
        }
        boolean hasDuration = positive(dto.getDurationMinutes());
        boolean hasSets = positive(dto.getSetCount());
        boolean hasReps = positive(dto.getReps());
        boolean hasWeight = positive(dto.getWeightKg());
        boolean hasDistance = positive(dto.getDistanceKm());
        int metricKinds = (hasDuration ? 1 : 0)
                + (hasSets || hasReps ? 1 : 0)
                + (hasWeight ? 1 : 0)
                + (hasDistance ? 1 : 0);
        if (metricKinds > 1 && !(hasWeight && hasReps && metricKinds == 2)) {
            return ExerciseLogMeasurementType.MIXED;
        }
        if (hasWeight && hasReps) {
            return ExerciseLogMeasurementType.WEIGHT_REPS;
        }
        if (hasSets && hasReps) {
            return ExerciseLogMeasurementType.SETS_REPS;
        }
        if (hasReps) {
            return ExerciseLogMeasurementType.REPS;
        }
        if (hasDistance) {
            return ExerciseLogMeasurementType.DISTANCE;
        }
        return ExerciseLogMeasurementType.DURATION;
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private boolean positive(Double value) {
        return value != null && value > 0;
    }

    private String normalizeSource(String source, String defaultSource) {
        String normalized = trimToNull(source);
        return normalized == null ? defaultSource : normalized.toUpperCase();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized.toUpperCase();
    }

    private String requireTrimmed(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
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
