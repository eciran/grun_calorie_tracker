package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ProductAnalyticsEventDto;
import com.grun.calorietracker.dto.ProductAnalyticsEventRequestDto;
import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.ProductAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductAnalyticsServiceImpl implements ProductAnalyticsService {

    private static final int MAX_METADATA_JSON_LENGTH = 4000;
    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of(
            "resultCount", "source", "outcome", "variant", "reasonCode");

    private final UserRepository userRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProductAnalyticsEventDto recordEvent(String email, ProductAnalyticsEventRequestDto request) {
        if (request.getEventType() != null
                && request.getEventType().name().startsWith("ONBOARDING_")) {
            throw new IllegalArgumentException("Onboarding events must use the privacy-safe onboarding event endpoint.");
        }
        if (request.getEventType() == com.grun.calorietracker.enums.ProductAnalyticsEventType.FEATURE_USED
                && request.getFeature() == null) {
            throw new IllegalArgumentException("Feature is required for FEATURE_USED analytics events.");
        }
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ProductAnalyticsEventEntity entity = new ProductAnalyticsEventEntity();
        entity.setUser(user);
        entity.setEventType(request.getEventType());
        entity.setEventVersion(1);
        entity.setSurface(trimToNull(request.getSurface()));
        entity.setMarketRegion(user.getMarketRegion() == null ? null : user.getMarketRegion().name());
        entity.setLanguage(trimToNull(request.getLanguage()));
        entity.setStartedAt(request.getStartedAt());
        entity.setCompletedAt(request.getCompletedAt());
        entity.setDurationMs(resolveDurationMs(request));
        entity.setTargetType(request.getEventType() == com.grun.calorietracker.enums.ProductAnalyticsEventType.FEATURE_USED
                ? request.getFeature().name()
                : trimToNull(request.getTargetType()));
        entity.setTargetId(request.getTargetId());
        entity.setMetadataJson(writeSafeMetadata(request.getMetadata()));
        return toDto(productAnalyticsEventRepository.save(entity));
    }

    private Long resolveDurationMs(ProductAnalyticsEventRequestDto request) {
        if (request.getDurationMs() != null) {
            return Math.max(0, request.getDurationMs());
        }
        if (request.getStartedAt() != null && request.getCompletedAt() != null && !request.getCompletedAt().isBefore(request.getStartedAt())) {
            return Duration.between(request.getStartedAt(), request.getCompletedAt()).toMillis();
        }
        return null;
    }

    private String writeSafeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!ALLOWED_METADATA_KEYS.contains(key)) {
                throw new IllegalArgumentException("Analytics metadata key is not allowed: " + key);
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("Analytics metadata values must be scalar.");
            }
            safe.put(key, value);
        });
        try {
            String json = objectMapper.writeValueAsString(safe);
            if (json.length() > MAX_METADATA_JSON_LENGTH) {
                throw new IllegalArgumentException("Analytics metadata is too large.");
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Analytics metadata could not be serialized.");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private ProductAnalyticsEventDto toDto(ProductAnalyticsEventEntity entity) {
        ProductAnalyticsEventDto dto = new ProductAnalyticsEventDto();
        dto.setId(entity.getId());
        dto.setEventType(entity.getEventType());
        dto.setSurface(entity.getSurface());
        dto.setDurationMs(entity.getDurationMs());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetId(entity.getTargetId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
