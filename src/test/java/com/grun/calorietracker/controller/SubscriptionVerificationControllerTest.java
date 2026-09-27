package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.service.SubscriptionPurchaseVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionVerificationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SubscriptionPurchaseVerificationService service;

    @Test void anonymousRequestIsRejected() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"pro\",\"attemptId\":\"attempt:1234\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void derivesIdentityFromPrincipalAndNeverAcceptsClientUserId() throws Exception {
        when(service.request("owner@example.com", "pro", "attempt:1234"))
                .thenReturn(new SubscriptionPurchaseVerificationService.Result(
                        SubscriptionPurchaseVerificationService.Status.PENDING, new SubscriptionDto(), 5));
        mvc.perform(post("/api/v1/subscriptions/me/verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"pro\",\"attemptId\":\"attempt:1234\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(service).request("owner@example.com", "pro", "attempt:1234");
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void validatesAttemptAndProduct() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"\",\"attemptId\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
