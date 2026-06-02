package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundResponseDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.service.AdminAiMealDraftService;
import com.grun.calorietracker.service.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAiMealDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminAiMealDraftService adminAiMealDraftService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void listRequests_whenAdmin_returnsReviewQueue() throws Exception {
        AdminAiRequestReviewDto item = new AdminAiRequestReviewDto();
        item.setRequestId(10L);
        item.setUserId(1L);
        item.setUserEmail("user@test.com");
        item.setStatus(AiRequestStatus.REJECTED);
        item.setQuotaConsumedAmount(2);
        item.setQuotaRefundedAmount(1);
        item.setRefundableAmount(1);

        when(adminAiMealDraftService.listRequests(eq(AiRequestStatus.REJECTED), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/admin/ai/meal-drafts")
                        .param("status", "REJECTED")
                        .param("refundableOnly", "true")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").value(10))
                .andExpect(jsonPath("$.content[0].userEmail").value("user@test.com"))
                .andExpect(jsonPath("$.content[0].refundableAmount").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void refundQuota_whenAdmin_returnsRefundResultAndAudits() throws Exception {
        AdminAiQuotaRefundRequestDto request = new AdminAiQuotaRefundRequestDto();
        request.setAmount(1);
        request.setReason("AI result was unrelated.");

        SubscriptionDto subscription = new SubscriptionDto();
        subscription.setAiUsedThisPeriod(4);
        subscription.setAiRemainingThisPeriod(11);

        AdminAiQuotaRefundResponseDto response = new AdminAiQuotaRefundResponseDto();
        response.setRequestId(10L);
        response.setUserId(1L);
        response.setStatus(AiRequestStatus.REJECTED);
        response.setQuotaConsumedAmount(1);
        response.setQuotaRefundedAmount(1);
        response.setRefundedNow(1);
        response.setQuotaRefundReason("AI result was unrelated.");
        response.setQuotaRefundedBy("admin@test.com");
        response.setQuotaRefundedAt(LocalDateTime.of(2026, 6, 2, 16, 0));
        response.setSubscription(subscription);

        when(adminAiMealDraftService.refundQuota(eq("admin@test.com"), eq(10L), any(AdminAiQuotaRefundRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/ai/meal-drafts/10/quota-refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(10))
                .andExpect(jsonPath("$.quotaRefundedAmount").value(1))
                .andExpect(jsonPath("$.subscription.aiUsedThisPeriod").value(4));

        verify(adminAuditService).record(
                eq("admin@test.com"),
                eq(AdminAuditActionType.AI_QUOTA_REFUND),
                eq(AdminAuditTargetType.AI_REQUEST),
                eq("10"),
                eq(null),
                any(AdminAiQuotaRefundResponseDto.class),
                any()
        );
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void refundQuota_whenNotAdmin_returnsForbidden() throws Exception {
        AdminAiQuotaRefundRequestDto request = new AdminAiQuotaRefundRequestDto();
        request.setAmount(1);
        request.setReason("AI result was unrelated.");

        mockMvc.perform(post("/api/v1/admin/ai/meal-drafts/10/quota-refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
