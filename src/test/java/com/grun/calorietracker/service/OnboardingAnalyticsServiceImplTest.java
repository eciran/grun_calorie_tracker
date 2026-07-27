package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OnboardingAnalyticsEventRequestDto;
import com.grun.calorietracker.entity.ProductAnalyticsEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.OnboardingClientAnalyticsEventType;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.OnboardingAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingAnalyticsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ProductAnalyticsEventRepository eventRepository;

    private OnboardingAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OnboardingAnalyticsServiceImpl(userRepository, eventRepository);
    }

    @Test
    void recordClientEvent_persistsOnlyAllowlistedFieldsWithoutMetadata() {
        UserEntity user = user();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OnboardingAnalyticsEventRequestDto request = new OnboardingAnalyticsEventRequestDto();
        request.setEventType(OnboardingClientAnalyticsEventType.STEP_VIEWED);
        request.setStep(OnboardingStep.NUTRITION);
        request.setLanguage("en_IE");
        request.setDurationMs(900L);

        service.recordClientEvent("user@example.com", request);

        ArgumentCaptor<ProductAnalyticsEventEntity> captor =
                ArgumentCaptor.forClass(ProductAnalyticsEventEntity.class);
        verify(eventRepository).save(captor.capture());
        ProductAnalyticsEventEntity event = captor.getValue();
        assertEquals(ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED, event.getEventType());
        assertEquals(1, event.getEventVersion());
        assertEquals("mobile_onboarding", event.getSurface());
        assertEquals("UK_IE", event.getMarketRegion());
        assertEquals("ONBOARDING_STEP:NUTRITION", event.getTargetType());
        assertNull(event.getTargetId());
        assertNull(event.getMetadataJson());
    }

    @Test
    void recordClientEvent_requiresStepForViewedEvent() {
        OnboardingAnalyticsEventRequestDto request = new OnboardingAnalyticsEventRequestDto();
        request.setEventType(OnboardingClientAnalyticsEventType.STEP_VIEWED);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordClientEvent("user@example.com", request)
        );
    }

    @Test
    void recordServerEvent_rejectsNonOnboardingType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordServerEvent(
                        "user@example.com",
                        ProductAnalyticsEventType.SEARCH_STARTED,
                        OnboardingStep.PROFILE
                )
        );
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setMarketRegion(MarketRegion.UK_IE);
        return user;
    }
}
