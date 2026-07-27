package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.OnboardingAnalyticsEventRequestDto;
import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.OnboardingClientAnalyticsEventType;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.OnboardingAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingAnalyticsServiceImpl implements OnboardingAnalyticsService {

    private static final String SURFACE = "mobile_onboarding";
    private static final String TARGET_TYPE = "ONBOARDING_STEP";

    private final UserRepository userRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordServerEvent(String email, ProductAnalyticsEventType eventType, OnboardingStep step) {
        if (eventType == null || !eventType.name().startsWith("ONBOARDING_")) {
            throw new IllegalArgumentException("Only onboarding analytics events can be recorded.");
        }
        save(email, eventType, step, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClientEvent(String email, OnboardingAnalyticsEventRequestDto request) {
        ProductAnalyticsEventType eventType = switch (request.getEventType()) {
            case STEP_VIEWED -> ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED;
            case ABANDONED -> ProductAnalyticsEventType.ONBOARDING_ABANDONED;
        };
        if (request.getEventType() == OnboardingClientAnalyticsEventType.STEP_VIEWED
                && request.getStep() == null) {
            throw new IllegalArgumentException("Step is required for a viewed event.");
        }
        save(email, eventType, request.getStep(), request.getLanguage(), request.getDurationMs());
    }

    private void save(String email, ProductAnalyticsEventType eventType, OnboardingStep step,
                      String language, Long durationMs) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ProductAnalyticsEventEntity event = new ProductAnalyticsEventEntity();
        event.setUser(user);
        event.setEventType(eventType);
        event.setEventVersion(1);
        event.setSurface(SURFACE);
        event.setMarketRegion(user.getMarketRegion() == null ? null : user.getMarketRegion().name());
        event.setLanguage(trimToNull(language));
        event.setDurationMs(durationMs);
        event.setTargetType(step == null ? null : TARGET_TYPE + ":" + step.name());
        event.setTargetId(null);
        event.setMetadataJson(null);
        productAnalyticsEventRepository.save(event);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
