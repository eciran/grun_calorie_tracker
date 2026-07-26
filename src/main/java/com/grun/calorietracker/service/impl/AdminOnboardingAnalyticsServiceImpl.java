package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminOnboardingAnalyticsDto;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.service.AdminOnboardingAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminOnboardingAnalyticsServiceImpl implements AdminOnboardingAnalyticsService {

    private final ProductAnalyticsEventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminOnboardingAnalyticsDto getSummary(int hours) {
        if (hours < 1 || hours > 2160) {
            throw new IllegalArgumentException("Analytics window must be between 1 and 2160 hours.");
        }
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime since = generatedAt.minusHours(hours);
        return new AdminOnboardingAnalyticsDto(
                hours,
                since,
                generatedAt,
                count(ProductAnalyticsEventType.ONBOARDING_STARTED, since),
                count(ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED, since),
                count(ProductAnalyticsEventType.ONBOARDING_STEP_COMPLETED, since),
                count(ProductAnalyticsEventType.ONBOARDING_STEP_FAILED, since),
                count(ProductAnalyticsEventType.ONBOARDING_RESUMED, since),
                count(ProductAnalyticsEventType.ONBOARDING_PREVIEWED, since),
                count(ProductAnalyticsEventType.ONBOARDING_COMPLETED, since),
                count(ProductAnalyticsEventType.ONBOARDING_ABANDONED, since)
        );
    }

    private long count(ProductAnalyticsEventType eventType, LocalDateTime since) {
        return eventRepository.countByEventTypeAndCreatedAtAfter(eventType, since);
    }
}
