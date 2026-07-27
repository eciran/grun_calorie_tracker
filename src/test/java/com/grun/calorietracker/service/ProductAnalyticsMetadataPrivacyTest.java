package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ProductAnalyticsEventRequestDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.ProductAnalyticsServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAnalyticsMetadataPrivacyTest {

    @Test
    void recordEvent_rejectsSensitiveMetadataKeys() {
        UserRepository userRepository = mock(UserRepository.class);
        ProductAnalyticsEventRepository eventRepository = mock(ProductAnalyticsEventRepository.class);
        ProductAnalyticsServiceImpl service = new ProductAnalyticsServiceImpl(
                userRepository, eventRepository, new ObjectMapper());
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        ProductAnalyticsEventRequestDto request = new ProductAnalyticsEventRequestDto();
        request.setEventType(ProductAnalyticsEventType.LOG_FLOW_COMPLETED);
        request.setMetadata(Map.of("healthNote", "private detail"));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordEvent("user@example.com", request));

        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
