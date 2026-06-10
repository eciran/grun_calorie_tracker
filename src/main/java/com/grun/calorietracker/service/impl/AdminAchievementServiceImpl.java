package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminAchievementDefinitionDto;
import com.grun.calorietracker.dto.AdminAchievementDefinitionRequestDto;
import com.grun.calorietracker.dto.AdminAchievementMetricsDto;
import com.grun.calorietracker.entity.AchievementDefinitionEntity;
import com.grun.calorietracker.repository.AchievementDefinitionRepository;
import com.grun.calorietracker.service.AdminAchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAchievementServiceImpl implements AdminAchievementService {

    private static final List<String> SUPPORTED_METRIC_KEYS = List.of(
            AchievementServiceImpl.PROFILE_COMPLETED,
            AchievementServiceImpl.GOAL_SET,
            AchievementServiceImpl.FOOD_LOG_COUNT,
            AchievementServiceImpl.CORE_MEALS_SINGLE_DAY,
            AchievementServiceImpl.FOOD_DISTINCT_DAYS,
            AchievementServiceImpl.EXERCISE_LOG_COUNT,
            AchievementServiceImpl.EXERCISE_WEEKLY_BURN_CALORIES,
            AchievementServiceImpl.FASTING_COMPLETED_COUNT,
            AchievementServiceImpl.FASTING_DISTINCT_COMPLETED_DAYS,
            AchievementServiceImpl.PROGRESS_LOG_COUNT,
            AchievementServiceImpl.WEIGHT_PROGRESS_KG,
            AchievementServiceImpl.WATER_LOG_COUNT,
            AchievementServiceImpl.WATER_TARGET_HIT_COUNT
    );

    private static final Set<String> SUPPORTED_METRIC_KEY_SET = Set.copyOf(SUPPORTED_METRIC_KEYS);

    private final AchievementDefinitionRepository achievementDefinitionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminAchievementDefinitionDto> listDefinitions() {
        return achievementDefinitionRepository.findAllByOrderBySortOrderAscCodeAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AdminAchievementMetricsDto listMetricKeys() {
        return new AdminAchievementMetricsDto(SUPPORTED_METRIC_KEYS);
    }

    @Override
    @Transactional
    public AdminAchievementDefinitionDto createDefinition(AdminAchievementDefinitionRequestDto request) {
        validateMetric(request.getMetricKey());
        String code = normalizeCode(request.getCode());
        if (achievementDefinitionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Achievement code already exists.");
        }
        AchievementDefinitionEntity entity = new AchievementDefinitionEntity();
        entity.setCode(code);
        entity.setCreatedAt(LocalDateTime.now());
        apply(entity, request);
        return toDto(achievementDefinitionRepository.save(entity));
    }

    @Override
    @Transactional
    public AdminAchievementDefinitionDto updateDefinition(String code, AdminAchievementDefinitionRequestDto request) {
        validateMetric(request.getMetricKey());
        String normalizedPathCode = normalizeCode(code);
        String normalizedBodyCode = normalizeCode(request.getCode());
        if (!normalizedPathCode.equals(normalizedBodyCode)) {
            throw new IllegalArgumentException("Achievement code cannot be changed.");
        }
        AchievementDefinitionEntity entity = findDefinition(normalizedPathCode);
        apply(entity, request);
        return toDto(achievementDefinitionRepository.save(entity));
    }

    @Override
    @Transactional
    public AdminAchievementDefinitionDto deactivateDefinition(String code) {
        AchievementDefinitionEntity entity = findDefinition(normalizeCode(code));
        entity.setActive(false);
        return toDto(achievementDefinitionRepository.save(entity));
    }

    private AchievementDefinitionEntity findDefinition(String code) {
        return achievementDefinitionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Achievement definition was not found."));
    }

    private void apply(AchievementDefinitionEntity entity, AdminAchievementDefinitionRequestDto request) {
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(request.getDescription().trim());
        entity.setMetricKey(request.getMetricKey().trim());
        entity.setCategory(request.getCategory());
        entity.setTier(request.getTier());
        entity.setTargetValue(request.getTargetValue());
        entity.setActive(request.getActive());
        entity.setSortOrder(request.getSortOrder());
    }

    private void validateMetric(String metricKey) {
        if (metricKey == null || !SUPPORTED_METRIC_KEY_SET.contains(metricKey.trim())) {
            throw new IllegalArgumentException("Unsupported achievement metricKey.");
        }
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private AdminAchievementDefinitionDto toDto(AchievementDefinitionEntity entity) {
        return AdminAchievementDefinitionDto.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .metricKey(entity.getMetricKey())
                .category(entity.getCategory())
                .tier(entity.getTier())
                .targetValue(entity.getTargetValue())
                .active(entity.getActive())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
