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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test void preparationAndRecoveryRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification/prepare").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"pro\",\"attemptId\":\"attempt:1234\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/subscriptions/me/verification/pending"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void preparationUsesPrincipalAndIsNeverCached() throws Exception {
        when(service.prepare("owner@example.com", "pro", "attempt:1234"))
                .thenReturn(new SubscriptionPurchaseVerificationService.Result(
                        SubscriptionPurchaseVerificationService.Status.PREPARED, new SubscriptionDto(), 0));
        mvc.perform(post("/api/v1/subscriptions/me/verification/prepare").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"pro\",\"attemptId\":\"attempt:1234\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PREPARED"));
        verify(service).prepare("owner@example.com", "pro", "attempt:1234");
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void recoveryReturnsOnlyAuthenticatedUsersAttempt() throws Exception {
        when(service.pending("owner@example.com")).thenReturn(new SubscriptionPurchaseVerificationService.PendingAttempt(
                "pro", "attempt:1234", java.time.Instant.parse("2026-10-06T10:00:00Z")));
        mvc.perform(get("/api/v1/subscriptions/me/verification/pending").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.attemptId").value("attempt:1234"));
        verify(service).pending("owner@example.com");
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void forwardsReceiptWithAuthenticatedIdentity() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"credits\",\"attemptId\":\"attempt:1234\",\"transactionId\":\"tx-123\"}"))
                .andExpect(status().isOk());
        verify(service).request("owner@example.com", "credits", "attempt:1234", "tx-123");
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void preparationRejectsInvalidAttempt() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification/prepare").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"pro\",\"attemptId\":\"x\"}"))
                .andExpect(status().isBadRequest());
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

    @Test void anonymousCancellationIsRejected() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"attemptId\":\"attempt:1234\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void cancellationUsesAuthenticatedIdentity() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"attemptId\":\"attempt:1234\",\"userId\":999}"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).cancelUnconfirmedAttempt("owner@example.com", "attempt:1234");
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void cancellationValidatesAttempt() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"attemptId\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username = "owner@example.com", roles = "USER")
    void validatesAttemptAndProduct() throws Exception {
        mvc.perform(post("/api/v1/subscriptions/me/verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"\",\"attemptId\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
