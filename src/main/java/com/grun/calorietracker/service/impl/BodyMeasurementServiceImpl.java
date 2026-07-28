package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.BodyMeasurementDto;
import com.grun.calorietracker.dto.BodyMeasurementRequestDto;
import com.grun.calorietracker.dto.BodyMeasurementSummaryDto;
import com.grun.calorietracker.entity.BodyMeasurementEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.BodyMeasurementUnitSystem;
import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.enums.HealthProvider;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.BodyMeasurementRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.BodyMeasurementService;
import com.grun.calorietracker.service.UserService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BodyMeasurementServiceImpl implements BodyMeasurementService {

    private static final double POUNDS_TO_KG = 0.45359237;
    private static final double INCHES_TO_CM = 2.54;

    private final BodyMeasurementRepository repository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserAnalyticsCacheRevisionService analyticsCacheRevisionService;

    @Override
    @Transactional
    public BodyMeasurementDto create(BodyMeasurementRequestDto request, String email) {
        UserEntity user = requireUser(email);
        HealthProvider provider = request.getProvider() == null ? HealthProvider.MANUAL : request.getProvider();
        String externalId = provider == HealthProvider.MANUAL ? null : trimToNull(request.getExternalId());
        if (provider != HealthProvider.MANUAL && externalId == null) {
            throw new IllegalArgumentException("externalId is required for provider body measurements");
        }

        BodyMeasurementEntity entity = provider == HealthProvider.MANUAL || externalId == null
                ? new BodyMeasurementEntity()
                : repository.findByUserAndProviderAndExternalId(user, provider, externalId)
                .orElseGet(BodyMeasurementEntity::new);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUser(user);
        entity.setProvider(provider);
        entity.setExternalId(externalId);
        apply(request, entity);
        entity.setUpdatedAt(now);

        BodyMeasurementEntity saved = repository.save(entity);
        syncCurrentProfile(user);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.BODY_MEASUREMENT);
        return toDto(saved, user);
    }

    @Override
    @Transactional
    public BodyMeasurementDto update(Long id, BodyMeasurementRequestDto request, String email) {
        UserEntity user = requireUser(email);
        BodyMeasurementEntity entity = requireOwned(id, user);
        if (entity.getProvider() != HealthProvider.MANUAL) {
            throw new AccessDeniedException("Provider measurements can only be changed by an idempotent provider sync");
        }
        apply(request, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        BodyMeasurementEntity saved = repository.save(entity);
        syncCurrentProfile(user);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.BODY_MEASUREMENT);
        return toDto(saved, user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BodyMeasurementDto> list(String email, LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start) || end.isEqual(start)) {
            throw new IllegalArgumentException("Body measurement end must be after start");
        }
        if (start.plusDays(366).isBefore(end)) {
            throw new IllegalArgumentException("Body measurement range cannot exceed 366 days");
        }
        UserEntity user = requireUser(email);
        return repository.findByUserAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(user, start, end)
                .stream()
                .map(entity -> toDto(entity, user))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BodyMeasurementSummaryDto summary(String email) {
        UserEntity user = requireUser(email);
        List<BodyMeasurementEntity> weights = repository
                .findTop2ByUserAndWeightKgIsNotNullOrderByRecordedAtDescIdDesc(user);
        List<BodyMeasurementEntity> bodyFat = repository
                .findTop2ByUserAndBodyFatPercentageIsNotNullOrderByRecordedAtDescIdDesc(user);
        BodyMeasurementEntity latest = repository.findTopByUserOrderByRecordedAtDescIdDesc(user).orElse(null);

        Double currentWeight = value(weights, 0, BodyMeasurementEntity::getWeightKg);
        Double previousWeight = value(weights, 1, BodyMeasurementEntity::getWeightKg);
        Double currentBodyFat = value(bodyFat, 0, BodyMeasurementEntity::getBodyFatPercentage);
        Double previousBodyFat = value(bodyFat, 1, BodyMeasurementEntity::getBodyFatPercentage);

        return BodyMeasurementSummaryDto.builder()
                .recordCount(repository.countByUser(user))
                .latest(latest == null ? null : toDto(latest, user))
                .currentWeightKg(currentWeight)
                .previousWeightKg(previousWeight)
                .weightChangeKg(difference(currentWeight, previousWeight))
                .currentBodyFatPercentage(currentBodyFat)
                .previousBodyFatPercentage(previousBodyFat)
                .bodyFatChangePercentagePoints(difference(currentBodyFat, previousBodyFat))
                .currentBmi(calculateBmi(currentWeight, user.getHeight()))
                .build();
    }

    @Override
    @Transactional
    public void syncWeightFromProgress(Long progressLogId, Double weightKg, LocalDateTime recordedAt, String email) {
        UserEntity user = requireUser(email);
        String externalId = progressExternalId(progressLogId);
        BodyMeasurementEntity entity = repository
                .findByUserAndProviderAndExternalId(user, HealthProvider.MANUAL, externalId)
                .orElseGet(BodyMeasurementEntity::new);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUser(user);
        entity.setProvider(HealthProvider.MANUAL);
        entity.setExternalId(externalId);
        entity.setRecordedAt(recordedAt);
        entity.setWeightKg(round(weightKg));
        entity.setUpdatedAt(now);
        validateCanonical(entity);
        repository.save(entity);
        syncCurrentProfile(user);
    }

    @Override
    @Transactional
    public void deleteWeightFromProgress(Long progressLogId, String email) {
        UserEntity user = requireUser(email);
        repository.findByUserAndProviderAndExternalId(user, HealthProvider.MANUAL, progressExternalId(progressLogId))
                .ifPresent(repository::delete);
        repository.flush();
        syncCurrentProfile(user);
    }

    @Override
    @Transactional
    public void delete(Long id, String email) {
        UserEntity user = requireUser(email);
        BodyMeasurementEntity entity = requireOwned(id, user);
        if (entity.getProvider() != HealthProvider.MANUAL) {
            throw new AccessDeniedException("Provider measurements must be deleted through the provider data flow");
        }
        repository.delete(entity);
        repository.flush();
        syncCurrentProfile(user);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.BODY_MEASUREMENT);
    }

    private void apply(BodyMeasurementRequestDto request, BodyMeasurementEntity entity) {
        boolean imperial = request.getUnitSystem() == BodyMeasurementUnitSystem.IMPERIAL;
        entity.setRecordedAt(request.getRecordedAt());
        entity.setWeightKg(normalizeWeight(request.getWeight(), imperial));
        entity.setBodyFatPercentage(round(request.getBodyFatPercentage()));
        entity.setWaistCm(normalizeLength(request.getWaist(), imperial));
        entity.setChestCm(normalizeLength(request.getChest(), imperial));
        entity.setHipCm(normalizeLength(request.getHip(), imperial));
        entity.setUpperArmCm(normalizeLength(request.getUpperArm(), imperial));
        entity.setThighCm(normalizeLength(request.getThigh(), imperial));
        entity.setNeckCm(normalizeLength(request.getNeck(), imperial));
        entity.setShoulderCm(normalizeLength(request.getShoulder(), imperial));
        entity.setForearmCm(normalizeLength(request.getForearm(), imperial));
        entity.setCalfCm(normalizeLength(request.getCalf(), imperial));
        entity.setLeftUpperArmCm(normalizeLength(request.getLeftUpperArm(), imperial));
        entity.setRightUpperArmCm(normalizeLength(request.getRightUpperArm(), imperial));
        entity.setLeftThighCm(normalizeLength(request.getLeftThigh(), imperial));
        entity.setRightThighCm(normalizeLength(request.getRightThigh(), imperial));
        entity.setLeftCalfCm(normalizeLength(request.getLeftCalf(), imperial));
        entity.setRightCalfCm(normalizeLength(request.getRightCalf(), imperial));
        entity.setNote(trimToNull(request.getNote()));
        validateCanonical(entity);
    }

    private void validateCanonical(BodyMeasurementEntity entity) {
        requireRange(entity.getWeightKg(), 20, 500, "weight");
        requireRange(entity.getBodyFatPercentage(), 0, 80, "bodyFatPercentage");
        requireRange(entity.getWaistCm(), 10, 300, "waist");
        requireRange(entity.getChestCm(), 10, 300, "chest");
        requireRange(entity.getHipCm(), 10, 300, "hip");
        requireRange(entity.getUpperArmCm(), 10, 150, "upperArm");
        requireRange(entity.getThighCm(), 10, 200, "thigh");
        requireRange(entity.getNeckCm(), 10, 100, "neck");
        requireRange(entity.getShoulderCm(), 20, 300, "shoulder");
        requireRange(entity.getForearmCm(), 5, 100, "forearm");
        requireRange(entity.getCalfCm(), 5, 150, "calf");
        requireRange(entity.getLeftUpperArmCm(), 5, 150, "leftUpperArm");
        requireRange(entity.getRightUpperArmCm(), 5, 150, "rightUpperArm");
        requireRange(entity.getLeftThighCm(), 10, 200, "leftThigh");
        requireRange(entity.getRightThighCm(), 10, 200, "rightThigh");
        requireRange(entity.getLeftCalfCm(), 5, 150, "leftCalf");
        requireRange(entity.getRightCalfCm(), 5, 150, "rightCalf");
    }

    private void syncCurrentProfile(UserEntity user) {
        repository.findTop2ByUserAndWeightKgIsNotNullOrderByRecordedAtDescIdDesc(user).stream().findFirst()
                .ifPresent(latest -> {
                    user.setWeight(latest.getWeightKg());
                    user.setBmi(calculateBmi(latest.getWeightKg(), user.getHeight()));
                });
        repository.findTop2ByUserAndBodyFatPercentageIsNotNullOrderByRecordedAtDescIdDesc(user).stream().findFirst()
                .ifPresent(latest -> user.setBodyFatPercentage(latest.getBodyFatPercentage()));
        userRepository.save(user);
    }

    private BodyMeasurementEntity requireOwned(Long id, UserEntity user) {
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Body measurement not found"));
    }

    private UserEntity requireUser(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private BodyMeasurementDto toDto(BodyMeasurementEntity entity, UserEntity user) {
        return BodyMeasurementDto.builder()
                .id(entity.getId())
                .recordedAt(entity.getRecordedAt())
                .weightKg(entity.getWeightKg())
                .bodyFatPercentage(entity.getBodyFatPercentage())
                .waistCm(entity.getWaistCm())
                .chestCm(entity.getChestCm())
                .hipCm(entity.getHipCm())
                .upperArmCm(entity.getUpperArmCm())
                .thighCm(entity.getThighCm())
                .neckCm(entity.getNeckCm())
                .shoulderCm(entity.getShoulderCm())
                .forearmCm(entity.getForearmCm())
                .calfCm(entity.getCalfCm())
                .leftUpperArmCm(entity.getLeftUpperArmCm())
                .rightUpperArmCm(entity.getRightUpperArmCm())
                .leftThighCm(entity.getLeftThighCm())
                .rightThighCm(entity.getRightThighCm())
                .leftCalfCm(entity.getLeftCalfCm())
                .rightCalfCm(entity.getRightCalfCm())
                .bmi(calculateBmi(entity.getWeightKg(), user.getHeight()))
                .provider(entity.getProvider())
                .externalId(entity.getExternalId())
                .note(entity.getNote())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Double normalizeWeight(Double value, boolean imperial) {
        return round(value == null ? null : value * (imperial ? POUNDS_TO_KG : 1));
    }

    private Double normalizeLength(Double value, boolean imperial) {
        return round(value == null ? null : value * (imperial ? INCHES_TO_CM : 1));
    }

    private Double calculateBmi(Double weightKg, Double heightCm) {
        if (weightKg == null || heightCm == null || heightCm <= 0) {
            return null;
        }
        double heightM = heightCm / 100.0;
        return round(weightKg / (heightM * heightM));
    }

    private Double difference(Double current, Double previous) {
        return current == null || previous == null ? null : round(current - previous);
    }

    private <T> Double value(List<T> values, int index, java.util.function.Function<T, Double> getter) {
        return values.size() > index ? getter.apply(values.get(index)) : null;
    }

    private void requireRange(Double value, double min, double max, String field) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(field + " is outside the supported range");
        }
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private String progressExternalId(Long progressLogId) {
        return "progress-log:" + progressLogId;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
