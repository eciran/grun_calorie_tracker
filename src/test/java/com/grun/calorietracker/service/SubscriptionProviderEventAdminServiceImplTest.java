package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.entity.SubscriptionProviderEventEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.service.impl.SubscriptionProviderEventAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionProviderEventAdminServiceImplTest {

    @Mock
    private SubscriptionProviderEventRepository eventRepository;

    @Mock
    private RevenueCatWebhookService revenueCatWebhookService;

    private SubscriptionProviderEventAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SubscriptionProviderEventAdminServiceImpl(eventRepository, revenueCatWebhookService);
    }

    @Test
    void getEvents_returnsPagedEvents() {
        SubscriptionProviderEventEntity event = event();
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        var result = service.getEvents(SubscriptionProviderEventStatus.FAILED, "RENEWAL", "grun_pro_monthly", 1L, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals("evt_1", result.getContent().get(0).getProviderEventId());
        assertEquals("user@example.com", result.getContent().get(0).getUserEmail());
    }

    @Test
    void getEvent_returnsRawPayloadDetail() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event()));

        var result = service.getEvent(1L);

        assertEquals("evt_1", result.getProviderEventId());
        assertEquals("{\"event\":{}}", result.getRawPayload());
    }

    @Test
    void retryEvent_delegatesToRevenueCatWebhookService() {
        when(revenueCatWebhookService.retryStoredEvent(1L))
                .thenReturn(new RevenueCatWebhookResponseDto(true, false, "evt_1", "PROCESSED", "RevenueCat event processed."));

        var result = service.retryEvent(1L);

        assertEquals("PROCESSED", result.getStatus());
        verify(revenueCatWebhookService).retryStoredEvent(1L);
    }

    @Test
    void getUserHistory_filtersByUserId() {
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event())));

        var result = service.getUserHistory(1L, 0, 25);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getUserId());
    }

    private SubscriptionProviderEventEntity event() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");

        SubscriptionProviderEventEntity event = new SubscriptionProviderEventEntity();
        event.setId(1L);
        event.setProvider(PaymentProvider.REVENUECAT);
        event.setProviderEventId("evt_1");
        event.setProviderAppUserId("user:1");
        event.setEventType("RENEWAL");
        event.setProductId("grun_pro_monthly");
        event.setEntitlementIds("pro");
        event.setTransactionId("tx_1");
        event.setOriginalTransactionId("otx_1");
        event.setUser(user);
        event.setStatus(SubscriptionProviderEventStatus.FAILED);
        event.setRawPayload("{\"event\":{}}");
        event.setProcessingError("Mapping failed");
        event.setReceivedAt(LocalDateTime.now());
        return event;
    }
}
