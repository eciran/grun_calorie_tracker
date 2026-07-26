package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.service.impl.AdminOnboardingAnalyticsServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOnboardingAnalyticsServiceImplTest {

    @Test
    void getSummary_returnsRawPrivacySafeFunnelCounts() {
        ProductAnalyticsEventRepository repository = mock(ProductAnalyticsEventRepository.class);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_STARTED), any(LocalDateTime.class))).thenReturn(10L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED), any(LocalDateTime.class))).thenReturn(42L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_STEP_COMPLETED), any(LocalDateTime.class))).thenReturn(30L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_STEP_FAILED), any(LocalDateTime.class))).thenReturn(2L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_RESUMED), any(LocalDateTime.class))).thenReturn(4L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_PREVIEWED), any(LocalDateTime.class))).thenReturn(7L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_COMPLETED), any(LocalDateTime.class))).thenReturn(6L);
        when(repository.countByEventTypeAndCreatedAtAfter(eq(ProductAnalyticsEventType.ONBOARDING_ABANDONED), any(LocalDateTime.class))).thenReturn(3L);

        var result = new AdminOnboardingAnalyticsServiceImpl(repository).getSummary(168);

        assertEquals(168, result.hours());
        assertEquals(10, result.started());
        assertEquals(42, result.stepViewed());
        assertEquals(30, result.stepCompleted());
        assertEquals(2, result.stepFailed());
        assertEquals(4, result.resumed());
        assertEquals(7, result.previewed());
        assertEquals(6, result.completed());
        assertEquals(3, result.abandoned());
    }

    @Test
    void getSummary_rejectsUnsafeWindow() {
        ProductAnalyticsEventRepository repository = mock(ProductAnalyticsEventRepository.class);
        AdminOnboardingAnalyticsServiceImpl service = new AdminOnboardingAnalyticsServiceImpl(repository);

        assertThrows(IllegalArgumentException.class, () -> service.getSummary(0));
        assertThrows(IllegalArgumentException.class, () -> service.getSummary(2161));
    }
}
