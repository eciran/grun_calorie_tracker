package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventDetailDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventPageDto;
import com.grun.calorietracker.enums.PaymentProvider;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.service.SubscriptionProviderEventAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSubscriptionProviderEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionProviderEventAdminService eventAdminService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getEvents_whenAdmin_returnsProviderEvents() throws Exception {
        SubscriptionProviderEventPageDto page = new SubscriptionProviderEventPageDto();
        SubscriptionProviderEventDto event = new SubscriptionProviderEventDto();
        event.setId(1L);
        event.setProvider(PaymentProvider.REVENUECAT);
        event.setProviderEventId("evt_1");
        event.setStatus(SubscriptionProviderEventStatus.FAILED);
        page.setContent(List.of(event));
        page.setTotalElements(1);

        when(eventAdminService.getEvents(any(), any(), any(), any(), eq(0), eq(25))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/subscription-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].provider").value("REVENUECAT"))
                .andExpect(jsonPath("$.content[0].providerEventId").value("evt_1"))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getEvent_whenAdmin_returnsRawPayload() throws Exception {
        SubscriptionProviderEventDetailDto detail = new SubscriptionProviderEventDetailDto();
        detail.setId(1L);
        detail.setProviderEventId("evt_1");
        detail.setRawPayload("{\"event\":{}}");

        when(eventAdminService.getEvent(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/subscription-events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerEventId").value("evt_1"))
                .andExpect(jsonPath("$.rawPayload").value("{\"event\":{}}"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void retryEvent_whenAdmin_returnsRetryResult() throws Exception {
        when(eventAdminService.retryEvent(1L))
                .thenReturn(new RevenueCatWebhookResponseDto(true, false, "evt_1", "PROCESSED", "RevenueCat event processed."));

        mockMvc.perform(post("/api/v1/admin/subscription-events/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.providerEventId").value("evt_1"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getUserHistory_whenAdmin_returnsUserEvents() throws Exception {
        SubscriptionProviderEventPageDto page = new SubscriptionProviderEventPageDto();
        SubscriptionProviderEventDto event = new SubscriptionProviderEventDto();
        event.setId(1L);
        event.setUserId(7L);
        event.setProviderEventId("evt_user");
        page.setContent(List.of(event));

        when(eventAdminService.getUserHistory(7L, 0, 25)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/subscription-events/users/7/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(7))
                .andExpect(jsonPath("$.content[0].providerEventId").value("evt_user"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getEvents_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscription-events"))
                .andExpect(status().isForbidden());
    }
}
