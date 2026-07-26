package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ProductAnalyticsEventRequestDto;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.ProductAnalyticsServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProductAnalyticsOnboardingPrivacyTest {

    @Test
    void genericAnalyticsEndpoint_rejectsOnboardingEventsWithFreeMetadata() {
        UserRepository userRepository = mock(UserRepository.class);
        ProductAnalyticsEventRepository eventRepository = mock(ProductAnalyticsEventRepository.class);
        ProductAnalyticsServiceImpl service = new ProductAnalyticsServiceImpl(
                userRepository,
                eventRepository,
                new ObjectMapper()
        );
        ProductAnalyticsEventRequestDto request = new ProductAnalyticsEventRequestDto();
        request.setEventType(ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED);
        request.setMetadata(Map.of("weight", 82, "allergens", "PEANUTS"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordEvent("user@example.com", request)
        );
        verify(userRepository, never()).findByEmail("user@example.com");
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
